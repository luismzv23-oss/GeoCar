package com.example.data.model

import java.net.URI
import kotlin.random.Random

data class TrackingPreferences(
  val deviceId: String = DEFAULT_DEVICE_ID_PLACEHOLDER,
  val serverUrl: String = DEFAULT_SERVER_URL,
  val accuracy: TrackingAccuracy = TrackingAccuracy.MEDIUM,
  val distance: Int = DEFAULT_DISTANCE_METERS,
  val interval: Int = DEFAULT_INTERVAL_SECONDS,
  val angle: Int = DEFAULT_ANGLE_DEGREES,
  val heartbeat: Int = DEFAULT_HEARTBEAT_SECONDS,
  val offlineBuffer: Boolean = true,
  val wakeLock: Boolean = false,
  val stopDetection: Boolean = true,
  val preferPlatformProviders: Boolean = false,
  val hasPassword: Boolean = false,
  val isTracking: Boolean = false,
  val batteryOptimizationPrompted: Boolean = false,
  val locationDisclosureAccepted: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val language: AppLanguage = AppLanguage.SYSTEM,
  val firstLaunchTimestamp: Long = 0L,
  val ratePromptDismissed: Boolean = false,
) {
  companion object {
    const val DEFAULT_DEVICE_ID_PLACEHOLDER = "10000000"
    const val DEFAULT_SERVER_URL = "http://demo.traccar.org:5055"
    const val DEFAULT_DISTANCE_METERS = 75
    const val DEFAULT_INTERVAL_SECONDS = 300
    const val DEFAULT_ANGLE_DEGREES = 0
    const val DEFAULT_HEARTBEAT_SECONDS = 0

    fun generateRandomDeviceId(): String =
      Random.nextInt(10_000_000, 100_000_000).toString()

    fun isValidServerUrl(rawUrl: String): Boolean {
      val trimmed = rawUrl.trim()
      if (trimmed.isEmpty()) return false
      return try {
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        (scheme == "http" || scheme == "https") && !host.isNullOrBlank()
      } catch (_: Exception) {
        false
      }
    }

    fun normalizeHeartbeat(seconds: Int): Int =
      when {
        seconds <= 0 -> 0
        seconds in 1..59 -> 60
        else -> seconds
      }

    fun isIntervalVisible(accuracy: TrackingAccuracy, distance: Int): Boolean =
      accuracy == TrackingAccuracy.HIGHEST || distance == 0

    fun isAngleVisible(accuracy: TrackingAccuracy): Boolean =
      accuracy == TrackingAccuracy.HIGHEST
  }
}
