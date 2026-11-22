package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.example.ui.components.PasswordPromptDialog
import com.example.ui.theme.WayPulseTheme
import kotlinx.coroutines.launch

class QuickActionActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val action = intent?.action.orEmpty()
    val app = application as WayPulseApplication
    val container = app.container

    if (action == ACTION_SOS) {
      lifecycleScope.launch {
        container.statusLogRepository.log("Shortcut SOS triggered", "WARN")
        container.trackingController.sendSingleLocation(alarm = "sos")
        Toast.makeText(this@QuickActionActivity, R.string.main_sos, Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
      }
      return
    }

    setContent {
      var showPasswordPrompt by remember { mutableStateOf(false) }
      var passwordError by remember { mutableStateOf<String?>(null) }
      val scope = rememberCoroutineScope()
      val wrongPwd = stringResource(R.string.password_prompt_error)

      fun executeAndClose() {
        when (action) {
          ACTION_START -> {
            container.trackingController.startTracking()
            Toast.makeText(
              this@QuickActionActivity,
              R.string.shortcut_start_long,
              Toast.LENGTH_SHORT,
            ).show()
          }
          ACTION_STOP -> {
            container.trackingController.stopTracking()
            Toast.makeText(
              this@QuickActionActivity,
              R.string.shortcut_stop_long,
              Toast.LENGTH_SHORT,
            ).show()
          }
        }
        finishAndRemoveTask()
      }

      LaunchedEffect(action) {
        val prefs = container.preferencesRepository.getCurrent()
        if (prefs.hasPassword) {
          showPasswordPrompt = true
        } else {
          executeAndClose()
        }
      }

      WayPulseTheme {
        if (showPasswordPrompt) {
          PasswordPromptDialog(
            errorText = passwordError,
            onVerify = { input ->
              scope.launch {
                if (container.preferencesRepository.verifyPassword(input)) {
                  showPasswordPrompt = false
                  executeAndClose()
                } else {
                  passwordError = wrongPwd
                }
              }
            },
            onDismiss = { finishAndRemoveTask() },
          )
        }
      }
    }
  }

  companion object {
    const val ACTION_START = "com.aistudio.waypulse.SHORTCUT_START"
    const val ACTION_STOP = "com.aistudio.waypulse.SHORTCUT_STOP"
    const val ACTION_SOS = "com.aistudio.waypulse.SHORTCUT_SOS"
  }
}
