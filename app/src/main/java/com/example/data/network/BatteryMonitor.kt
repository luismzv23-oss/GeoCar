package com.example.data.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryStatus(
  val levelPercent: Double,
  val isCharging: Boolean,
)

class BatteryMonitor(private val context: Context) {
  fun read(): BatteryStatus {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val pct =
      if (level >= 0 && scale > 0) {
        (level * 100.0) / scale
      } else {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0).toDouble()
      }
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging =
      status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryStatus(
      levelPercent = pct.coerceIn(0.0, 100.0),
      isCharging = charging,
    )
  }
}
