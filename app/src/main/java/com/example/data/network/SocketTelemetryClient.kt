package com.example.data.network

import com.example.data.local.PositionEntity
import com.example.data.model.TrackingPreferences
import com.example.data.model.TrackingProtocol
import com.example.data.model.TransportType
import com.example.data.network.protocol.Gt06Encoder
import com.example.data.network.protocol.H02Encoder
import com.example.data.network.protocol.Jt808Encoder
import com.example.data.network.protocol.TeltonikaEncoder
import com.example.data.network.protocol.Tk103Encoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

class SocketTelemetryClient(
  private val connectTimeoutMs: Int = 10_000,
  private val readTimeoutMs: Int = 10_000,
) {

  private data class SessionKey(
    val protocol: TrackingProtocol,
    val host: String,
    val port: Int,
    val deviceId: String,
  )

  private val tcpMutex = Mutex()
  private val serialCounter = AtomicInteger(1)
  private var activeSocket: Socket? = null
  private var activeKey: SessionKey? = null
  private var sessionAuthenticated: Boolean = false

  suspend fun sendPosition(
    prefs: TrackingPreferences,
    position: PositionEntity,
  ): SendResult =
    withContext(Dispatchers.IO) {
      val protocol = prefs.protocol
      val transport = protocol.normalizeTransport(prefs.transport)
      val hostPort =
        TrackingPreferences.parseHostAndPort(prefs.serverUrl, protocol.defaultPort)
          ?: return@withContext SendResult.InvalidUrl

      val (host, port) = hostPort
      when (transport) {
        TransportType.UDP -> sendUdp(protocol, host, port, position)
        TransportType.TCP -> sendTcpWithReconnect(protocol, host, port, position)
        TransportType.HTTP -> SendResult.InvalidUrl
      }
    }

  private fun sendUdp(
    protocol: TrackingProtocol,
    host: String,
    port: Int,
    position: PositionEntity,
  ): SendResult {
    val payloadStr =
      when (protocol) {
        TrackingProtocol.H02 -> H02Encoder.buildMessage(position)
        TrackingProtocol.TK103 -> Tk103Encoder.buildMessage(position)
        else -> return SendResult.NetworkFailure("${protocol.displayName} requires TCP")
      }
    val bytes = payloadStr.toByteArray(Charsets.US_ASCII)
    return try {
      DatagramSocket().use { socket ->
        socket.soTimeout = readTimeoutMs
        val address = InetAddress.getByName(host)
        val packet = DatagramPacket(bytes, bytes.size, address, port)
        socket.send(packet)
      }
      SendResult.Success(200, "${protocol.displayName} UDP -> $host:$port")
    } catch (e: Exception) {
      SendResult.NetworkFailure(e.message ?: "UDP send error")
    }
  }

  private suspend fun sendTcpWithReconnect(
    protocol: TrackingProtocol,
    host: String,
    port: Int,
    position: PositionEntity,
  ): SendResult =
    tcpMutex.withLock {
      val targetKey = SessionKey(protocol, host, port, position.deviceId)
      val hadExistingSession = activeSocket != null && activeKey == targetKey && sessionAuthenticated

      val firstAttempt = trySendTcpOnce(targetKey, position)
      if (firstAttempt is SendResult.NetworkFailure && hadExistingSession) {
        // Stale kept-alive socket was likely closed by peer; reset and reconnect cleanly once
        closeActiveSessionLocked()
        return@withLock trySendTcpOnce(targetKey, position)
      }
      firstAttempt
    }

  private fun trySendTcpOnce(
    key: SessionKey,
    position: PositionEntity,
  ): SendResult {
    return try {
      val socket = ensureSessionLocked(key)
      when (key.protocol) {
        TrackingProtocol.GT06 -> sendGt06Record(socket, key, position)
        TrackingProtocol.H02 -> sendAsciiTcpRecord(socket, key, H02Encoder.buildMessage(position))
        TrackingProtocol.TK103 -> sendAsciiTcpRecord(socket, key, Tk103Encoder.buildMessage(position))
        TrackingProtocol.TELTONIKA -> sendTeltonikaRecord(socket, key, position)
        TrackingProtocol.JT808 -> sendJt808Record(socket, key, position)
        TrackingProtocol.OSMAND -> SendResult.InvalidUrl
      }
    } catch (e: Exception) {
      closeActiveSessionLocked()
      SendResult.NetworkFailure(e.message ?: "TCP error")
    }
  }

  private fun ensureSessionLocked(key: SessionKey): Socket {
    val existing = activeSocket
    if (
      existing != null &&
      !existing.isClosed &&
      existing.isConnected &&
      activeKey == key &&
      sessionAuthenticated
    ) {
      return existing
    }

    closeActiveSessionLocked()

    val socket = Socket()
    socket.soTimeout = readTimeoutMs
    socket.connect(InetSocketAddress(key.host, key.port), connectTimeoutMs)
    activeSocket = socket
    activeKey = key

    when (key.protocol) {
      TrackingProtocol.GT06 -> {
        val login = Gt06Encoder.buildLoginPacket(key.deviceId, nextSerial())
        socket.getOutputStream().apply {
          write(login)
          flush()
        }
        val ack = readGt06Frame(socket.getInputStream())
        if (!Gt06Encoder.isValidAck(ack, Gt06Encoder.PROTO_LOGIN)) {
          throw IOException("GT06 login ACK invalid or missing")
        }
      }
      TrackingProtocol.TELTONIKA -> {
        val handshake = TeltonikaEncoder.buildImeiHandshake(key.deviceId)
        socket.getOutputStream().apply {
          write(handshake)
          flush()
        }
        val accepted = socket.getInputStream().read()
        if (accepted != 0x01) {
          throw IOException("Teltonika IMEI handshake rejected (code=$accepted)")
        }
      }
      TrackingProtocol.JT808 -> {
        val regFrame = Jt808Encoder.buildRegistrationPacket(key.deviceId, nextSerial())
        socket.getOutputStream().apply {
          write(regFrame)
          flush()
        }
        val regAckRaw = readJt808Frame(socket.getInputStream())
        val regAck =
          Jt808Encoder.parseServerFrame(regAckRaw)
            ?: throw IOException("JT808 invalid registration response")
        if (regAck.result != 0) {
          throw IOException("JT808 registration rejected (code=${regAck.result})")
        }
        if (regAck.messageId == Jt808Encoder.MSG_REGISTER_RESPONSE && regAck.authCode.isNotEmpty()) {
          val authFrame = Jt808Encoder.buildAuthPacket(key.deviceId, nextSerial(), regAck.authCode)
          socket.getOutputStream().apply {
            write(authFrame)
            flush()
          }
          val authAckRaw = readJt808Frame(socket.getInputStream())
          val authAck =
            Jt808Encoder.parseServerFrame(authAckRaw)
              ?: throw IOException("JT808 invalid auth response")
          if (authAck.result != 0) {
            throw IOException("JT808 auth rejected (code=${authAck.result})")
          }
        }
      }
      TrackingProtocol.H02,
      TrackingProtocol.TK103,
      TrackingProtocol.OSMAND -> Unit
    }

    sessionAuthenticated = true
    return socket
  }

  private fun sendGt06Record(
    socket: Socket,
    key: SessionKey,
    position: PositionEntity,
  ): SendResult {
    val frame = Gt06Encoder.buildPositionPacket(position, nextSerial())
    socket.getOutputStream().apply {
      write(frame)
      flush()
    }
    if (position.alarm.equals("sos", ignoreCase = true)) {
      // GT06 alarm packets (0x16) receive an ACK from Traccar; tolerate timeout silently if custom server skips it
      try {
        val prevTimeout = socket.soTimeout
        socket.soTimeout = 2_000
        try {
          readGt06Frame(socket.getInputStream())
        } finally {
          socket.soTimeout = prevTimeout
        }
      } catch (_: SocketTimeoutException) {
        // Acceptable for custom/proxy GT06 endpoints
      }
    }
    return SendResult.Success(200, "GT06 TCP -> ${key.host}:${key.port}")
  }

  private fun sendAsciiTcpRecord(
    socket: Socket,
    key: SessionKey,
    message: String,
  ): SendResult {
    socket.getOutputStream().apply {
      write(message.toByteArray(Charsets.US_ASCII))
      flush()
    }
    return SendResult.Success(200, "${key.protocol.displayName} TCP -> ${key.host}:${key.port}")
  }

  private fun sendTeltonikaRecord(
    socket: Socket,
    key: SessionKey,
    position: PositionEntity,
  ): SendResult {
    val packet = TeltonikaEncoder.buildCodec8Packet(position)
    socket.getOutputStream().apply {
      write(packet)
      flush()
    }
    val ack = readExactBytes(socket.getInputStream(), 4)
    val acceptedCount = TeltonikaEncoder.parseAcceptedCountAck(ack) ?: 0
    if (acceptedCount < 1) {
      throw IOException("Teltonika server rejected record (ack=$acceptedCount)")
    }
    return SendResult.Success(200, "Teltonika TCP ack=$acceptedCount -> ${key.host}:${key.port}")
  }

  private fun sendJt808Record(
    socket: Socket,
    key: SessionKey,
    position: PositionEntity,
  ): SendResult {
    val frame = Jt808Encoder.buildLocationPacket(position, nextSerial())
    socket.getOutputStream().apply {
      write(frame)
      flush()
    }
    val ackRaw = readJt808Frame(socket.getInputStream())
    val ack =
      Jt808Encoder.parseServerFrame(ackRaw)
        ?: throw IOException("JT808 invalid location ACK")
    if (ack.result != 0) {
      throw IOException("JT808 location rejected (code=${ack.result})")
    }
    return SendResult.Success(200, "JT808 TCP -> ${key.host}:${key.port}")
  }

  private fun nextSerial(): Int = (serialCounter.getAndIncrement() and 0xFFFF).coerceAtLeast(1)

  private fun readExactBytes(input: InputStream, count: Int): ByteArray {
    val buf = ByteArray(count)
    var readTotal = 0
    while (readTotal < count) {
      val n = input.read(buf, readTotal, count - readTotal)
      if (n < 0) throw EOFException("Unexpected end of stream")
      readTotal += n
    }
    return buf
  }

  private fun readGt06Frame(input: InputStream): ByteArray {
    val header = readExactBytes(input, 3)
    val b0 = header[0].toInt() and 0xFF
    val b1 = header[1].toInt() and 0xFF
    if (b0 == 0x78 && b1 == 0x78) {
      val len = header[2].toInt() and 0xFF
      val rest = readExactBytes(input, len + 2)
      return header + rest
    }
    if (b0 == 0x79 && b1 == 0x79) {
      val lenLow = input.read()
      if (lenLow < 0) throw EOFException("Unexpected end of stream")
      val len = ((header[2].toInt() and 0xFF) shl 8) or (lenLow and 0xFF)
      val rest = readExactBytes(input, len + 2)
      return header + byteArrayOf(lenLow.toByte()) + rest
    }
    throw IOException("Unexpected GT06 header: %02X %02X".format(b0, b1))
  }

  private fun readJt808Frame(input: InputStream): ByteArray {
    val out = ByteArrayOutputStream(64)
    var b = input.read()
    while (b != -1 && b != 0x7E) {
      b = input.read()
    }
    if (b == -1) throw EOFException("Unexpected EOF before JT808 start delimiter")
    out.write(0x7E)

    while (true) {
      val next = input.read()
      if (next == -1) throw EOFException("Unexpected EOF inside JT808 frame")
      if (next == 0x7E) {
        if (out.size() == 1) {
          // Consecutive 0x7E delimiter; treat as start of actual frame
          continue
        }
        out.write(0x7E)
        break
      }
      out.write(next)
    }
    return out.toByteArray()
  }

  fun closeActiveSession() {
    runCatching {
      activeSocket?.close()
    }
    activeSocket = null
    activeKey = null
    sessionAuthenticated = false
  }

  private fun closeActiveSessionLocked() {
    closeActiveSession()
  }
}
