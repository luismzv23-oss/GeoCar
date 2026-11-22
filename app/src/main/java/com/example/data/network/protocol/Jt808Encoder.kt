package com.example.data.network.protocol

import com.example.data.local.PositionEntity
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Jt808Encoder {

  const val MSG_GENERAL_RESPONSE = 0x8001
  const val MSG_REGISTER = 0x0100
  const val MSG_REGISTER_RESPONSE = 0x8100
  const val MSG_AUTH = 0x0102
  const val MSG_LOCATION_REPORT = 0x0200

  data class ServerResponse(
    val messageId: Int,
    val result: Int,
    val authCode: String = "",
  )

  fun buildRegistrationPacket(deviceId: String, serial: Int): ByteArray {
    val body = ByteArrayOutputStream(38)
    // Province ID (2B) + City ID (2B)
    writeUInt16BE(body, 0)
    writeUInt16BE(body, 0)
    // Manufacturer ID (5B)
    body.write("WAYPL".toByteArray(Charsets.US_ASCII))
    // Terminal Model (20B ASCII padded with 0)
    val model = "WAYPULSE-ANDROID".toByteArray(Charsets.US_ASCII)
    body.write(model)
    repeat(20 - model.size) { body.write(0) }
    // Terminal ID (7B)
    val termId = ProtocolUtils.normalizeNumericId(deviceId, 7).toByteArray(Charsets.US_ASCII)
    body.write(termId)
    // License plate color (1B: 0 = no plate)
    body.write(0x00)
    return buildFrame(MSG_REGISTER, deviceId, serial, body.toByteArray())
  }

  fun buildAuthPacket(deviceId: String, serial: Int, authCode: String): ByteArray {
    val body = authCode.toByteArray(Charsets.UTF_8)
    return buildFrame(MSG_AUTH, deviceId, serial, body)
  }

  fun buildLocationPacket(position: PositionEntity, serial: Int): ByteArray {
    val body = ByteArrayOutputStream(28)

    // Alarm DWORD: bit 0 = SOS/emergency
    val alarmFlags = if (position.alarm.equals("sos", ignoreCase = true)) 0x00000001 else 0x00000000
    writeUInt32BE(body, alarmFlags.toLong())

    // Status DWORD: bit 0 = ACC ON, bit 1 = GPS fixed, bit 2 = South lat, bit 3 = West lon
    var statusFlags = 0x00000003
    if (position.latitude < 0) statusFlags = statusFlags or 0x00000004
    if (position.longitude < 0) statusFlags = statusFlags or 0x00000008
    writeUInt32BE(body, statusFlags.toLong())

    val latMicroDeg = (abs(position.latitude) * 1_000_000.0).roundToLong()
    val lonMicroDeg = (abs(position.longitude) * 1_000_000.0).roundToLong()
    writeUInt32BE(body, latMicroDeg)
    writeUInt32BE(body, lonMicroDeg)

    val alt = position.altitude.roundToInt().coerceIn(0, 65535)
    writeUInt16BE(body, alt)

    // Speed in 1/10 km/h
    val speedTenthKmh = (position.speed * 3.6 * 10.0).roundToInt().coerceIn(0, 65535)
    writeUInt16BE(body, speedTenthKmh)

    val course = position.course.roundToInt().coerceIn(0, 359)
    writeUInt16BE(body, course)

    // BCD[6] YYMMDDHHMMSS in GMT+8 per JT/T 808 spec
    val cal = ProtocolUtils.gmtPlus8Calendar(position.time)
    val timeDigits =
      String.format(
        Locale.US,
        "%02d%02d%02d%02d%02d%02d",
        cal.get(Calendar.YEAR) % 100,
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
      )
    body.write(ProtocolUtils.encodeBcd(timeDigits))

    return buildFrame(MSG_LOCATION_REPORT, position.deviceId, serial, body.toByteArray())
  }

  fun parseServerFrame(rawFrame: ByteArray): ServerResponse? {
    if (rawFrame.size < 15) return null
    val startIdx = if ((rawFrame[0].toInt() and 0xFF) == 0x7E) 1 else 0
    val endIdx =
      if ((rawFrame.last().toInt() and 0xFF) == 0x7E) rawFrame.size - 1 else rawFrame.size
    if (endIdx <= startIdx) return null

    val unescaped = ProtocolUtils.jt808Unescape(rawFrame.copyOfRange(startIdx, endIdx))
    if (unescaped.size < 13) return null

    val expectedChecksum = unescaped.last()
    val computedChecksum = ProtocolUtils.xorChecksum(unescaped, 0, unescaped.size - 1)
    if (expectedChecksum != computedChecksum) return null

    val messageId =
      ((unescaped[0].toInt() and 0xFF) shl 8) or (unescaped[1].toInt() and 0xFF)
    val bodyProps =
      ((unescaped[2].toInt() and 0xFF) shl 8) or (unescaped[3].toInt() and 0xFF)
    val bodyLen = bodyProps and 0x03FF
    val headerLen = 12
    if (headerLen + bodyLen + 1 > unescaped.size) return null

    return when (messageId) {
      MSG_REGISTER_RESPONSE -> {
        if (bodyLen < 3) return null
        val result = unescaped[headerLen + 2].toInt() and 0xFF
        val authBytes =
          if (bodyLen > 3) {
            unescaped.copyOfRange(headerLen + 3, headerLen + bodyLen)
          } else {
            ByteArray(0)
          }
        ServerResponse(
          messageId = messageId,
          result = result,
          authCode = authBytes.toString(Charsets.UTF_8),
        )
      }
      MSG_GENERAL_RESPONSE -> {
        if (bodyLen < 5) return null
        val result = unescaped[headerLen + 4].toInt() and 0xFF
        ServerResponse(messageId = messageId, result = result)
      }
      else -> ServerResponse(messageId = messageId, result = 0)
    }
  }

  fun buildFrame(msgId: Int, deviceId: String, serial: Int, body: ByteArray): ByteArray {
    val unescaped = ByteArrayOutputStream(12 + body.size + 1)
    writeUInt16BE(unescaped, msgId)
    writeUInt16BE(unescaped, body.size and 0x03FF)
    unescaped.write(ProtocolUtils.encodeBcd(ProtocolUtils.normalizeNumericId(deviceId, 12)))
    writeUInt16BE(unescaped, serial)
    unescaped.write(body)

    val headerAndBody = unescaped.toByteArray()
    val checksum = ProtocolUtils.xorChecksum(headerAndBody)

    val withChecksum = ByteArray(headerAndBody.size + 1)
    headerAndBody.copyInto(withChecksum)
    withChecksum[withChecksum.lastIndex] = checksum

    val escaped = ProtocolUtils.jt808Escape(withChecksum)
    val framed = ByteArray(escaped.size + 2)
    framed[0] = 0x7E
    escaped.copyInto(framed, destinationOffset = 1)
    framed[framed.lastIndex] = 0x7E
    return framed
  }

  private fun writeUInt16BE(out: ByteArrayOutputStream, value: Int) {
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
  }

  private fun writeUInt32BE(out: ByteArrayOutputStream, value: Long) {
    out.write(((value ushr 24) and 0xFF).toInt())
    out.write(((value ushr 16) and 0xFF).toInt())
    out.write(((value ushr 8) and 0xFF).toInt())
    out.write((value and 0xFF).toInt())
  }
}
