package com.example.remote

import android.net.Uri
import com.example.data.model.TrackingAccuracy
import com.example.data.model.TrackingPreferences
import org.json.JSONObject

sealed class RemoteConfigAction {
  data object StartTracking : RemoteConfigAction()
  data object StopTracking : RemoteConfigAction()
  data class ApplySettings(val values: Map<String, String>) : RemoteConfigAction()
  data object Invalid : RemoteConfigAction()
}

object RemoteConfigParser {

  val SUPPORTED_KEYS =
    setOf(
      "url",
      "id",
      "accuracy",
      "distance",
      "interval",
      "angle",
      "heartbeat",
      "buffer",
      "wakelock",
      "stop_detection",
      "prefer_platform_providers",
    )

  fun parseUriString(raw: String): RemoteConfigAction {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return RemoteConfigAction.Invalid

    if (trimmed.startsWith("{")) {
      return parseJson(trimmed)
    }

    val uri =
      try {
        Uri.parse(trimmed)
      } catch (_: Exception) {
        return RemoteConfigAction.Invalid
      }

    val actionParam = uri.getQueryParameter("action")?.lowercase()
    if (actionParam == "start") return RemoteConfigAction.StartTracking
    if (actionParam == "stop") return RemoteConfigAction.StopTracking

    val pathAction = uri.host?.lowercase()
    if ((uri.scheme == "waypulse" || uri.scheme == "traccar") && uri.queryParameterNames.isEmpty()) {
      if (pathAction == "start") return RemoteConfigAction.StartTracking
      if (pathAction == "stop") return RemoteConfigAction.StopTracking
    }

    val extracted = mutableMapOf<String, String>()
    for (name in uri.queryParameterNames) {
      if (name in SUPPORTED_KEYS) {
        uri.getQueryParameter(name)?.let { value ->
          if (isValidKeyValue(name, value)) {
            extracted[name] = value
          }
        }
      }
    }

    // Support QR codes that are directly an OsmAnd/Traccar server URL (without query params)
    if (extracted.isEmpty() && TrackingPreferences.isValidServerUrl(trimmed)) {
      extracted["url"] = trimmed
    }

    return if (extracted.isNotEmpty()) {
      RemoteConfigAction.ApplySettings(extracted)
    } else {
      RemoteConfigAction.Invalid
    }
  }

  private fun parseJson(raw: String): RemoteConfigAction {
    return try {
      val json = JSONObject(raw)
      val actionStr = json.optString("action", "").lowercase()
      if (actionStr == "start") return RemoteConfigAction.StartTracking
      if (actionStr == "stop") return RemoteConfigAction.StopTracking

      val extracted = mutableMapOf<String, String>()
      for (key in SUPPORTED_KEYS) {
        if (json.has(key)) {
          val value = json.opt(key)?.toString() ?: continue
          if (isValidKeyValue(key, value)) {
            extracted[key] = value
          }
        }
      }
      if (extracted.isNotEmpty()) {
        RemoteConfigAction.ApplySettings(extracted)
      } else {
        RemoteConfigAction.Invalid
      }
    } catch (_: Exception) {
      RemoteConfigAction.Invalid
    }
  }

  private fun isValidKeyValue(key: String, value: String): Boolean =
    when (key) {
      "url" -> TrackingPreferences.isValidServerUrl(value)
      "id" -> value.trim().isNotEmpty()
      "accuracy" -> TrackingAccuracy.fromKey(value) != null
      "distance",
      "interval",
      "angle",
      "heartbeat" -> value.toIntOrNull() != null
      "buffer",
      "wakelock",
      "stop_detection",
      "prefer_platform_providers" -> value.lowercase() == "true" || value.lowercase() == "false"
      else -> false
    }
}
