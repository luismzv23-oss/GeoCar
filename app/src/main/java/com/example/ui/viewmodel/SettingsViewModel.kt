package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesRepository
import com.example.data.local.StatusLogRepository
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import com.example.data.model.TrackingAccuracy
import com.example.data.model.TrackingPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val statusLogRepository: StatusLogRepository,
) : ViewModel() {

  val preferences: StateFlow<TrackingPreferences> =
    preferencesRepository.preferencesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = TrackingPreferences(),
    )

  suspend fun saveSettings(
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
    val ok =
      preferencesRepository.updateSettings(
        deviceId = deviceId,
        serverUrl = serverUrl,
        accuracy = accuracy,
        distance = distance,
        interval = interval,
        angle = angle,
        heartbeat = heartbeat,
        offlineBuffer = offlineBuffer,
        wakeLock = wakeLock,
        stopDetection = stopDetection,
        preferPlatformProviders = preferPlatformProviders,
      )
    if (ok) {
      statusLogRepository.log("Settings updated (accuracy=${accuracy.key}, dist=${distance}m)")
    }
    return ok
  }

  fun setPassword(password: String) {
    viewModelScope.launch {
      preferencesRepository.setPassword(password)
      statusLogRepository.log("Protection password updated")
    }
  }

  fun clearPassword() {
    viewModelScope.launch {
      preferencesRepository.clearPassword()
      statusLogRepository.log("Protection password cleared")
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      preferencesRepository.setThemeMode(mode)
    }
  }

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch {
      preferencesRepository.setLanguage(language)
    }
  }

  fun applyRemoteSettings(values: Map<String, String>) {
    viewModelScope.launch {
      val count = preferencesRepository.applyRemoteMap(values)
      statusLogRepository.log("QR configuration applied ($count keys)")
    }
  }

  class Factory(
    private val preferencesRepository: PreferencesRepository,
    private val statusLogRepository: StatusLogRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      SettingsViewModel(preferencesRepository, statusLogRepository) as T
  }
}
