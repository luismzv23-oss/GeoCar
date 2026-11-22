package com.example.location

import android.location.Location
import android.os.Build

data class TrackingLocation(
  val latitude: Double,
  val longitude: Double,
  val altitude: Double,
  val speedMps: Double,
  val bearingDeg: Double,
  val accuracyMeters: Double,
  val timeMs: Long,
  val isMock: Boolean,
  val provider: String,
) {
  companion object {
    fun fromAndroidLocation(location: Location): TrackingLocation {
      val mock =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          location.isMock
        } else {
          @Suppress("DEPRECATION")
          location.isFromMockProvider
        }
      return TrackingLocation(
        latitude = location.latitude,
        longitude = location.longitude,
        altitude = if (location.hasAltitude()) location.altitude else 0.0,
        speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
        bearingDeg = if (location.hasBearing()) location.bearing.toDouble() else 0.0,
        accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0,
        timeMs = if (location.time > 0L) location.time else System.currentTimeMillis(),
        isMock = mock,
        provider = location.provider ?: "unknown",
      )
    }
  }
}
