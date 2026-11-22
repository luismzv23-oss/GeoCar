package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import com.example.data.model.TrackingAccuracy
import com.example.data.model.TrackingPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.wayPulseDataStore: DataStore<Preferences> by preferencesDataStore(name = "waypulse_prefs")

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

  private object Keys {
    val DEVICE_ID = stringPreferencesKey("id")
    val SERVER_URL = stringPreferencesKey("url")
    val ACCURACY = stringPreferencesKey("accuracy")
    val DISTANCE = intPreferencesKey("distance")
    val INTERVAL = intPreferencesKey("interval")
    val ANGLE = intPreferencesKey("angle")
    val HEARTBEAT = intPreferencesKey("heartbeat")
    val OFFLINE_BUFFER = booleanPreferencesKey("buffer")
    val WAKE_LOCK = booleanPreferencesKey("wakelock")
    val STOP_DETECTION = booleanPreferencesKey("stop_detection")
    val PREFER_PLATFORM_PROVIDERS = booleanPreferencesKey("prefer_platform_providers")
    val PASSWORD_HASH = stringPreferencesKey("password_hash")
    val IS_TRACKING = booleanPreferencesKey("is_tracking")
    val BATTERY_OPT_PROMPTED = booleanPreferencesKey("battery_opt_prompted")
    val LOCATION_DISCLOSURE_ACCEPTED = booleanPreferencesKey("location_disclosure_accepted")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val FIRST_LAUNCH_TS = longPreferencesKey("first_launch_ts")
    val RATE_PROMPT_DISMISSED = booleanPreferencesKey("rate_prompt_dismissed")
  }

  val preferencesFlow: Flow<TrackingPreferences> =
    dataStore.data.map { prefs -> mapPreferences(prefs) }

  suspend fun ensureInitialized() {
    dataStore.edit { prefs ->
      if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
        prefs[Keys.DEVICE_ID] = TrackingPreferences.generateRandomDeviceId()
      }
      if (prefs[Keys.FIRST_LAUNCH_TS] == null) {
        prefs[Keys.FIRST_LAUNCH_TS] = System.currentTimeMillis()
      }
    }
  }

  suspend fun getCurrent(): TrackingPreferences {
    ensureInitialized()
    return preferencesFlow.first()
  }

  suspend fun updateSettings(
    deviceId: String,
    serverUrl: String,
    accuracy: TrackingAccuracy,
    distance: Int,
    interval: Int,
    angle: Int,
    heartbeat: Int,
    offlineBuffer: Boolean,
    wakeLock: Boolean,
    stopDetection: Boolean,
    preferPlatformProviders: Boolean,
  ): Boolean {
    val cleanUrl = serverUrl.trim()
    if (!TrackingPreferences.isValidServerUrl(cleanUrl)) return false

    val cleanDistance = distance.coerceAtLeast(0)
    val cleanInterval = interval.coerceAtLeast(1)
    val cleanAngle = angle.coerceIn(0, 360)
    val cleanHeartbeat = TrackingPreferences.normalizeHeartbeat(heartbeat)

    dataStore.edit { prefs ->
      val trimmedId = deviceId.trim()
      if (trimmedId.isNotEmpty()) {
        prefs[Keys.DEVICE_ID] = trimmedId
      }
      prefs[Keys.SERVER_URL] = cleanUrl
      prefs[Keys.ACCURACY] = accuracy.key
      prefs[Keys.DISTANCE] = cleanDistance
      prefs[Keys.INTERVAL] = cleanInterval
      prefs[Keys.ANGLE] = cleanAngle
      prefs[Keys.HEARTBEAT] = cleanHeartbeat
      prefs[Keys.OFFLINE_BUFFER] = offlineBuffer
      prefs[Keys.WAKE_LOCK] = wakeLock
      prefs[Keys.STOP_DETECTION] = stopDetection
      prefs[Keys.PREFER_PLATFORM_PROVIDERS] = preferPlatformProviders
    }
    return true
  }

  suspend fun applyRemoteMap(values: Map<String, String>): Int {
    var applied = 0
    dataStore.edit { prefs ->
      values["id"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
        prefs[Keys.DEVICE_ID] = it
        applied++
      }
      values["url"]?.trim()?.takeIf { TrackingPreferences.isValidServerUrl(it) }?.let {
        prefs[Keys.SERVER_URL] = it
        applied++
      }
      values["accuracy"]?.let { TrackingAccuracy.fromKey(it) }?.let {
        prefs[Keys.ACCURACY] = it.key
        applied++
      }
      values["distance"]?.toIntOrNull()?.let {
        prefs[Keys.DISTANCE] = it.coerceAtLeast(0)
        applied++
      }
      values["interval"]?.toIntOrNull()?.let {
        prefs[Keys.INTERVAL] = it.coerceAtLeast(1)
        applied++
      }
      values["angle"]?.toIntOrNull()?.let {
        prefs[Keys.ANGLE] = it.coerceIn(0, 360)
        applied++
      }
      values["heartbeat"]?.toIntOrNull()?.let {
        prefs[Keys.HEARTBEAT] = TrackingPreferences.normalizeHeartbeat(it)
        applied++
      }
      values["buffer"]?.toBooleanStrictOrNull()?.let {
        prefs[Keys.OFFLINE_BUFFER] = it
        applied++
      }
      values["wakelock"]?.toBooleanStrictOrNull()?.let {
        prefs[Keys.WAKE_LOCK] = it
        applied++
      }
      values["stop_detection"]?.toBooleanStrictOrNull()?.let {
        prefs[Keys.STOP_DETECTION] = it
        applied++
      }
      values["prefer_platform_providers"]?.toBooleanStrictOrNull()?.let {
        prefs[Keys.PREFER_PLATFORM_PROVIDERS] = it
        applied++
      }
    }
    return applied
  }

  suspend fun setPassword(plainPassword: String) {
    val trimmed = plainPassword.trim()
    dataStore.edit { prefs ->
      if (trimmed.isEmpty()) {
        prefs.remove(Keys.PASSWORD_HASH)
      } else {
        prefs[Keys.PASSWORD_HASH] = PasswordService.hashPassword(trimmed)
      }
    }
  }

  suspend fun clearPassword() {
    dataStore.edit { prefs -> prefs.remove(Keys.PASSWORD_HASH) }
  }

  suspend fun verifyPassword(candidate: String): Boolean {
    val prefs = dataStore.data.first()
    val storedHash = prefs[Keys.PASSWORD_HASH]
    return PasswordService.verifyPassword(candidate, storedHash)
  }

  suspend fun setTracking(active: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.IS_TRACKING] = active }
  }

  suspend fun markBatteryOptimizationPrompted() {
    dataStore.edit { prefs -> prefs[Keys.BATTERY_OPT_PROMPTED] = true }
  }

  suspend fun acceptLocationDisclosure() {
    dataStore.edit { prefs -> prefs[Keys.LOCATION_DISCLOSURE_ACCEPTED] = true }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.key }
  }

  suspend fun setLanguage(language: AppLanguage) {
    dataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = language.key }
  }

  suspend fun dismissRatePrompt() {
    dataStore.edit { prefs -> prefs[Keys.RATE_PROMPT_DISMISSED] = true }
  }

  suspend fun factoryReset() {
    dataStore.edit { prefs ->
      prefs.remove(Keys.PASSWORD_HASH)
      prefs[Keys.IS_TRACKING] = false
      prefs[Keys.SERVER_URL] = TrackingPreferences.DEFAULT_SERVER_URL
      prefs[Keys.ACCURACY] = TrackingAccuracy.MEDIUM.key
      prefs[Keys.DISTANCE] = TrackingPreferences.DEFAULT_DISTANCE_METERS
      prefs[Keys.INTERVAL] = TrackingPreferences.DEFAULT_INTERVAL_SECONDS
      prefs[Keys.ANGLE] = TrackingPreferences.DEFAULT_ANGLE_DEGREES
      prefs[Keys.HEARTBEAT] = TrackingPreferences.DEFAULT_HEARTBEAT_SECONDS
      prefs[Keys.OFFLINE_BUFFER] = true
      prefs[Keys.WAKE_LOCK] = false
      prefs[Keys.STOP_DETECTION] = true
      prefs[Keys.PREFER_PLATFORM_PROVIDERS] = false
    }
  }

  private fun mapPreferences(prefs: Preferences): TrackingPreferences {
    val rawId = prefs[Keys.DEVICE_ID]
    val deviceId = if (rawId.isNullOrBlank()) TrackingPreferences.DEFAULT_DEVICE_ID_PLACEHOLDER else rawId
    return TrackingPreferences(
      deviceId = deviceId,
      serverUrl = prefs[Keys.SERVER_URL] ?: TrackingPreferences.DEFAULT_SERVER_URL,
      accuracy = TrackingAccuracy.fromKey(prefs[Keys.ACCURACY]) ?: TrackingAccuracy.MEDIUM,
      distance = prefs[Keys.DISTANCE] ?: TrackingPreferences.DEFAULT_DISTANCE_METERS,
      interval = prefs[Keys.INTERVAL] ?: TrackingPreferences.DEFAULT_INTERVAL_SECONDS,
      angle = prefs[Keys.ANGLE] ?: TrackingPreferences.DEFAULT_ANGLE_DEGREES,
      heartbeat = prefs[Keys.HEARTBEAT] ?: TrackingPreferences.DEFAULT_HEARTBEAT_SECONDS,
      offlineBuffer = prefs[Keys.OFFLINE_BUFFER] ?: true,
      wakeLock = prefs[Keys.WAKE_LOCK] ?: false,
      stopDetection = prefs[Keys.STOP_DETECTION] ?: true,
      preferPlatformProviders = prefs[Keys.PREFER_PLATFORM_PROVIDERS] ?: false,
      hasPassword = !prefs[Keys.PASSWORD_HASH].isNullOrBlank(),
      isTracking = prefs[Keys.IS_TRACKING] ?: false,
      batteryOptimizationPrompted = prefs[Keys.BATTERY_OPT_PROMPTED] ?: false,
      locationDisclosureAccepted = prefs[Keys.LOCATION_DISCLOSURE_ACCEPTED] ?: false,
      themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
      language = AppLanguage.fromKey(prefs[Keys.APP_LANGUAGE]),
      firstLaunchTimestamp = prefs[Keys.FIRST_LAUNCH_TS] ?: 0L,
      ratePromptDismissed = prefs[Keys.RATE_PROMPT_DISMISSED] ?: false,
    )
  }
}
