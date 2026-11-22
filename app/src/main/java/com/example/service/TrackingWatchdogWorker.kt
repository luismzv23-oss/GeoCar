package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.WayPulseApplication
import java.util.concurrent.TimeUnit

class TrackingWatchdogWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val app = applicationContext as? WayPulseApplication ?: return Result.success()
    val container = app.container
    val prefs = container.preferencesRepository.getCurrent()
    if (prefs.isTracking && !container.trackingController.runtimeState.value.isServiceRunning) {
      container.statusLogRepository.log("Watchdog restoring killed tracking service", "WARN")
      container.trackingController.startTracking()
    }
    container.positionDispatcher.flushQueueInternal()
    return Result.success()
  }

  companion object {
    private const val WORK_NAME = "waypulse_tracking_watchdog"

    fun schedule(context: Context) {
      val request =
        PeriodicWorkRequestBuilder<TrackingWatchdogWorker>(15, TimeUnit.MINUTES).build()
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
  }
}
