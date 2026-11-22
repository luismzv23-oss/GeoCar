package com.example.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Build
import android.os.Bundle
import com.example.data.local.PreferencesRepository
import com.example.data.local.StatusLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MdmRestrictionsManager(
  private val context: Context,
  private val preferencesRepository: PreferencesRepository,
  private val statusLogRepository: StatusLogRepository,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var registered = false

  private val restrictionsReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        applyManagedRestrictions()
      }
    }

  fun startObserving() {
    applyManagedRestrictions()
    if (registered) return
    val filter = IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(restrictionsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("UnspecifiedRegisterReceiverFlag")
      context.registerReceiver(restrictionsReceiver, filter)
    }
    registered = true
  }

  fun applyManagedRestrictions() {
    val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager ?: return
    val bundle: Bundle = runCatching { rm.applicationRestrictions }.getOrNull() ?: return
    if (bundle.isEmpty) return

    val updates = mutableMapOf<String, String>()
    for (key in RemoteConfigParser.SUPPORTED_KEYS) {
      if (!bundle.containsKey(key)) continue
      @Suppress("DEPRECATION")
      val value = bundle.get(key) ?: continue
      updates[key] = value.toString()
    }
    if (updates.isNotEmpty()) {
      scope.launch {
        val count = preferencesRepository.applyRemoteMap(updates)
        if (count > 0) {
          statusLogRepository.log("MDM managed configuration applied ($count keys)")
        }
      }
    }
  }
}
