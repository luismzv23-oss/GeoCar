package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import com.example.data.model.TrackingAccuracy
import com.example.data.model.TrackingPreferences
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.components.SetPasswordDialog
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onBack: () -> Unit,
  onOpenQrScanner: () -> Unit,
  onOpenBatteryGuide: () -> Unit,
  onOpenPermissionsWizard: () -> Unit,
) {
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var deviceIdText by remember(prefs.deviceId) { mutableStateOf(prefs.deviceId) }
  var serverUrlText by remember(prefs.serverUrl) { mutableStateOf(prefs.serverUrl) }
  var urlError by remember { mutableStateOf(false) }
  var accuracy by remember(prefs.accuracy) { mutableStateOf(prefs.accuracy) }
  var distanceText by remember(prefs.distance) { mutableStateOf(prefs.distance.toString()) }
  var intervalText by remember(prefs.interval) { mutableStateOf(prefs.interval.toString()) }
  var angleText by remember(prefs.angle) { mutableStateOf(prefs.angle.toString()) }
  var heartbeatText by remember(prefs.heartbeat) { mutableStateOf(prefs.heartbeat.toString()) }

  var showAdvanced by remember { mutableStateOf(false) }
  var offlineBuffer by remember(prefs.offlineBuffer) { mutableStateOf(prefs.offlineBuffer) }
  var wakeLock by remember(prefs.wakeLock) { mutableStateOf(prefs.wakeLock) }
  var stopDetection by remember(prefs.stopDetection) { mutableStateOf(prefs.stopDetection) }
  var preferPlatform by remember(prefs.preferPlatformProviders) {
    mutableStateOf(prefs.preferPlatformProviders)
  }

  var showSetPasswordDialog by remember { mutableStateOf(false) }
  var showPrivacyDialog by remember { mutableStateOf(false) }

  LaunchedEffect(prefs) {
    deviceIdText = prefs.deviceId
    serverUrlText = prefs.serverUrl
    accuracy = prefs.accuracy
    distanceText = prefs.distance.toString()
    intervalText = prefs.interval.toString()
    angleText = prefs.angle.toString()
    heartbeatText = prefs.heartbeat.toString()
    offlineBuffer = prefs.offlineBuffer
    wakeLock = prefs.wakeLock
    stopDetection = prefs.stopDetection
    preferPlatform = prefs.preferPlatformProviders
  }

  val parsedDistance = distanceText.toIntOrNull() ?: prefs.distance
  val intervalVisible = TrackingPreferences.isIntervalVisible(accuracy, parsedDistance)
  val angleVisible = TrackingPreferences.isAngleVisible(accuracy)
  val savedMsg = stringResource(R.string.settings_saved)

  fun commitSettings() {
    if (!TrackingPreferences.isValidServerUrl(serverUrlText)) {
      urlError = true
      return
    }
    urlError = false
    val dist = (distanceText.toIntOrNull() ?: prefs.distance).coerceAtLeast(0)
    val interval = (intervalText.toIntOrNull() ?: prefs.interval).coerceAtLeast(1)
    val angle = (angleText.toIntOrNull() ?: prefs.angle).coerceIn(0, 360)
    val rawHeartbeat = (heartbeatText.toIntOrNull() ?: prefs.heartbeat).coerceAtLeast(0)
    val normalizedHeartbeat = TrackingPreferences.normalizeHeartbeat(rawHeartbeat)
    heartbeatText = normalizedHeartbeat.toString()

    scope.launch {
      val ok =
        viewModel.saveSettings(
          deviceId = deviceIdText,
          serverUrl = serverUrlText,
          accuracy = accuracy,
          distance = dist,
          interval = interval,
          angle = angle,
          heartbeat = normalizedHeartbeat,
          offlineBuffer = offlineBuffer,
          wakeLock = wakeLock,
          stopDetection = stopDetection,
          preferPlatformProviders = preferPlatform,
        )
      if (ok) {
        snackbarHostState.showSnackbar(savedMsg)
      }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.close),
            )
          }
        },
        actions = {
          IconButton(
            onClick = onOpenQrScanner,
            modifier = Modifier.testTag("scan_qr_button"),
          ) {
            Icon(
              imageVector = Icons.Default.QrCodeScanner,
              contentDescription = stringResource(R.string.settings_scan_qr),
            )
          }
          IconButton(
            onClick = { commitSettings() },
            modifier = Modifier.testTag("save_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = stringResource(R.string.setting_save),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier =
          Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        OutlinedButton(
          onClick = onOpenQrScanner,
          modifier = Modifier.fillMaxWidth().testTag("scan_qr_card_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.settings_scan_qr))
        }

        // Core Settings Card
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedTextField(
              value = deviceIdText,
              onValueChange = { deviceIdText = it },
              label = { Text(stringResource(R.string.setting_device_id)) },
              supportingText = { Text(stringResource(R.string.setting_device_id_hint)) },
              textStyle = MonoCodeStyle,
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("device_id_input"),
            )

            OutlinedTextField(
              value = serverUrlText,
              onValueChange = {
                serverUrlText = it
                urlError = false
              },
              label = { Text(stringResource(R.string.setting_server_url)) },
              supportingText = {
                Text(
                  if (urlError) {
                    stringResource(R.string.setting_server_url_error)
                  } else {
                    stringResource(R.string.setting_server_url_hint)
                  }
                )
              },
              isError = urlError,
              textStyle = MonoCodeStyle,
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              modifier = Modifier.fillMaxWidth().testTag("server_url_input"),
            )

            Text(
              text = stringResource(R.string.setting_accuracy),
              style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              AccuracyChip(
                label = stringResource(R.string.accuracy_highest),
                selected = accuracy == TrackingAccuracy.HIGHEST,
                onClick = { accuracy = TrackingAccuracy.HIGHEST },
                tag = "accuracy_highest",
              )
              AccuracyChip(
                label = stringResource(R.string.accuracy_high),
                selected = accuracy == TrackingAccuracy.HIGH,
                onClick = { accuracy = TrackingAccuracy.HIGH },
                tag = "accuracy_high",
              )
              AccuracyChip(
                label = stringResource(R.string.accuracy_medium),
                selected = accuracy == TrackingAccuracy.MEDIUM,
                onClick = { accuracy = TrackingAccuracy.MEDIUM },
                tag = "accuracy_medium",
              )
              AccuracyChip(
                label = stringResource(R.string.accuracy_low),
                selected = accuracy == TrackingAccuracy.LOW,
                onClick = { accuracy = TrackingAccuracy.LOW },
                tag = "accuracy_low",
              )
            }

            OutlinedTextField(
              value = distanceText,
              onValueChange = { distanceText = it.filter { ch -> ch.isDigit() } },
              label = { Text(stringResource(R.string.setting_distance)) },
              supportingText = { Text(stringResource(R.string.setting_distance_hint)) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("distance_input"),
            )

            AnimatedVisibility(visible = intervalVisible) {
              OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.setting_interval)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("interval_input"),
              )
            }

            AnimatedVisibility(visible = angleVisible) {
              OutlinedTextField(
                value = angleText,
                onValueChange = { angleText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.setting_angle)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("angle_input"),
              )
            }

            OutlinedTextField(
              value = heartbeatText,
              onValueChange = { heartbeatText = it.filter { ch -> ch.isDigit() } },
              label = { Text(stringResource(R.string.setting_heartbeat)) },
              supportingText = { Text(stringResource(R.string.setting_heartbeat_hint)) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("heartbeat_input"),
            )
          }
        }

        // Advanced Settings Toggle & Section
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.settings_advanced_toggle),
                style = MaterialTheme.typography.titleMedium,
              )
              Switch(
                checked = showAdvanced,
                onCheckedChange = { showAdvanced = it },
                modifier = Modifier.testTag("advanced_settings_toggle"),
              )
            }

            AnimatedVisibility(visible = showAdvanced) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSwitchRow(
                  title = stringResource(R.string.setting_offline_buffer),
                  subtitle = stringResource(R.string.setting_offline_buffer_desc),
                  checked = offlineBuffer,
                  onCheckedChange = { offlineBuffer = it },
                  tag = "buffer_switch",
                )
                SettingSwitchRow(
                  title = stringResource(R.string.setting_wake_lock),
                  subtitle = stringResource(R.string.setting_wake_lock_desc),
                  checked = wakeLock,
                  onCheckedChange = { wakeLock = it },
                  tag = "wakelock_switch",
                )
                SettingSwitchRow(
                  title = stringResource(R.string.setting_stop_detection),
                  subtitle = stringResource(R.string.setting_stop_detection_desc),
                  checked = stopDetection,
                  onCheckedChange = { stopDetection = it },
                  tag = "stop_detection_switch",
                )
                SettingSwitchRow(
                  title = stringResource(R.string.setting_prefer_platform_providers),
                  subtitle = stringResource(R.string.setting_prefer_platform_providers_desc),
                  checked = preferPlatform,
                  onCheckedChange = { preferPlatform = it },
                  tag = "platform_providers_switch",
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = stringResource(R.string.setting_password),
                        style = MaterialTheme.typography.titleMedium,
                      )
                    }
                    Text(
                      text =
                        if (prefs.hasPassword) {
                          stringResource(R.string.setting_password_set)
                        } else {
                          stringResource(R.string.setting_password_unset)
                        },
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (prefs.hasPassword) {
                      TextButton(
                        onClick = { viewModel.clearPassword() },
                        modifier = Modifier.testTag("clear_password_button"),
                      ) {
                        Text(stringResource(R.string.setting_password_clear_btn))
                      }
                    }
                    OutlinedButton(
                      onClick = { showSetPasswordDialog = true },
                      modifier = Modifier.testTag("set_password_button"),
                    ) {
                      Text(stringResource(R.string.setting_password_set_btn))
                    }
                  }
                }
              }
            }
          }
        }

        Button(
          onClick = { commitSettings() },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_settings_bottom_button"),
        ) {
          Text(stringResource(R.string.setting_save))
        }

        // Theme & Language Card
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.settings_appearance_section),
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = stringResource(R.string.setting_theme),
              style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = prefs.themeMode == ThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                label = { Text(stringResource(R.string.theme_system)) },
                modifier = Modifier.testTag("theme_system_chip"),
              )
              FilterChip(
                selected = prefs.themeMode == ThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                label = { Text(stringResource(R.string.theme_light)) },
                modifier = Modifier.testTag("theme_light_chip"),
              )
              FilterChip(
                selected = prefs.themeMode == ThemeMode.DARK,
                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                label = { Text(stringResource(R.string.theme_dark)) },
                modifier = Modifier.testTag("theme_dark_chip"),
              )
            }

            Text(
              text = stringResource(R.string.setting_language),
              style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = prefs.language == AppLanguage.SYSTEM,
                onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) },
                label = { Text(stringResource(R.string.lang_system)) },
              )
              FilterChip(
                selected = prefs.language == AppLanguage.SPANISH,
                onClick = { viewModel.setLanguage(AppLanguage.SPANISH) },
                label = { Text(stringResource(R.string.lang_spanish)) },
              )
              FilterChip(
                selected = prefs.language == AppLanguage.ENGLISH,
                onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                label = { Text(stringResource(R.string.lang_english)) },
              )
            }
          }
        }

        // Tools & Compliance
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.settings_tools_section),
              style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
              onClick = onOpenBatteryGuide,
              modifier = Modifier.fillMaxWidth().testTag("settings_battery_guide_button"),
            ) {
              Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.setting_battery_guide))
            }
            OutlinedButton(
              onClick = onOpenPermissionsWizard,
              modifier = Modifier.fillMaxWidth().testTag("settings_permissions_button"),
            ) {
              Icon(Icons.Default.Security, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.setting_permissions_wizard))
            }
            OutlinedButton(
              onClick = { showPrivacyDialog = true },
              modifier = Modifier.fillMaxWidth().testTag("settings_privacy_button"),
            ) {
              Icon(Icons.Default.Policy, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.setting_privacy_policy))
            }
          }
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }

  if (showSetPasswordDialog) {
    SetPasswordDialog(
      onSave = { pwd ->
        viewModel.setPassword(pwd)
        showSetPasswordDialog = false
      },
      onDismiss = { showSetPasswordDialog = false },
    )
  }

  if (showPrivacyDialog) {
    PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
  }
}

@Composable
private fun AccuracyChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  tag: String,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    modifier = Modifier.testTag(tag),
  )
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  tag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(tag),
    )
  }
}
