package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.MotionState
import com.example.data.model.TrackingPreferences
import com.example.data.model.TrackingRuntimeState
import com.example.ui.components.LocationDisclosureDialog
import com.example.ui.components.PasswordPromptDialog
import com.example.ui.components.RateAppCard
import com.example.ui.components.TelemetryPulseOrb
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProtectedTarget {
  TOGGLE_TRACKING_ON,
  TOGGLE_TRACKING_OFF,
  OPEN_SETTINGS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  viewModel: MainViewModel,
  onOpenSettings: () -> Unit,
  onOpenStatus: () -> Unit,
  onOpenPermissions: () -> Unit,
  onOpenBatteryGuide: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()
  val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
  val queuedCount by viewModel.queuedCount.collectAsStateWithLifecycle()

  var pendingProtectedTarget by remember { mutableStateOf<ProtectedTarget?>(null) }
  var passwordError by remember { mutableStateOf<String?>(null) }
  var showDisclosureDialog by remember { mutableStateOf(false) }
  var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

  val hasLocationPermission = remember(prefs, runtimeState) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  }

  val hasBackgroundPermission = remember(prefs, runtimeState) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  val isIgnoringBattery = remember(prefs, runtimeState) {
    val pm = context.getSystemService(PowerManager::class.java)
    pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
      val granted =
        results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      if (granted) {
        pendingActionAfterPermission?.invoke()
      }
      pendingActionAfterPermission = null
    }

  fun promptBatteryOptimizationOnce() {
    if (!prefs.batteryOptimizationPrompted) {
      viewModel.markBatteryPrompted()
      val pm = context.getSystemService(PowerManager::class.java)
      if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
        runCatching {
          val intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
              data = Uri.parse("package:${context.packageName}")
            }
          context.startActivity(intent)
        }
      }
    }
  }

  fun startWithPermissionCheck(action: () -> Unit) {
    val fineGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    if (fineGranted) {
      action()
      return
    }

    if (!prefs.locationDisclosureAccepted) {
      pendingActionAfterPermission = action
      showDisclosureDialog = true
    } else {
      pendingActionAfterPermission = action
      val toRequest = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
      }
      permissionLauncher.launch(toRequest.toTypedArray())
    }
  }

  fun executeProtected(target: ProtectedTarget) {
    if (prefs.hasPassword) {
      passwordError = null
      pendingProtectedTarget = target
    } else {
      when (target) {
        ProtectedTarget.TOGGLE_TRACKING_ON -> {
          startWithPermissionCheck {
            viewModel.setTrackingEnabled(true)
            promptBatteryOptimizationOnce()
          }
        }
        ProtectedTarget.TOGGLE_TRACKING_OFF -> viewModel.setTrackingEnabled(false)
        ProtectedTarget.OPEN_SETTINGS -> onOpenSettings()
      }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.headlineMedium,
            )
            Surface(
              shape = RoundedCornerShape(50),
              color =
                if (prefs.isTracking) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
              Text(
                text = if (prefs.isTracking) "ON" else "OFF",
                style = MaterialTheme.typography.labelMedium,
                color =
                  if (prefs.isTracking) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
  ) { innerPadding ->
    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.TopCenter,
    ) {
      val isWide = maxWidth >= 680.dp

      Column(
        modifier =
          Modifier
            .widthIn(max = if (isWide) 960.dp else 600.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (isWide) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              TrackingHeroCard(
                prefs = prefs,
                runtimeState = runtimeState,
                queuedCount = queuedCount,
                onToggleTracking = { checked ->
                  executeProtected(
                    if (checked) {
                      ProtectedTarget.TOGGLE_TRACKING_ON
                    } else {
                      ProtectedTarget.TOGGLE_TRACKING_OFF
                    }
                  )
                },
              )
              QuickActionsSection(
                onSendLocation = { startWithPermissionCheck { viewModel.sendSingleLocation() } },
                onSendSos = { startWithPermissionCheck { viewModel.sendSos() } },
                onOpenStatus = onOpenStatus,
              )
            }
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              DeviceConfigCard(
                prefs = prefs,
                queuedCount = queuedCount,
                onOpenSettings = { executeProtected(ProtectedTarget.OPEN_SETTINGS) },
              )
              if (!hasLocationPermission || !hasBackgroundPermission || !isIgnoringBattery) {
                PermissionsAndBatteryBanner(
                  onOpenPermissions = onOpenPermissions,
                  onOpenBatteryGuide = onOpenBatteryGuide,
                )
              }
              LocationDisclosureBanner()
            }
          }
        } else {
          TrackingHeroCard(
            prefs = prefs,
            runtimeState = runtimeState,
            queuedCount = queuedCount,
            onToggleTracking = { checked ->
              executeProtected(
                if (checked) {
                  ProtectedTarget.TOGGLE_TRACKING_ON
                } else {
                  ProtectedTarget.TOGGLE_TRACKING_OFF
                }
              )
            },
          )
          QuickActionsSection(
            onSendLocation = { startWithPermissionCheck { viewModel.sendSingleLocation() } },
            onSendSos = { startWithPermissionCheck { viewModel.sendSos() } },
            onOpenStatus = onOpenStatus,
          )
          DeviceConfigCard(
            prefs = prefs,
            queuedCount = queuedCount,
            onOpenSettings = { executeProtected(ProtectedTarget.OPEN_SETTINGS) },
          )
          if (!hasLocationPermission || !hasBackgroundPermission || !isIgnoringBattery) {
            PermissionsAndBatteryBanner(
              onOpenPermissions = onOpenPermissions,
              onOpenBatteryGuide = onOpenBatteryGuide,
            )
          }
          LocationDisclosureBanner()
        }

        val showRatePrompt =
          !prefs.ratePromptDismissed &&
            prefs.firstLaunchTimestamp > 0L &&
            (System.currentTimeMillis() - prefs.firstLaunchTimestamp) >= 3 * 24 * 60 * 60 * 1000L
        if (showRatePrompt) {
          RateAppCard(
            onRateNow = {
              viewModel.dismissRatePrompt()
              runCatching {
                val intent =
                  Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${context.packageName}"),
                  )
                context.startActivity(intent)
              }
            },
            onDismiss = { viewModel.dismissRatePrompt() },
          )
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }

  val activeTarget = pendingProtectedTarget
  if (activeTarget != null) {
    val wrongPwd = stringResource(R.string.password_prompt_error)
    PasswordPromptDialog(
      errorText = passwordError,
      onVerify = { input ->
        scope.launch {
          if (viewModel.verifyPassword(input)) {
            pendingProtectedTarget = null
            passwordError = null
            when (activeTarget) {
              ProtectedTarget.TOGGLE_TRACKING_ON -> {
                startWithPermissionCheck {
                  viewModel.setTrackingEnabled(true)
                  promptBatteryOptimizationOnce()
                }
              }
              ProtectedTarget.TOGGLE_TRACKING_OFF -> viewModel.setTrackingEnabled(false)
              ProtectedTarget.OPEN_SETTINGS -> onOpenSettings()
            }
          } else {
            passwordError = wrongPwd
          }
        }
      },
      onDismiss = {
        pendingProtectedTarget = null
        passwordError = null
      },
    )
  }

  if (showDisclosureDialog) {
    LocationDisclosureDialog(
      onAccept = {
        showDisclosureDialog = false
        viewModel.acceptLocationDisclosure()
        val toRequest = buildList {
          add(Manifest.permission.ACCESS_FINE_LOCATION)
          add(Manifest.permission.ACCESS_COARSE_LOCATION)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
          }
        }
        permissionLauncher.launch(toRequest.toTypedArray())
      },
      onDismiss = {
        showDisclosureDialog = false
        pendingActionAfterPermission = null
      },
    )
  }
}

