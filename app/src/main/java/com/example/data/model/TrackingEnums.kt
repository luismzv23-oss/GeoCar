package com.example.data.model

enum class TrackingAccuracy(val key: String) {
  HIGHEST("highest"),
  HIGH("high"),
  MEDIUM("medium"),
  LOW("low");

  companion object {
    fun fromKey(raw: String?): TrackingAccuracy? {
      if (raw == null) return null
      return entries.firstOrNull { it.key.equals(raw.trim(), ignoreCase = true) }
    }
  }
}

enum class ThemeMode(val key: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromKey(raw: String?): ThemeMode =
      entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SYSTEM
  }
}

enum class AppLanguage(val key: String, val languageTag: String?) {
  SYSTEM("system", null),
  SPANISH("es", "es"),
  ENGLISH("en", "en");

  companion object {
    fun fromKey(raw: String?): AppLanguage =
      entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SYSTEM
  }
}

enum class TransportType(val key: String, val label: String) {
  HTTP("http", "HTTP"),
  TCP("tcp", "TCP"),
  UDP("udp", "UDP");

  companion object {
    fun fromKey(raw: String?): TransportType? {
      if (raw == null) return null
      return entries.firstOrNull { it.key.equals(raw.trim(), ignoreCase = true) }
    }
  }
}

enum class TrackingProtocol(
  val key: String,
  val displayName: String,
  val defaultPort: Int,
  val defaultTransport: TransportType,
  val supportedTransports: List<TransportType>,
) {
  OSMAND(
    key = "osmand",
    displayName = "OsmAnd",
    defaultPort = 5055,
    defaultTransport = TransportType.HTTP,
    supportedTransports = listOf(TransportType.HTTP),
  ),
  GT06(
    key = "gt06",
    displayName = "GT06",
    defaultPort = 5023,
    defaultTransport = TransportType.TCP,
    supportedTransports = listOf(TransportType.TCP),
  ),
  H02(
    key = "h02",
    displayName = "H02",
    defaultPort = 5013,
    defaultTransport = TransportType.TCP,
    supportedTransports = listOf(TransportType.TCP, TransportType.UDP),
  ),
  TK103(
    key = "tk103",
    displayName = "TK103",
    defaultPort = 5002,
    defaultTransport = TransportType.TCP,
    supportedTransports = listOf(TransportType.TCP, TransportType.UDP),
  ),
  TELTONIKA(
    key = "teltonika",
    displayName = "Teltonika",
    defaultPort = 5027,
    defaultTransport = TransportType.TCP,
    supportedTransports = listOf(TransportType.TCP),
  ),
  JT808(
    key = "jt808",
    displayName = "JT808",
    defaultPort = 5015,
    defaultTransport = TransportType.TCP,
    supportedTransports = listOf(TransportType.TCP),
  );

  fun normalizeTransport(candidate: TransportType?): TransportType =
    if (candidate != null && candidate in supportedTransports) candidate else defaultTransport

  companion object {
    fun fromKey(raw: String?): TrackingProtocol? {
      if (raw == null) return null
      return entries.firstOrNull { it.key.equals(raw.trim(), ignoreCase = true) }
    }
  }
}

enum class MotionState {
  MOVING,
  STATIONARY,
}

data class TrackingRuntimeState(
  val isServiceRunning: Boolean = false,
  val motionState: MotionState = MotionState.MOVING,
  val activeProvider: String = "",
  val lastSentTimeMs: Long? = null,
  val lastLatitude: Double? = null,
  val lastLongitude: Double? = null,
  val lastError: String? = null,
)

