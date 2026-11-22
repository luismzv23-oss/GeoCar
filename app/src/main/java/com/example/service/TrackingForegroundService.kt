package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.WayPulseApplication
import com.example.data.model.MotionState
import com.example.data.model.TrackingPreferences
import com.example.location.ActivityStopDetector
import com.example.location.LocationEngine
import com.example.location.LocationEngineFactory
import com.example.location.PositionFilter
import com.example.location.TrackingLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TrackingForegroundService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val positionFilter = PositionFilter()

  private var locationEngine: LocationEngine? = null
  private var stopDetector: ActivityStopDetector? = null
  private var wakeLock: PowerManager.WakeLock? = null

  private var heartbeatJob: Job? = null
  private var prefsObserverJob: Job? = null
  private var permissionWatchJob: Job? = null

  private var currentPrefs: TrackingPreferences = TrackingPreferences()
  private var isStationary = false
  private var lastDispatchTimeMs = 0L
  private var lastKnownLocation: TrackingLocation? = null
  private var queuedCount = 0

  private val appContainer
    get() = (application as WayPulseApplication).container

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    appContainer.positionDispatcher.startNetworkMonitoring()
    serviceScope.launch {
      appContainer.positionDispatcher.queuedCountFlow.collect { count ->
        queuedCount = count
        if (appContainer.trackingController.runtimeState.value.isServiceRunning) {
          refreshNotification()
        }
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopTrackingGracefully(userInitiated = true)
      return START_NOT_STICKY
    }

    if (!appContainer.trackingController.hasForegroundLocationPermission()) {
      serviceScope.launch {
        appContainer.statusLogRepository.log(
          "Location permission revoked or missing; tracking stopped",
          "ERROR",
        )
        appContainer.preferencesRepository.setTracking(false)
        postPermissionWarningNotification()
      }
      stopSelf()
      return START_NOT_STICKY
    }

    try {
      val notification = buildNotification()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (e: SecurityException) {
      serviceScope.launch {
        appContainer.statusLogRepository.log(
          "Foreground location service rejected by OS: ${e.message}",
          "ERROR",
        )
        appContainer.preferencesRepository.setTracking(false)
        postPermissionWarningNotification()
      }
      stopSelf()
      return START_NOT_STICKY
    }

    startOrRestartPipeline()
    TrackingWatchdogWorker.schedule(applicationContext)
    return START_STICKY
  }

  private fun startOrRestartPipeline() {
    prefsObserverJob?.cancel()
    prefsObserverJob =
      serviceScope.launch {
        currentPrefs = appContainer.preferencesRepository.getCurrent()
        appContainer.statusLogRepository.log(
          "Service started (ID=${currentPrefs.deviceId}, accuracy=${currentPrefs.accuracy.key})"
        )
        applyPipeline(currentPrefs)

        appContainer.preferencesRepository.preferencesFlow
          .distinctUntilChanged()
          .collect { updated ->
            if (!updated.isTracking) {
              stopTrackingGracefully(userInitiated = true)
              return@collect
            }
            val needsEngineReload =
              updated.accuracy != currentPrefs.accuracy ||
                updated.distance != currentPrefs.distance ||
                updated.interval != currentPrefs.interval ||
                updated.preferPlatformProviders != currentPrefs.preferPlatformProviders ||
                updated.stopDetection != currentPrefs.stopDetection ||
                updated.wakeLock != currentPrefs.wakeLock ||
                updated.heartbeat != currentPrefs.heartbeat
            currentPrefs = updated
            if (needsEngineReload) {
              applyPipeline(updated)
            }
            refreshNotification()
          }
      }

    permissionWatchJob?.cancel()
    permissionWatchJob =
      serviceScope.launch {
        while (true) {
          delay(15_000L)
          if (!appContainer.trackingController.hasForegroundLocationPermission()) {
            appContainer.statusLogRepository.log(
              "Location permission was revoked while service was running",
              "ERROR",
            )
            appContainer.trackingController.recordRuntimeError("Location permission revoked")
            postPermissionWarningNotification()
            stopTrackingGracefully(userInitiated = false)
            break
          }
        }
      }
  }

  private fun applyPipeline(prefs: TrackingPreferences) {
    locationEngine?.stopUpdates()
    stopDetector?.stop()
    positionFilter.reset()
    isStationary = false

    val engine = LocationEngineFactory.create(applicationContext, prefs.preferPlatformProviders)
    locationEngine = engine

    appContainer.trackingController.updateServiceStatus(
      running = true,
      provider = engine.engineName,
      motionState = MotionState.MOVING,
    )

    if (!isStationary) {
      startLocationUpdates(engine, prefs)
    }

    if (prefs.stopDetection) {
      val detector =
        ActivityStopDetector(applicationContext) { stationary ->
          handleStationaryChange(stationary)
        }
      stopDetector = detector
      detector.start()
    } else {
      stopDetector = null
    }

    updateWakeLock(prefs.wakeLock && !isStationary)
    restartHeartbeat(prefs.heartbeat)
    refreshNotification()
  }

  private fun startLocationUpdates(engine: LocationEngine, prefs: TrackingPreferences) {
    if (!appContainer.trackingController.hasForegroundLocationPermission()) return
    try {
      engine.startUpdates(prefs) { location ->
        lastKnownLocation = location
        stopDetector?.onLocationMovementObserved()
        if (positionFilter.shouldAccept(location, currentPrefs)) {
          dispatchLocation(location)
        }
      }
    } catch (e: SecurityException) {
      serviceScope.launch {
        appContainer.statusLogRepository.log("Location update failed: ${e.message}", "ERROR")
      }
    }
  }

  private fun handleStationaryChange(stationary: Boolean) {
    if (isStationary == stationary) return
    isStationary = stationary
    val engine = locationEngine
    if (stationary) {
      engine?.stopUpdates()
      updateWakeLock(false)
      appContainer.trackingController.updateMotionState(MotionState.STATIONARY)
      serviceScope.launch {
        appContainer.statusLogRepository.log("Stop detection: stationary for 60s, GPS paused")
      }
    } else {
      if (engine != null) {
        startLocationUpdates(engine, currentPrefs)
      }
      updateWakeLock(currentPrefs.wakeLock)
      appContainer.trackingController.updateMotionState(MotionState.MOVING)
      serviceScope.launch {
        appContainer.statusLogRepository.log("Stop detection: movement detected, GPS resumed")
      }
    }
    refreshNotification()
  }

  private fun restartHeartbeat(heartbeatSec: Int) {
    heartbeatJob?.cancel()
    val effective = TrackingPreferences.normalizeHeartbeat(heartbeatSec)
    if (effective <= 0) return

    heartbeatJob =
      serviceScope.launch(Dispatchers.IO) {
        while (true) {
          delay(effective * 1000L)
          val elapsed = System.currentTimeMillis() - lastDispatchTimeMs
          if (elapsed >= effective * 1000L) {
            val loc =
              lastKnownLocation
                ?: locationEngine?.fetchSingleLocation(currentPrefs)
            if (loc != null) {
              val heartbeatLoc = loc.copy(timeMs = System.currentTimeMillis())
              val entity =
                appContainer.trackingController.toPositionEntity(
                  currentPrefs.deviceId,
                  heartbeatLoc,
                )
              appContainer.positionDispatcher.enqueueOrSend(entity)
              lastDispatchTimeMs = System.currentTimeMillis()
              appContainer.trackingController.updateLastPosition(heartbeatLoc)
              appContainer.statusLogRepository.log("Heartbeat position queued (${effective}s)")
            }
          }
        }
      }
  }

  private fun dispatchLocation(location: TrackingLocation) {
    serviceScope.launch(Dispatchers.IO) {
      val entity =
        appContainer.trackingController.toPositionEntity(currentPrefs.deviceId, location)
      appContainer.positionDispatcher.enqueueOrSend(entity)
      lastDispatchTimeMs = System.currentTimeMillis()
      appContainer.trackingController.updateLastPosition(location)
    }
  }

  private fun updateWakeLock(shouldHold: Boolean) {
    if (shouldHold) {
      if (wakeLock?.isHeld != true) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
          pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WayPulse::TrackingWakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
          }
      }
    } else {
      if (wakeLock?.isHeld == true) {
        runCatching { wakeLock?.release() }
      }
    }
  }

  private fun stopTrackingGracefully(userInitiated: Boolean) {
    serviceScope.launch {
      if (userInitiated) {
        appContainer.preferencesRepository.setTracking(false)
      }
      appContainer.statusLogRepository.log("Tracking service stopped")
    }
    locationEngine?.stopUpdates()
    stopDetector?.stop()
    heartbeatJob?.cancel()
    permissionWatchJob?.cancel()
    updateWakeLock(false)
    appContainer.trackingController.updateServiceStatus(running = false)
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    locationEngine?.stopUpdates()
    stopDetector?.stop()
    heartbeatJob?.cancel()
    permissionWatchJob?.cancel()
    prefsObserverJob?.cancel()
    updateWakeLock(false)
    appContainer.trackingController.updateServiceStatus(running = false)
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun buildNotification(): Notification {
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val openPendingIntent =
      PendingIntent.getActivity(
        this,
        101,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(this, TrackingForegroundService::class.java).apply {
        action = ACTION_STOP
      }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        102,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val text =
      if (isStationary) {
        getString(R.string.notif_text_stationary, currentPrefs.deviceId, queuedCount)
      } else {
        getString(R.string.notif_text_active, currentPrefs.deviceId, queuedCount)
      }

    return NotificationCompat.Builder(this, WayPulseApplication.CHANNEL_TRACKING)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(getString(R.string.notif_title))
      .setContentText(text)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openPendingIntent)
      .addAction(0, getString(R.string.notif_action_stop), stopPendingIntent)
      .build()
  }

  private fun refreshNotification() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun postPermissionWarningNotification() {
    val openIntent = Intent(this, MainActivity::class.java)
    val pi =
      PendingIntent.getActivity(
        this,
        103,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notif =
      NotificationCompat.Builder(this, WayPulseApplication.CHANNEL_ALERTS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Location permission revoked. Open WayPulse to resume tracking.")
        .setAutoCancel(true)
        .setContentIntent(pi)
        .build()
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(ALERT_NOTIFICATION_ID, notif)
  }

  companion object {
    const val ACTION_START = "com.aistudio.waypulse.ACTION_START_SERVICE"
    const val ACTION_STOP = "com.aistudio.waypulse.ACTION_STOP_SERVICE"
    const val NOTIFICATION_ID = 7001
    const val ALERT_NOTIFICATION_ID = 7002
  }
}
