package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.local.PositionEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.StatusLogRepository
import com.example.data.model.MotionState
import com.example.data.model.TrackingRuntimeState
import com.example.data.network.BatteryMonitor
import com.example.data.network.OsmAndClient
import com.example.data.network.PositionDispatcher
import com.example.data.network.SendResult
import com.example.location.LocationEngineFactory
import com.example.location.TrackingLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrackingController(
  private val context: Context,
  private val preferencesRepository: PreferencesRepository,
  private val statusLogRepository: StatusLogRepository,
  private val positionDispatcher: PositionDispatcher,
  private val batteryMonitor: BatteryMonitor,
  private val osmAndClient: OsmAndClient,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _runtimeState = MutableStateFlow(TrackingRuntimeState())
  val runtimeState: StateFlow<TrackingRuntimeState> = _runtimeState.asStateFlow()

  fun hasForegroundLocationPermission(): Boolean {
    val fine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  fun startTracking() {
    scope.launch {
      if (!hasForegroundLocationPermission()) {
        statusLogRepository.log("Cannot start tracking: location permission missing", "ERROR")
        _runtimeState.update { it.copy(lastError = "Location permission missing") }
        return@launch
      }
      preferencesRepository.setTracking(true)
      val intent =
        Intent(context, TrackingForegroundService::class.java).apply {
          action = TrackingForegroundService.ACTION_START
        }
      try {
        ContextCompat.startForegroundService(context, intent)
      } catch (e: Exception) {
        statusLogRepository.log("Failed to start service: ${e.message}", "ERROR")
        _runtimeState.update { it.copy(lastError = e.message) }
      }
    }
  }

  fun stopTracking() {
    scope.launch {
      preferencesRepository.setTracking(false)
      val intent =
        Intent(context, TrackingForegroundService::class.java).apply {
          action = TrackingForegroundService.ACTION_STOP
        }
      context.startService(intent)
      TrackingWatchdogWorker.cancel(context)
    }
  }

  fun sendSingleLocation(alarm: String? = null) {
    scope.launch {
      if (!hasForegroundLocationPermission()) {
        statusLogRepository.log("Cannot fetch location: permission missing", "ERROR")
        return@launch
      }
      val prefs = preferencesRepository.getCurrent()
      val engine = LocationEngineFactory.create(context, prefs.preferPlatformProviders)
      val loc = engine.fetchSingleLocation(prefs)
      if (loc == null) {
        statusLogRepository.log(
          if (alarm == "sos") "SOS failed: unable to acquire location" else "Send location failed: no fix",
          "WARN",
        )
        return@launch
      }
      val entity = toPositionEntity(prefs.deviceId, loc, alarm)
      // "Enviar ubicación" bypasses the buffer directly; SOS sends immediately with buffer fallback if offline
      val forceDirect = alarm == null
      val result = positionDispatcher.enqueueOrSend(entity, forceDirect = forceDirect)
      if (result is SendResult.Success) {
        updateLastPosition(loc)
      }
    }
  }

  suspend fun toPositionEntity(
    deviceId: String,
    location: TrackingLocation,
    alarm: String? = null,
  ): PositionEntity {
    val batt = batteryMonitor.read()
    return PositionEntity(
      deviceId = deviceId,
      time = location.timeMs,
      latitude = location.latitude,
      longitude = location.longitude,
      altitude = location.altitude,
      speed = location.speedMps,
      course = location.bearingDeg,
      accuracy = location.accuracyMeters,
      battery = batt.levelPercent,
      charging = batt.isCharging,
      mock = location.isMock,
      alarm = alarm,
    )
  }

  suspend fun registerPushToken(token: String) {
    val prefs = preferencesRepository.getCurrent()
    val ok = osmAndClient.registerNotificationToken(prefs.serverUrl, prefs.deviceId, token)
    if (ok) {
      statusLogRepository.log("Push notification token registered")
    }
  }

  fun updateServiceStatus(running: Boolean, provider: String = "", motionState: MotionState = MotionState.MOVING) {
    _runtimeState.update {
      it.copy(
        isServiceRunning = running,
        activeProvider = provider,
        motionState = motionState,
        lastError = if (running) null else it.lastError,
      )
    }
  }

  fun updateMotionState(motionState: MotionState) {
    _runtimeState.update { it.copy(motionState = motionState) }
  }

  fun updateLastPosition(location: TrackingLocation) {
    _runtimeState.update {
      it.copy(
        lastSentTimeMs = System.currentTimeMillis(),
        lastLatitude = location.latitude,
        lastLongitude = location.longitude,
        lastError = null,
      )
    }
  }

  fun recordRuntimeError(message: String) {
    _runtimeState.update { it.copy(lastError = message) }
  }
}
