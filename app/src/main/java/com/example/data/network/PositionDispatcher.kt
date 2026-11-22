package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.local.PositionDao
import com.example.data.local.PositionEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.StatusLogRepository
import com.example.data.model.TrackingPreferences
import com.example.data.model.TrackingProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PositionDispatcher(
  private val context: Context,
  private val positionDao: PositionDao,
  private val preferencesRepository: PreferencesRepository,
  private val statusLogRepository: StatusLogRepository,
  private val osmAndClient: OsmAndClient,
  private val socketTelemetryClient: SocketTelemetryClient = SocketTelemetryClient(),
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val flushMutex = Mutex()
  private var retryJob: Job? = null
  private var currentBackoffMs: Long = INITIAL_BACKOFF_MS
  private var networkCallbackRegistered = false

  val queuedCountFlow: Flow<Int> = positionDao.observeCount()

  fun startNetworkMonitoring() {
    if (networkCallbackRegistered) return
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
    val request =
      NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    try {
      cm.registerNetworkCallback(
        request,
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            triggerFlush()
          }
        },
      )
      networkCallbackRegistered = true
    } catch (_: Exception) {
      // Ignore if permission or OEM restriction prevents callback registration
    }
  }

  suspend fun enqueueOrSend(position: PositionEntity, forceDirect: Boolean = false): SendResult {
    val prefs = preferencesRepository.getCurrent()
    if (forceDirect || !prefs.offlineBuffer) {
      val result = sendSingleDirect(prefs, position)
      return result
    }

    positionDao.insert(position)
    val count = positionDao.getCount()
    if (count > MAX_BUFFER_ROWS) {
      positionDao.deleteOldestBatch(count - MAX_BUFFER_ROWS)
      statusLogRepository.log("Buffer limit reached; trimmed oldest positions", "WARN")
    }
    triggerFlush()
    return SendResult.Success(200)
  }

  private suspend fun dispatchViaSelectedProtocol(
    prefs: TrackingPreferences,
    position: PositionEntity,
  ): SendResult =
    if (prefs.protocol == TrackingProtocol.OSMAND) {
      osmAndClient.sendPosition(prefs.serverUrl, position)
    } else {
      socketTelemetryClient.sendPosition(prefs, position)
    }

  private suspend fun sendSingleDirect(
    prefs: TrackingPreferences,
    position: PositionEntity,
  ): SendResult {
    if (!isNetworkAvailable()) {
      statusLogRepository.log("Direct send skipped: no connectivity", "WARN")
      return SendResult.NetworkFailure("No connectivity")
    }
    val result = dispatchViaSelectedProtocol(prefs, position)
    when (result) {
      is SendResult.Success -> {
        val tag = if (position.alarm == "sos") "SOS sent" else "Position sent"
        val detail = result.detail ?: "${prefs.protocol.displayName} HTTP ${result.statusCode}"
        statusLogRepository.log("$tag ($detail)")
      }
      is SendResult.HttpFailure -> {
        statusLogRepository.log(
          "Send failed: HTTP ${result.statusCode} ${result.message}",
          "ERROR",
        )
      }
      is SendResult.NetworkFailure -> {
        statusLogRepository.log("Send failed: ${result.error}", "ERROR")
      }
      SendResult.InvalidUrl -> {
        statusLogRepository.log("Send failed: invalid server endpoint", "ERROR")
      }
    }
    return result
  }

  fun triggerFlush() {
    retryJob?.cancel()
    retryJob =
      scope.launch {
        flushQueueInternal()
      }
  }

  suspend fun flushQueueInternal() {
    flushMutex.withLock {
      while (true) {
        val oldest = positionDao.getOldest() ?: run {
          currentBackoffMs = INITIAL_BACKOFF_MS
          return
        }

        if (!isNetworkAvailable()) {
          statusLogRepository.log("Waiting for connectivity (${positionDao.getCount()} queued)")
          return
        }

        val prefs = preferencesRepository.getCurrent()
        when (val result = dispatchViaSelectedProtocol(prefs, oldest)) {
          is SendResult.Success -> {
            positionDao.deleteById(oldest.id)
            currentBackoffMs = INITIAL_BACKOFF_MS
            val label = if (oldest.alarm == "sos") "SOS delivered" else "Position delivered"
            val detail = result.detail ?: "${prefs.protocol.displayName} HTTP ${result.statusCode}"
            statusLogRepository.log("$label ($detail)")
          }
          is SendResult.HttpFailure -> {
            statusLogRepository.log(
              "Server error HTTP ${result.statusCode}; retry in ${currentBackoffMs / 1000}s",
              "ERROR",
            )
            scheduleRetry()
            return
          }
          is SendResult.NetworkFailure -> {
            statusLogRepository.log(
              "Network error (${result.error}); retry in ${currentBackoffMs / 1000}s",
              "WARN",
            )
            scheduleRetry()
            return
          }
          SendResult.InvalidUrl -> {
            statusLogRepository.log("Invalid server endpoint; queue paused", "ERROR")
            return
          }
        }
      }
    }
  }

  private fun scheduleRetry() {
    val waitMs = currentBackoffMs
    currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
    retryJob?.cancel()
    retryJob =
      scope.launch {
        delay(waitMs)
        flushQueueInternal()
      }
  }

  fun isNetworkAvailable(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  companion object {
    const val MAX_BUFFER_ROWS = 50_000
    const val INITIAL_BACKOFF_MS = 5_000L
    const val MAX_BACKOFF_MS = 300_000L
  }
}
