package com.example.data.network

import com.example.data.local.PositionEntity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

object OsmAndProtocol {
  private const val MPS_TO_KNOTS = 1.944

  fun metersPerSecondToKnots(speedMps: Double): Double =
    (speedMps.coerceAtLeast(0.0)) * MPS_TO_KNOTS

  fun buildPositionUrl(serverUrl: String, position: PositionEntity): String? {
    val baseUrl = serverUrl.trim().toHttpUrlOrNull() ?: return null
    val timestampSeconds = position.time / 1000L
    val speedKnots = metersPerSecondToKnots(position.speed)

    val builder =
      baseUrl
        .newBuilder()
        .addQueryParameter("id", position.deviceId)
        .addQueryParameter("timestamp", timestampSeconds.toString())
        .addQueryParameter("lat", String.format(Locale.US, "%.6f", position.latitude))
        .addQueryParameter("lon", String.format(Locale.US, "%.6f", position.longitude))
        .addQueryParameter("speed", String.format(Locale.US, "%.2f", speedKnots))
        .addQueryParameter("bearing", String.format(Locale.US, "%.1f", position.course))
        .addQueryParameter("altitude", String.format(Locale.US, "%.1f", position.altitude))
        .addQueryParameter("accuracy", String.format(Locale.US, "%.1f", position.accuracy))
        .addQueryParameter("batt", String.format(Locale.US, "%.0f", position.battery))
        .addQueryParameter("charge", position.charging.toString())

    if (position.mock) {
      builder.addQueryParameter("mock", "true")
    }
    if (!position.alarm.isNullOrBlank()) {
      builder.addQueryParameter("alarm", position.alarm)
    }

    return builder.build().toString()
  }
}
