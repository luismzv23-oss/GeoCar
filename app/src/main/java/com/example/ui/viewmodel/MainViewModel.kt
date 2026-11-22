package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesRepository
import com.example.data.local.StatusLogRepository
import com.example.data.model.TrackingPreferences
import com.example.data.model.TrackingRuntimeState
import com.example.data.network.PositionDispatcher
import com.example.service.TrackingController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val trackingController: TrackingController,
  private val positionDispatcher: PositionDispatcher,
  private val statusLogRepository: StatusLogRepository,
) : ViewModel() {

  val preferences: StateFlow<TrackingPreferences> =
    preferencesRepository.preferencesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = TrackingPreferences(),
    )

  val runtimeState: StateFlow<TrackingRuntimeState> = trackingController.runtimeState

  val queuedCount: StateFlow<Int> =
    positionDispatcher.queuedCountFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = 0,
    )

  suspend fun verifyPassword(candidate: String): Boolean =
    preferencesRepository.verifyPassword(candidate)

  fun setTrackingEnabled(enabled: Boolean) {
    if (enabled) {
      trackingController.startTracking()
    } else {
      trackingController.stopTracking()
    }
  }

  fun sendSingleLocation() {
    viewModelScope.launch {
      statusLogRepository.log("Manual Send Location requested")
      trackingController.sendSingleLocation(alarm = null)
    }
  }

  fun sendSos() {
    viewModelScope.launch {
      statusLogRepository.log("SOS alarm triggered", "WARN")
      trackingController.sendSingleLocation(alarm = "sos")
    }
  }

  fun acceptLocationDisclosure() {
    viewModelScope.launch {
      preferencesRepository.acceptLocationDisclosure()
    }
  }

  fun markBatteryPrompted() {
    viewModelScope.launch {
      preferencesRepository.markBatteryOptimizationPrompted()
    }
  }

  fun applyRemoteConfig(values: Map<String, String>) {
    viewModelScope.launch {
      val count = preferencesRepository.applyRemoteMap(values)
      statusLogRepository.log("Remote link configuration applied ($count keys)")
    }
  }

  fun dismissRatePrompt() {
    viewModelScope.launch {
      preferencesRepository.dismissRatePrompt()
    }
  }

  class Factory(
    private val preferencesRepository: PreferencesRepository,
    private val trackingController: TrackingController,
    private val positionDispatcher: PositionDispatcher,
    private val statusLogRepository: StatusLogRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      MainViewModel(
        preferencesRepository,
        trackingController,
        positionDispatcher,
        statusLogRepository,
      ) as T
  }
}
