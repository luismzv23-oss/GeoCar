package com.example.data.network.protocol

import com.example.data.local.PositionEntity
import java.util.Calendar
import java.util.Locale

object Tk103Encoder {

  fun buildMessage(position: PositionEntity): String {
    val id12 = ProtocolUtils.normalizeNumericId(position.deviceId, 12)
    val isSos = position.alarm.equals("sos", ignoreCase = true)
    // BR00 = standard GPS report; BO01 + '2' = SOS alarm
    val cmd = if (isSos) "BO012" else "BR00"

    val cal = ProtocolUtils.utcCalendar(position.time)
    val yymmdd =
      String.format(
        Locale.US,
        "%02d%02d%02d",
        cal.get(Calendar.YEAR) % 100,
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
      )
    val hhmmss =
      String.format(
        Locale.US,
        "%02d%02d%02d",
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
      )

    val latStr = ProtocolUtils.formatLatitudeDm(position.latitude)
    val latHem = if (position.latitude >= 0) "N" else "S"
    val lonStr = ProtocolUtils.formatLongitudeDm(position.longitude)
    val lonHem = if (position.longitude >= 0) "E" else "W"

    // Speed in km/h formatted as 5 chars (000.0)
    val kmh = (position.speed * 3.6).coerceIn(0.0, 999.9)
    val speedStr = String.format(Locale.US, "%05.1f", kmh)
    val courseStr = String.format(Locale.US, "%06.2f", position.course.coerceIn(0.0, 359.99))

    return "($id12$cmd${yymmdd}A$latStr$latHem$lonStr$lonHem$speedStr$hhmmss$courseStr" +
      "00000000L00000000)"
  }
}
