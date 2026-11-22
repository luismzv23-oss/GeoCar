package com.example

import com.example.data.local.PositionEntity
import com.example.data.model.TrackingPreferences
import com.example.data.model.TrackingProtocol
import com.example.data.model.TransportType
import com.example.data.network.SendResult
import com.example.data.network.SocketTelemetryClient
import com.example.data.network.protocol.Gt06Encoder
import com.example.data.network.protocol.H02Encoder
import com.example.data.network.protocol.Jt808Encoder
import com.example.data.network.protocol.ProtocolUtils
import com.example.data.network.protocol.TeltonikaEncoder
import com.example.data.network.protocol.Tk103Encoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Locale

class HardwareProtocolsTest {

  private fun samplePosition(alarm: String? = null): PositionEntity =
    PositionEntity(
      deviceId = "12345678",
      time = 1_763_740_800_000L, // 2025-11-21 16:00:00 UTC
      latitude = 37.4220,
      longitude = -122.0840,
      altitude = 25.0,
      speed = 10.0, // 36.0 km/h, 19.438 knots
      course = 180.0,
      accuracy = 5.0,
      battery = 87.0,
      charging = false,
      mock = false,
      alarm = alarm,
    )

  @Test
  fun `crc16 x25 matches official gt06 login vector and jt808 escaping roundtrips`() {
    val vector = byteArrayOf(0x05, 0x01, 0x00, 0x01)
    assertEquals(0xD9DC, ProtocolUtils.crc16X25(vector))

    val raw = byteArrayOf(0x02, 0x7E, 0x05, 0x7D, 0x7E, 0x01)
    val escaped = ProtocolUtils.jt808Escape(raw)
    assertArrayEquals(raw, ProtocolUtils.jt808Unescape(escaped))
  }

  @Test
  fun `gt06 encodes login location and sos alarm frames with valid crc`() {
    val login = Gt06Encoder.buildLoginPacket("12345678", 1)
    assertEquals(0x78, login[0].toInt() and 0xFF)
    assertEquals(0x78, login[1].toInt() and 0xFF)
    assertEquals(Gt06Encoder.PROTO_LOGIN, login[3].toInt() and 0xFF)
    assertEquals(0x0D, login[login.size - 2].toInt() and 0xFF)
    assertEquals(0x0A, login[login.size - 1].toInt() and 0xFF)

    val gps = Gt06Encoder.buildPositionPacket(samplePosition(), 2)
    assertEquals(Gt06Encoder.PROTO_GPS, gps[3].toInt() and 0xFF)
    val crcStart = 2
    val crcLen = gps.size - 6
    val expectedCrc = ProtocolUtils.crc16X25(gps, crcStart, crcLen)
    val actualCrc =
      ((gps[gps.size - 4].toInt() and 0xFF) shl 8) or (gps[gps.size - 3].toInt() and 0xFF)
    assertEquals(expectedCrc, actualCrc)

    val sos = Gt06Encoder.buildPositionPacket(samplePosition(alarm = "sos"), 3)
    assertEquals(Gt06Encoder.PROTO_ALARM, sos[3].toInt() and 0xFF)
  }

  @Test
  fun `h02 and tk103 format coordinates speed and sos flags regardless of locale`() {
    val prev = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("es-ES"))
      val normalH02 = H02Encoder.buildMessage(samplePosition())
      assertTrue(normalH02.startsWith("*HQ,0012345678,V1,"))
      assertTrue(normalH02.contains(",A,3725.3200,N,12205.0400,W,019.44,180,"))
      assertTrue(normalH02.endsWith(",FFFFFFFF#"))

      val sosH02 = H02Encoder.buildMessage(samplePosition(alarm = "sos"))
      assertTrue(sosH02.endsWith(",FFFFFFFD#"))

      val normalTk = Tk103Encoder.buildMessage(samplePosition())
      assertTrue(normalTk.startsWith("(000012345678BR00"))
      assertTrue(normalTk.contains("3725.3200N12205.0400W036.0"))
      assertTrue(normalTk.endsWith(")"))

