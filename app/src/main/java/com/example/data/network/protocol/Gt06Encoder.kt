package com.example.data.network.protocol

import com.example.data.local.PositionEntity
import java.io.ByteArrayOutputStream
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Gt06Encoder {

  const val PROTO_LOGIN = 0x01
  const val PROTO_GPS = 0x12
  const val PROTO_ALARM = 0x16

  fun buildLoginPacket(deviceId: String, serial: Int): ByteArray {
    // 15-digit IMEI encoded into 8 bytes BCD with leading 0
    val imei15 = ProtocolUtils.normalizeNumericId(deviceId, 15)
    val content = ProtocolUtils.encodeBcd("0$imei15")
    return wrapFrame(PROTO_LOGIN, content, serial)
  }

  fun buildPositionPacket(position: PositionEntity, serial: Int): ByteArray =
    if (position.alarm.equals("sos", ignoreCase = true)) {
      buildAlarmPacket(position, serial)
    } else {
      buildGpsPacket(position, serial)
    }

  fun buildGpsPacket(position: PositionEntity, serial: Int): ByteArray {
    val body = ByteArrayOutputStream(32)
    writeGpsBlock(body, position)
    // LBS placeholder (MCC 2B, MNC 1B, LAC 2B, CellID 3B) = 8 zero bytes
    repeat(8) { body.write(0x00) }
    return wrapFrame(PROTO_GPS, body.toByteArray(), serial)
  }

  fun buildAlarmPacket(position: PositionEntity, serial: Int): ByteArray {
    val body = ByteArrayOutputStream(40)
    writeGpsBlock(body, position)
    // LBS length (9 bytes: 1 length + 2 MCC + 1 MNC + 2 LAC + 3 CellID)
    body.write(0x09)
    repeat(8) { body.write(0x00) }

    // Terminal info byte:
    // Bit 6 (0x40): GPS tracking on
    // Bits 5..3 = 100 (0x20): SOS alarm
    // Bit 2 (0x04): Power charging
    val terminalInfo =
      0x40 or 0x20 or (if (position.charging) 0x04 else 0x00)
    body.write(terminalInfo)

    // Voltage level 0..6
    val voltageLevel = ((position.battery / 100.0) * 6.0).roundToInt().coerceIn(0, 6)
    body.write(voltageLevel)

    // GSM signal strength 0..4
    body.write(0x04)

    // Alarm/Language: 0x01 = SOS alarm, 0x02 = English
    body.write(0x01)
    body.write(0x02)

    return wrapFrame(PROTO_ALARM, body.toByteArray(), serial)
  }

  fun isValidAck(frame: ByteArray, expectedProtocol: Int): Boolean {
    if (frame.size < 10) return false
    if ((frame[0].toInt() and 0xFF) != 0x78 || (frame[1].toInt() and 0xFF) != 0x78) return false
    val proto = frame[3].toInt() and 0xFF
    return proto == expectedProtocol
  }

  private fun writeGpsBlock(out: ByteArrayOutputStream, position: PositionEntity) {
    val cal = ProtocolUtils.utcCalendar(position.time)
    out.write((cal.get(Calendar.YEAR) % 100) and 0xFF)
    out.write((cal.get(Calendar.MONTH) + 1) and 0xFF)
    out.write(cal.get(Calendar.DAY_OF_MONTH) and 0xFF)
    out.write(cal.get(Calendar.HOUR_OF_DAY) and 0xFF)
    out.write(cal.get(Calendar.MINUTE) and 0xFF)
    out.write(cal.get(Calendar.SECOND) and 0xFF)

    // GPS info length (0xC = 12 bytes) + 10 satellites
    out.write(0xCA)

    val latRaw = (abs(position.latitude) * 60.0 * 30000.0).roundToLong()
    val lonRaw = (abs(position.longitude) * 60.0 * 30000.0).roundToLong()
    writeUInt32BE(out, latRaw)
    writeUInt32BE(out, lonRaw)

    // Speed in km/h (0..255)
    val speedKmh = (position.speed * 3.6).roundToInt().coerceIn(0, 255)
    out.write(speedKmh)

    // Course + status bits:
    // Bit 12 (0x1000): GPS fixed
    // Bit 11 (0x0800): West longitude (lon < 0)
    // Bit 10 (0x0400): North latitude (lat >= 0)
    // Bits 9..0: course degrees (0..359)
    val courseDeg = position.course.roundToInt().coerceIn(0, 359)
    var courseStatus = 0x1000 or (courseDeg and 0x03FF)
    if (position.longitude < 0) courseStatus = courseStatus or 0x0800
    if (position.latitude >= 0) courseStatus = courseStatus or 0x0400
    out.write((courseStatus ushr 8) and 0xFF)
    out.write(courseStatus and 0xFF)
  }

  private fun wrapFrame(protocol: Int, content: ByteArray, serial: Int): ByteArray {
    // Length = 1 (protocol) + content.size + 2 (serial) + 2 (crc)
    val packetLength = 1 + content.size + 2 + 2
    val crcPayload = ByteArray(1 + 1 + content.size + 2)
    crcPayload[0] = (packetLength and 0xFF).toByte()
    crcPayload[1] = (protocol and 0xFF).toByte()
    content.copyInto(crcPayload, destinationOffset = 2)
    val serialIdx = 2 + content.size
    crcPayload[serialIdx] = ((serial ushr 8) and 0xFF).toByte()
    crcPayload[serialIdx + 1] = (serial and 0xFF).toByte()

    val crc = ProtocolUtils.crc16X25(crcPayload)

    val frame = ByteArray(2 + crcPayload.size + 2 + 2)
    frame[0] = 0x78
    frame[1] = 0x78
    crcPayload.copyInto(frame, destinationOffset = 2)
    val crcIdx = 2 + crcPayload.size
    frame[crcIdx] = ((crc ushr 8) and 0xFF).toByte()
    frame[crcIdx + 1] = (crc and 0xFF).toByte()
    frame[crcIdx + 2] = 0x0D
    frame[crcIdx + 3] = 0x0A
    return frame
  }

  private fun writeUInt32BE(out: ByteArrayOutputStream, value: Long) {
    out.write(((value ushr 24) and 0xFF).toInt())
    out.write(((value ushr 16) and 0xFF).toInt())
    out.write(((value ushr 8) and 0xFF).toInt())
    out.write((value and 0xFF).toInt())
  }
}
