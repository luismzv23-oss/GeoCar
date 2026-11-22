package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.di.AppContainer
import com.example.remote.PushCommandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WayPulseApplication : Application() {
  lateinit var container: AppContainer
    private set

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    createNotificationChannels()
    container.mdmRestrictionsManager.startObserving()

    appScope.launch {
      container.preferencesRepository.ensureInitialized()
      val prefs = container.preferencesRepository.getCurrent()
      if (prefs.isTracking && !container.trackingController.runtimeState.value.isServiceRunning) {
        container.trackingController.startTracking()
      }
      PushCommandService.syncFcmTokenIfConfigured(this@WayPulseApplication)
    }
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java)
      val trackingChannel =
        NotificationChannel(
          CHANNEL_TRACKING,
          getString(R.string.notif_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.notif_channel_desc)
          setShowBadge(false)
        }

      val alertsChannel =
        NotificationChannel(
          CHANNEL_ALERTS,
          getString(R.string.app_name),
          NotificationManager.IMPORTANCE_HIGH,
        )

      nm.createNotificationChannel(trackingChannel)
      nm.createNotificationChannel(alertsChannel)
    }
  }

  companion object {
    const val CHANNEL_TRACKING = "waypulse_tracking"
    const val CHANNEL_ALERTS = "waypulse_alerts"
  }
}