      val sosTk = Tk103Encoder.buildMessage(samplePosition(alarm = "sos"))
      assertTrue(sosTk.startsWith("(000012345678BO012"))
    } finally {
      Locale.setDefault(prev)
    }
  }

  @Test
  fun `teltonika codec 8 encodes imei handshake and valid crc16 ibm packet`() {
    val handshake = TeltonikaEncoder.buildImeiHandshake("12345678")
    assertEquals(17, handshake.size)
    assertEquals(0x00, handshake[0].toInt() and 0xFF)
    assertEquals(0x0F, handshake[1].toInt() and 0xFF)
    assertEquals("000000012345678", String(handshake.copyOfRange(2, 17), Charsets.US_ASCII))

    val packet = TeltonikaEncoder.buildCodec8Packet(samplePosition(alarm = "sos"))
    // Preamble 00000000
    assertEquals(0, packet[0].toInt() + packet[1].toInt() + packet[2].toInt() + packet[3].toInt())
    val dataLen =
      ((packet[4].toInt() and 0xFF) shl 24) or
        ((packet[5].toInt() and 0xFF) shl 16) or
        ((packet[6].toInt() and 0xFF) shl 8) or
        (packet[7].toInt() and 0xFF)
    assertEquals(packet.size - 12, dataLen)
    assertEquals(0x08, packet[8].toInt() and 0xFF) // Codec 8
    val computedCrc = ProtocolUtils.crc16Ibm(packet, 8, dataLen)
    val packetCrc =
      ((packet[packet.size - 2].toInt() and 0xFF) shl 8) or (packet[packet.size - 1].toInt() and 0xFF)
    assertEquals(computedCrc, packetCrc)
  }

  @Test
  fun `jt808 encodes registration location with sos bit and parses server response`() {
    val reg = Jt808Encoder.buildRegistrationPacket("12345678", 1)
    assertEquals(0x7E, reg.first().toInt() and 0xFF)
    assertEquals(0x7E, reg.last().toInt() and 0xFF)

    val loc = Jt808Encoder.buildLocationPacket(samplePosition(alarm = "sos"), 2)
    val unescaped = ProtocolUtils.jt808Unescape(loc.copyOfRange(1, loc.size - 1))
    val msgId = ((unescaped[0].toInt() and 0xFF) shl 8) or (unescaped[1].toInt() and 0xFF)
    assertEquals(Jt808Encoder.MSG_LOCATION_REPORT, msgId)
    // Alarm DWORD starts at index 12; SOS bit 0 should be 1
    assertEquals(1, unescaped[15].toInt() and 0x01)

    val mockRegAck =
      Jt808Encoder.buildFrame(
        msgId = Jt808Encoder.MSG_REGISTER_RESPONSE,
        deviceId = "12345678",
        serial = 1,
        body = byteArrayOf(0x00, 0x01, 0x00) + "TOKEN123".toByteArray(Charsets.US_ASCII),
      )
    val parsed = Jt808Encoder.parseServerFrame(mockRegAck)
    assertNotNull(parsed)
    assertEquals(Jt808Encoder.MSG_REGISTER_RESPONSE, parsed!!.messageId)
    assertEquals(0, parsed.result)
    assertEquals("TOKEN123", parsed.authCode)
  }

  @Test
  fun `socket telemetry client completes gt06 teltonika jt808 tcp handshakes and udp sends`() =
    runTest {
      // 1. GT06 TCP handshake + GPS packet
      ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
        val serverJob =
          async(Dispatchers.IO) {
            server.accept().use { client ->
              val input = client.getInputStream()
              val loginBuf = ByteArray(18)
              var n = 0
              while (n < 18) {
                val r = input.read(loginBuf, n, 18 - n)
                if (r < 0) break
                n += r
              }
              assertEquals(Gt06Encoder.PROTO_LOGIN, loginBuf[3].toInt() and 0xFF)
              // Reply with verified login ACK
              client.getOutputStream().write(
                byteArrayOf(0x78, 0x78, 0x05, 0x01, 0x00, 0x01, 0xD9.toByte(), 0xDC.toByte(), 0x0D, 0x0A)
              )
              client.getOutputStream().flush()
              val gpsHdr = ByteArray(4)
              input.read(gpsHdr)
              gpsHdr[3].toInt() and 0xFF
            }
          }

        val telemetryClient = SocketTelemetryClient()
        val prefs =
          TrackingPreferences(
            deviceId = "12345678",
            serverUrl = "127.0.0.1:${server.localPort}",
            protocol = TrackingProtocol.GT06,
            transport = TransportType.TCP,
          )
        val result = telemetryClient.sendPosition(prefs, samplePosition())
        telemetryClient.closeActiveSession()
        assertTrue(result is SendResult.Success)
        assertEquals(Gt06Encoder.PROTO_GPS, serverJob.await())
      }

      // 2. Teltonika TCP IMEI handshake + Codec 8 ACK
      ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
        val serverJob =
          async(Dispatchers.IO) {
            server.accept().use { client ->
              val input = client.getInputStream()
              val imei = ByteArray(17)
              var n = 0
              while (n < 17) n += input.read(imei, n, 17 - n)
              assertEquals(0x0F, imei[1].toInt() and 0xFF)
              client.getOutputStream().write(byteArrayOf(0x01))
              client.getOutputStream().flush()

              val preambleAndLen = ByteArray(8)
              var m = 0
              while (m < 8) m += input.read(preambleAndLen, m, 8 - m)
              val len =
                ((preambleAndLen[4].toInt() and 0xFF) shl 24) or
                  ((preambleAndLen[5].toInt() and 0xFF) shl 16) or
                  ((preambleAndLen[6].toInt() and 0xFF) shl 8) or
                  (preambleAndLen[7].toInt() and 0xFF)
              val rest = ByteArray(len + 4)
              var k = 0
              while (k < rest.size) k += input.read(rest, k, rest.size - k)
              client.getOutputStream().write(byteArrayOf(0x00, 0x00, 0x00, 0x01))
              client.getOutputStream().flush()
              rest[0].toInt() and 0xFF
            }
          }

        val telemetryClient = SocketTelemetryClient()
        val prefs =
          TrackingPreferences(
            deviceId = "12345678",
            serverUrl = "127.0.0.1:${server.localPort}",
            protocol = TrackingProtocol.TELTONIKA,
            transport = TransportType.TCP,
          )
        val result = telemetryClient.sendPosition(prefs, samplePosition())
        telemetryClient.closeActiveSession()
        assertTrue(result is SendResult.Success)
        assertEquals(0x08, serverJob.await())
      }

      // 3. JT808 TCP register -> auth -> location report
      ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
        val serverJob =
          async(Dispatchers.IO) {
            server.accept().use { client ->
              val input = client.getInputStream()
              val out = client.getOutputStream()

              fun readFrame(): ByteArray {
                val buf = ByteArrayOutputStream()
                var b = input.read()
                while (b != -1 && b != 0x7E) b = input.read()
                buf.write(0x7E)
                while (true) {
                  val c = input.read()
                  if (c == -1) break
                  buf.write(c)
                  if (c == 0x7E) break
                }
                return buf.toByteArray()
              }

              readFrame() // 0x0100 register
              out.write(
                Jt808Encoder.buildFrame(
                  msgId = Jt808Encoder.MSG_REGISTER_RESPONSE,
                  deviceId = "12345678",
                  serial = 1,
                  body = byteArrayOf(0x00, 0x01, 0x00) + "AUTH9".toByteArray(),
                )
              )
              out.flush()

              readFrame() // 0x0102 auth
              out.write(
                Jt808Encoder.buildFrame(
                  msgId = Jt808Encoder.MSG_GENERAL_RESPONSE,
                  deviceId = "12345678",
                  serial = 2,
                  body = byteArrayOf(0x00, 0x02, 0x01, 0x02, 0x00),
                )
              )
              out.flush()

              val locFrame = readFrame() // 0x0200 location
              out.write(
                Jt808Encoder.buildFrame(
                  msgId = Jt808Encoder.MSG_GENERAL_RESPONSE,
                  deviceId = "12345678",
                  serial = 3,
                  body = byteArrayOf(0x00, 0x03, 0x02, 0x00, 0x00),
                )
              )
              out.flush()
              locFrame.size
            }
          }

        val telemetryClient = SocketTelemetryClient()
        val prefs =
          TrackingPreferences(
            deviceId = "12345678",
            serverUrl = "127.0.0.1:${server.localPort}",
            protocol = TrackingProtocol.JT808,
            transport = TransportType.TCP,
          )
        val result = telemetryClient.sendPosition(prefs, samplePosition())
        telemetryClient.closeActiveSession()
        assertTrue(result is SendResult.Success)
        assertTrue(serverJob.await() > 15)
      }

      // 4. H02 UDP datagram send
      DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { udpServer ->
        val receiveJob =
          async(Dispatchers.IO) {
            val pkt = DatagramPacket(ByteArray(256), 256)
            udpServer.receive(pkt)
            String(pkt.data, 0, pkt.length, Charsets.US_ASCII)
          }
        val telemetryClient = SocketTelemetryClient()
        val prefs =
          TrackingPreferences(
            deviceId = "12345678",
            serverUrl = "127.0.0.1:${udpServer.localPort}",
            protocol = TrackingProtocol.H02,
            transport = TransportType.UDP,
          )
        val result = telemetryClient.sendPosition(prefs, samplePosition())
        assertTrue(result is SendResult.Success)
        val payload = receiveJob.await()
        assertTrue(payload.startsWith("*HQ,0012345678,V1,"))
      }
    }

  @Test
  fun `preferences validation and endpoint rewriting support multi-protocol endpoints`() {
    assertTrue(TrackingPreferences.isValidServerEndpoint("demo.traccar.org:5023", TrackingProtocol.GT06))
    assertTrue(TrackingPreferences.isValidServerEndpoint("demo.traccar.org", TrackingProtocol.TELTONIKA))
    assertFalse(TrackingPreferences.isValidServerEndpoint("demo.traccar.org:99999", TrackingProtocol.GT06))
    assertFalse(TrackingPreferences.isValidServerEndpoint("demo.traccar.org:5055", TrackingProtocol.OSMAND))

    assertEquals(
      "demo.traccar.org:5027",
      TrackingPreferences.rewriteEndpointForProtocol("http://demo.traccar.org:5055", TrackingProtocol.TELTONIKA),
    )
    assertEquals(
      "http://demo.traccar.org:5055",
      TrackingPreferences.rewriteEndpointForProtocol("demo.traccar.org:5027", TrackingProtocol.OSMAND),
    )
  }
}