@Composable
private fun TrackingHeroCard(
  prefs: TrackingPreferences,
  runtimeState: TrackingRuntimeState,
  queuedCount: Int,
  onToggleTracking: (Boolean) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_telemetry_banner_1795311528039),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(24.dp))
            .alpha(if (prefs.isTracking) 0.24f else 0.10f),
      )

      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
          ) {
            TelemetryPulseOrb(
              isTracking = prefs.isTracking,
              motionState = runtimeState.motionState,
            )
            Column {
              Text(
                text = stringResource(R.string.main_tracking_label),
                style = MaterialTheme.typography.titleLarge,
              )
              val subtitle =
                when {
                  !prefs.isTracking -> stringResource(R.string.main_tracking_inactive)
                  runtimeState.motionState == MotionState.STATIONARY ->
                    stringResource(R.string.main_tracking_stationary)
                  else -> stringResource(R.string.main_tracking_active)
                }
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Switch(
            checked = prefs.isTracking,
            onCheckedChange = onToggleTracking,
            modifier = Modifier.testTag("tracking_switch"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TelemetryBadge(text = stringResource(R.string.main_queued_count, queuedCount))
          if (runtimeState.activeProvider.isNotBlank()) {
            TelemetryBadge(text = runtimeState.activeProvider)
          }
          runtimeState.lastSentTimeMs?.let { ts ->
            val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
            TelemetryBadge(text = fmt.format(Date(ts)))
          }
        }

        runtimeState.lastError?.let { err ->
          Text(
            text = err,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun TelemetryBadge(text: String) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
  ) {
    Text(
      text = text,
      style = MonoCodeStyle,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    )
  }
}

@Composable
private fun QuickActionsSection(
  onSendLocation: () -> Unit,
  onSendSos: () -> Unit,
  onOpenStatus: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = onSendLocation,
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier
            .weight(1f)
            .height(56.dp)
            .testTag("send_location_button"),
      ) {
        Icon(
          imageVector = Icons.Default.MyLocation,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.main_send_location),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Button(
        onClick = onSendSos,
        shape = RoundedCornerShape(16.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        modifier =
          Modifier
            .weight(0.65f)
            .height(56.dp)
            .testTag("sos_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = stringResource(R.string.main_sos))
      }
    }

    FilledTonalButton(
      onClick = onOpenStatus,
      shape = RoundedCornerShape(16.dp),
      modifier =
        Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("status_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ListAlt,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text(text = stringResource(R.string.main_status))
    }
  }
}

@Composable
private fun DeviceConfigCard(
  prefs: TrackingPreferences,
  queuedCount: Int,
  onOpenSettings: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.main_device_id),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = prefs.deviceId,
            style = MonoCodeStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
          )
        }
        OutlinedButton(
          onClick = onOpenSettings,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("settings_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.main_settings))
        }
      }

      Column {
        Text(
          text = stringResource(R.string.main_server_url),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = prefs.serverUrl,
          style = MonoCodeStyle,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Text(
        text =
          "${prefs.accuracy.key.uppercase()} • ${prefs.distance}m • " +
            stringResource(R.string.main_queued_count, queuedCount),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun PermissionsAndBatteryBanner(
  onOpenPermissions: () -> Unit,
  onOpenBatteryGuide: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
          text = stringResource(R.string.main_permissions_needed),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
      Text(
        text = stringResource(R.string.main_permissions_needed_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = onOpenPermissions,
          modifier = Modifier.testTag("open_permissions_wizard_button"),
        ) {
          Text(stringResource(R.string.setting_permissions_wizard))
        }
        OutlinedButton(
          onClick = onOpenBatteryGuide,
          modifier = Modifier.testTag("open_battery_guide_button"),
        ) {
          Icon(
            imageVector = Icons.Default.BatteryChargingFull,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.battery_guide_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun LocationDisclosureBanner() {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
      ),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = Icons.Default.LocationOn,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.main_location_disclosure_title),
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          text = stringResource(R.string.main_location_disclosure_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
