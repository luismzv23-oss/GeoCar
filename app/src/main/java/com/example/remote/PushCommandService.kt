package com.example.remote

import android.content.Context
import com.example.WayPulseApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PushCommandService : FirebaseMessagingService() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    val app = applicationContext as? WayPulseApplication ?: return
    scope.launch {
      app.container.trackingController.registerPushToken(token)
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val app = applicationContext as? WayPulseApplication ?: return
    val command =
      message.data["command"]
        ?: message.data["type"]
        ?: message.data["action"]
        ?: return

    scope.launch {
      handlePushCommand(app, command)
    }
  }

  companion object {
    suspend fun handlePushCommand(app: WayPulseApplication, rawCommand: String) {
      val container = app.container
      when (rawCommand.trim()) {
        "positionSingle" -> {
          container.statusLogRepository.log("Push command: positionSingle")
          container.trackingController.sendSingleLocation()
        }
        "positionPeriodic" -> {
          container.statusLogRepository.log("Push command: positionPeriodic")
          container.trackingController.startTracking()
        }
        "positionStop" -> {
          container.statusLogRepository.log("Push command: positionStop")
          container.trackingController.stopTracking()
        }
        "factoryReset" -> {
          container.statusLogRepository.log("Push command: factoryReset", "WARN")
          container.trackingController.stopTracking()
          container.preferencesRepository.factoryReset()
        }
      }
    }

    fun syncFcmTokenIfConfigured(context: Context) {
      val app = context.applicationContext as? WayPulseApplication ?: return
      if (FirebaseApp.getApps(context).isEmpty()) return
      runCatching {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
          if (!token.isNullOrBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
              app.container.trackingController.registerPushToken(token)
            }
          }
        }
      }
    }
  }
}
