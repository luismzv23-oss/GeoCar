package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.WayPulseApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootAndUpdateReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (
      action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      val pendingResult = goAsync()
      val app = context.applicationContext as? WayPulseApplication
      CoroutineScope(Dispatchers.IO).launch {
        try {
          if (app != null) {
            val prefs = app.container.preferencesRepository.getCurrent()
            if (prefs.isTracking) {
              app.container.statusLogRepository.log("Auto-restarting tracking after $action")
              app.container.trackingController.startTracking()
            }
          }
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
