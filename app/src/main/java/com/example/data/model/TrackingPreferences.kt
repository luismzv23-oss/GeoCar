package com.example.data.model

import java.net.URI
import kotlin.random.Random

data class TrackingPreferences(
  val deviceId: String = DEFAULT_DEVICE_ID_PLACEHOLDER,
  val serverUrl: String = DEFAULT_SERVER_URL,
  val protocol: TrackingProtocol = TrackingProtocol.OSMAND,
  val transport: TransportType = TransportType.HTTP,
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

    fun isValidServerEndpoint(rawEndpoint: String, protocol: TrackingProtocol): Boolean {
      if (protocol == TrackingProtocol.OSMAND) {
        return isValidServerUrl(rawEndpoint)
      }
      return parseHostAndPort(rawEndpoint, protocol.defaultPort) != null
    }

    fun parseHostAndPort(rawEndpoint: String, defaultPort: Int): Pair<String, Int>? {
      val trimmed = rawEndpoint.trim()
      if (trimmed.isEmpty()) return null
      return try {
        val normalized =
          if (trimmed.contains("://")) {
            trimmed
          } else {
            "tcp://$trimmed"
          }
        val uri = URI(normalized)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "tcp" && scheme != "udp" && scheme != "http" && scheme != "https") {
          return null
        }
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port != -1) uri.port else defaultPort
        if (port !in 1..65535) return null
        host to port
      } catch (_: Exception) {
        null
      }
    }

    fun rewriteEndpointForProtocol(
      currentEndpoint: String,
      targetProtocol: TrackingProtocol,
    ): String {
      val trimmed = currentEndpoint.trim()
      if (trimmed.isEmpty()) {
        return if (targetProtocol == TrackingProtocol.OSMAND) {
          "http://demo.traccar.org:${targetProtocol.defaultPort}"
        } else {
          "demo.traccar.org:${targetProtocol.defaultPort}"
        }
      }

      val parsedUri =
        try {
          URI(if (trimmed.contains("://")) trimmed else "tcp://$trimmed")
        } catch (_: Exception) {
          null
        }
      val host = parsedUri?.host?.takeIf { it.isNotBlank() } ?: "demo.traccar.org"

      return if (targetProtocol == TrackingProtocol.OSMAND) {
        val scheme =
          when (parsedUri?.scheme?.lowercase()) {
            "https" -> "https"
            else -> "http"
          }
        val path = parsedUri?.rawPath?.takeIf { it.isNotBlank() && it != "/" } ?: ""
        "$scheme://$host:${targetProtocol.defaultPort}$path"
      } else {
        "$host:${targetProtocol.defaultPort}"
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
