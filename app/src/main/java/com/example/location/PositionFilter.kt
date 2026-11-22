package com.example.location

import com.example.data.model.TrackingAccuracy
import com.example.data.model.TrackingPreferences
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class PositionFilter {
  private var lastAccepted: TrackingLocation? = null

  fun reset() {
    lastAccepted = null
  }

  fun lastLocation(): TrackingLocation? = lastAccepted

  fun shouldAccept(candidate: TrackingLocation, prefs: TrackingPreferences): Boolean {
    val previous = lastAccepted
    if (previous == null) {
      lastAccepted = candidate
      return true
    }

    val distanceTrigger =
      if (prefs.distance > 0) {
        haversineMeters(
          previous.latitude,
          previous.longitude,
          candidate.latitude,
          candidate.longitude,
        ) >= prefs.distance
      } else {
        false
      }

    val intervalEnabled =
      TrackingPreferences.isIntervalVisible(prefs.accuracy, prefs.distance) && prefs.interval > 0
    val intervalTrigger =
      if (intervalEnabled) {
        (candidate.timeMs - previous.timeMs) >= prefs.interval * 1000L
      } else {
        false
      }

    val angleEnabled =
      TrackingPreferences.isAngleVisible(prefs.accuracy) && prefs.angle > 0
    val angleTrigger =
      if (angleEnabled) {
        angleDifferenceDegrees(previous.bearingDeg, candidate.bearingDeg) >= prefs.angle
      } else {
        false
      }

    val accept =
      if (prefs.distance == 0 && !intervalEnabled && !angleEnabled) {
        true
      } else {
        distanceTrigger || intervalTrigger || angleTrigger
      }

    if (accept) {
      lastAccepted = candidate
    }
    return accept
  }

  companion object {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val dLat = Math.toRadians(lat2 - lat1)
      val dLon = Math.toRadians(lon2 - lon1)
      val rLat1 = Math.toRadians(lat1)
      val rLat2 = Math.toRadians(lat2)
      val a =
        sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
      val c = 2 * atan2(sqrt(a), sqrt(1 - a))
      return EARTH_RADIUS_METERS * c
    }

    fun angleDifferenceDegrees(a: Double, b: Double): Double {
      val diff = abs(((b - a + 540.0) % 360.0) - 180.0)
      return diff
    }
  }
}
