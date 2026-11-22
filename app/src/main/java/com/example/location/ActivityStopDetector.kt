package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActivityStopDetector(
  private val context: Context,
  private val onStationaryChanged: (isStationary: Boolean) -> Unit,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var stationaryJob: Job? = null
  private var isStarted = false
  private var currentlyStationary = false

  private val transitionReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent == null || !ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        for (event in result.transitionEvents) {
          when (event.activityType) {
            DetectedActivity.STILL -> {
              if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                scheduleStationaryPause()
              } else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                markMoving()
              }
            }
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE -> {
              if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                markMoving()
              }
            }
          }
        }
      }
    }

  private fun pendingIntent(): PendingIntent {
    val intent = Intent(ACTION_ACTIVITY_TRANSITION).setPackage(context.packageName)
    val flags =
      PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    return PendingIntent.getBroadcast(context, 4102, intent, flags)
  }

  @SuppressLint("MissingPermission")
  fun start() {
    if (isStarted) return
    isStarted = true
    currentlyStationary = false

    val hasPerm =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    if (!hasPerm) return

    try {
      val filter = IntentFilter(ACTION_ACTIVITY_TRANSITION)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(transitionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(transitionReceiver, filter)
      }

      val transitions =
        listOf(
          ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
          ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            .build(),
          ActivityTransition.Builder()
            .setActivityType(DetectedActivity.WALKING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
          ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
          ActivityTransition.Builder()
            .setActivityType(DetectedActivity.ON_BICYCLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
          ActivityTransition.Builder()
            .setActivityType(DetectedActivity.RUNNING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        )
      val request = ActivityTransitionRequest(transitions)
      ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request, pendingIntent())
    } catch (_: Exception) {
      // Fallback if Google Play Services ActivityRecognition is unavailable on device
    }
  }

  fun onLocationMovementObserved() {
    if (currentlyStationary) {
      markMoving()
    }
  }

  private fun scheduleStationaryPause() {
    stationaryJob?.cancel()
    stationaryJob =
      scope.launch {
        delay(STATIONARY_DELAY_MS)
        if (!currentlyStationary) {
          currentlyStationary = true
          onStationaryChanged(true)
        }
      }
  }

  private fun markMoving() {
    stationaryJob?.cancel()
    if (currentlyStationary) {
      currentlyStationary = false
      onStationaryChanged(false)
    }
  }

  @SuppressLint("MissingPermission")
  fun stop() {
    if (!isStarted) return
    isStarted = false
    stationaryJob?.cancel()
    currentlyStationary = false
    runCatching { context.unregisterReceiver(transitionReceiver) }
    runCatching {
      ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent())
    }
  }

  companion object {
    private const val ACTION_ACTIVITY_TRANSITION = "com.aistudio.waypulse.ACTIVITY_TRANSITION"
    private const val STATIONARY_DELAY_MS = 60_000L
  }
}
