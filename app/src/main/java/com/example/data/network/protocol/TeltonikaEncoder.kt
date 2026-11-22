package com.example.data.network.protocol

import com.example.data.local.PositionEntity
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object TeltonikaEncoder {

  private const val CODEC_8 = 0x08
  private const val IO_BATTERY_LEVEL = 0x71
  private const val IO_SOS_ALARM = 0xEC

  fun buildImeiHandshake(deviceId: String): ByteArray {
    val imeiBytes = ProtocolUtils.normalizeNumericId(deviceId, 15).toByteArray(Charsets.US_ASCII)
    val out = ByteArray(2 + imeiBytes.size)
    out[0] = ((imeiBytes.size ushr 8) and 0xFF).toByte()
    out[1] = (imeiBytes.size and 0xFF).toByte()
    imeiBytes.copyInto(out, destinationOffset = 2)
    return out
  }

  fun buildCodec8Packet(position: PositionEntity): ByteArray {
    val dataField = ByteArrayOutputStream(64)
    val isSos = position.alarm.equals("sos", ignoreCase = true)

    // Codec ID & Number of Data 1
    dataField.write(CODEC_8)
    dataField.write(0x01)

    // Timestamp (8B ms BE)
    writeInt64BE(dataField, position.time)

    // Priority: 2 = panic/emergency, 0 = low
    dataField.write(if (isSos) 0x02 else 0x00)

    // GPS Element: lon (4B), lat (4B), alt (2B), angle (2B), sats (1B), speed km/h (2B)
    val lonScaled = (position.longitude * 10_000_000.0).roundToInt()
    val latScaled = (position.latitude * 10_000_000.0).roundToInt()
    writeInt32BE(dataField, lonScaled)
    writeInt32BE(dataField, latScaled)
    writeInt16BE(dataField, position.altitude.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
    writeInt16BE(dataField, position.course.roundToInt().coerceIn(0, 359))
    dataField.write(0x08) // satellites
    val speedKmh = (position.speed * 3.6).roundToInt().coerceIn(0, 65535)
    writeInt16BE(dataField, speedKmh)

    // IO Element
    dataField.write(if (isSos) IO_SOS_ALARM else 0x00) // Event IO ID
    val oneByteCount = if (isSos) 2 else 1
    dataField.write(oneByteCount) // Total IO count
    dataField.write(oneByteCount) // N1 count

    // Battery level %
    dataField.write(IO_BATTERY_LEVEL)
    dataField.write(position.battery.roundToInt().coerceIn(0, 100))

    if (isSos) {
      dataField.write(IO_SOS_ALARM)
      dataField.write(0x01)
    }

    dataField.write(0x00) // N2 count
    dataField.write(0x00) // N4 count
    dataField.write(0x00) // N8 count

    // Number of Data 2
    dataField.write(0x01)

    val dataBytes = dataField.toByteArray()
    val crc = ProtocolUtils.crc16Ibm(dataBytes)

    val packet = ByteArrayOutputStream(4 + 4 + dataBytes.size + 4)
    writeInt32BE(packet, 0) // Preamble 0x00000000
    writeInt32BE(packet, dataBytes.size)
    packet.write(dataBytes)
    writeInt32BE(packet, crc)
    return packet.toByteArray()
  }

  fun parseAcceptedCountAck(ack: ByteArray): Int? {
    if (ack.size < 4) return null
    return ((ack[0].toInt() and 0xFF) shl 24) or
      ((ack[1].toInt() and 0xFF) shl 16) or
      ((ack[2].toInt() and 0xFF) shl 8) or
      (ack[3].toInt() and 0xFF)
  }

  private fun writeInt16BE(out: ByteArrayOutputStream, value: Int) {
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
  }

  private fun writeInt32BE(out: ByteArrayOutputStream, value: Int) {
    out.write((value ushr 24) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
  }

  private fun writeInt64BE(out: ByteArrayOutputStream, value: Long) {
    for (shift in 56 downTo 0 step 8) {
      out.write(((value ushr shift) and 0xFF).toInt())
    }
  }
}
