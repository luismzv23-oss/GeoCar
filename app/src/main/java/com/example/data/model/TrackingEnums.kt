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
