package com.example.data.network.protocol

import com.example.data.local.PositionEntity
import java.util.Calendar
import java.util.Locale

object H02Encoder {

  fun buildMessage(position: PositionEntity): String {
    val cal = ProtocolUtils.utcCalendar(position.time)
    val hhmmss =
      String.format(
        Locale.US,
        "%02d%02d%02d",
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
      )
    val ddmmyy =
      String.format(
        Locale.US,
        "%02d%02d%02d",
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR) % 100,
      )

    val id = ProtocolUtils.normalizeNumericId(position.deviceId, 10)
    val latStr = ProtocolUtils.formatLatitudeDm(position.latitude)
    val latHem = if (position.latitude >= 0) "N" else "S"
    val lonStr = ProtocolUtils.formatLongitudeDm(position.longitude)
    val lonHem = if (position.longitude >= 0) "E" else "W"

    // Speed in knots
    val knots = (position.speed * 1.943844).coerceAtLeast(0.0)
    val speedStr = String.format(Locale.US, "%06.2f", knots)
    val courseStr = String.format(Locale.US, "%03d", position.course.toInt().coerceIn(0, 359))

    // Active-low 32-bit status mask: bit 1 cleared (0xFFFFFFFD) indicates SOS alarm
    val statusHex =
      if (position.alarm.equals("sos", ignoreCase = true)) {
        "FFFFFFFD"
      } else {
        "FFFFFFFF"
      }

    return "*HQ,$id,V1,$hhmmss,A,$latStr,$latHem,$lonStr,$lonHem,$speedStr,$courseStr,$ddmmyy,$statusHex#"
  }
}
