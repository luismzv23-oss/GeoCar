package com.example.data.network.protocol

import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object ProtocolUtils {

  fun normalizeNumericId(rawId: String, targetLength: Int): String {
    val digits = rawId.filter { it.isDigit() }
    val base = if (digits.isEmpty()) "0" else digits
    return if (base.length >= targetLength) {
      base.takeLast(targetLength)
    } else {
      base.padStart(targetLength, '0')
    }
  }

  fun encodeBcd(digits: String): ByteArray {
    val padded = if (digits.length % 2 != 0) "0$digits" else digits
    val out = ByteArray(padded.length / 2)
    for (i in out.indices) {
      val hi = padded[i * 2].digitToInt()
      val lo = padded[i * 2 + 1].digitToInt()
      out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
  }

  /**
   * CRC-16/X25 (ITU-T / ISO-13239) used by GT06.
   * Verified: crc16X25(byteArrayOf(0x05, 0x01, 0x00, 0x01)) == 0xD9DC.
   */
  fun crc16X25(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
    var crc = 0xFFFF
    for (i in offset until (offset + length)) {
      crc = crc xor (data[i].toInt() and 0xFF)
      repeat(8) {
        crc =
          if ((crc and 1) != 0) {
            (crc ushr 1) xor 0x8408
          } else {
            crc ushr 1
          }
      }
    }
    return (crc xor 0xFFFF) and 0xFFFF
  }

  /**
   * CRC-16/IBM (poly 0xA001, init 0x0000) used by Teltonika Codec 8.
   */
  fun crc16Ibm(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
    var crc = 0x0000
    for (i in offset until (offset + length)) {
      crc = crc xor (data[i].toInt() and 0xFF)
      repeat(8) {
        crc =
          if ((crc and 1) != 0) {
            (crc ushr 1) xor 0xA001
          } else {
            crc ushr 1
          }
      }
    }
    return crc and 0xFFFF
  }

  fun xorChecksum(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Byte {
    var acc = 0
    for (i in offset until (offset + length)) {
      acc = acc xor (data[i].toInt() and 0xFF)
    }
    return (acc and 0xFF).toByte()
  }

  fun jt808Escape(raw: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(raw.size + 8)
    for (b in raw) {
      when (b.toInt() and 0xFF) {
        0x7D -> {
          out.write(0x7D)
          out.write(0x01)
        }
        0x7E -> {
          out.write(0x7D)
          out.write(0x02)
        }
        else -> out.write(b.toInt() and 0xFF)
      }
    }
    return out.toByteArray()
  }

  fun jt808Unescape(escaped: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(escaped.size)
    var i = 0
    while (i < escaped.size) {
      val cur = escaped[i].toInt() and 0xFF
      if (cur == 0x7D && i + 1 < escaped.size) {
        val next = escaped[i + 1].toInt() and 0xFF
        when (next) {
          0x01 -> {
            out.write(0x7D)
            i += 2
            continue
          }
          0x02 -> {
            out.write(0x7E)
            i += 2
            continue
          }
        }
      }
      out.write(cur)
      i++
    }
    return out.toByteArray()
  }

  fun formatLatitudeDm(latitude: Double): String {
    val absVal = abs(latitude)
    val degrees = absVal.toInt()
    val minutes = (absVal - degrees) * 60.0
    return String.format(Locale.US, "%02d%07.4f", degrees, minutes)
  }

  fun formatLongitudeDm(longitude: Double): String {
    val absVal = abs(longitude)
    val degrees = absVal.toInt()
    val minutes = (absVal - degrees) * 60.0
    return String.format(Locale.US, "%03d%07.4f", degrees, minutes)
  }

  fun utcCalendar(epochMillis: Long): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      timeInMillis = epochMillis
    }

  fun gmtPlus8Calendar(epochMillis: Long): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply {
      timeInMillis = epochMillis
    }
}
