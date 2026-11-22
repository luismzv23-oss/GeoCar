package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.MonoCodeStyle

@Composable
fun PasswordPromptDialog(
  errorText: String? = null,
  onVerify: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var password by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.password_prompt_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(stringResource(R.string.password_prompt_hint)) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          isError = errorText != null,
          modifier = Modifier.fillMaxWidth().testTag("password_prompt_input"),
        )
        if (errorText != null) {
          Text(
            text = errorText,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onVerify(password) },
        modifier = Modifier.testTag("password_prompt_confirm"),
      ) {
        Text(stringResource(R.string.confirm))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("password_prompt_cancel"),
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
fun SetPasswordDialog(
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }
  var mismatch by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.password_new_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            mismatch = false
          },
          label = { Text(stringResource(R.string.password_prompt_hint)) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth().testTag("new_password_input"),
        )
        OutlinedTextField(
          value = confirm,
          onValueChange = {
            confirm = it
            mismatch = false
          },
          label = { Text(stringResource(R.string.password_confirm_hint)) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          isError = mismatch,
          modifier = Modifier.fillMaxWidth().testTag("confirm_password_input"),
        )
        if (mismatch) {
          Text(
            text = stringResource(R.string.password_mismatch),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (password.isNotBlank() && password == confirm) {
            onSave(password)
          } else {
            mismatch = true
          }
        },
        modifier = Modifier.testTag("save_password_button"),
      ) {
        Text(stringResource(R.string.apply))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
fun LocationDisclosureDialog(
  onAccept: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.disclosure_dialog_title)) },
    text = {
      Text(
        text = stringResource(R.string.disclosure_dialog_body),
        style = MaterialTheme.typography.bodyLarge,
      )
    },
    confirmButton = {
      Button(
        onClick = onAccept,
        modifier = Modifier.testTag("disclosure_accept_button"),
      ) {
        Text(stringResource(R.string.disclosure_accept))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
fun RemoteConfigConfirmDialog(
  values: Map<String, String>,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.remote_config_title)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.remote_config_body),
          style = MaterialTheme.typography.bodyMedium,
        )
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEach { (k, v) ->
              Text(text = "$k = $v", style = MonoCodeStyle)
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        modifier = Modifier.testTag("remote_config_apply_button"),
      ) {
        Text(stringResource(R.string.apply))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.privacy_title)) },
    text = {
      Text(
        text = stringResource(R.string.privacy_body),
        style = MaterialTheme.typography.bodyLarge,
      )
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
}

@Composable
fun RateAppCard(
  onRateNow: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = stringResource(R.string.rate_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.rate_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
          onClick = onRateNow,
          modifier = Modifier.testTag("rate_now_button"),
        ) {
          Text(stringResource(R.string.rate_now))
        }
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("rate_later_button"),
        ) {
          Text(stringResource(R.string.rate_later))
        }
      }
    }
  }
}
