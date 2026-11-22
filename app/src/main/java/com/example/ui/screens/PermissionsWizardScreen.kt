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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.components.LocationDisclosureDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsWizardScreen(
  onDisclosureAccepted: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  var refreshTick by remember { mutableIntStateOf(0) }
  var showBgDisclosure by remember { mutableStateOf(false) }

  val permLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      refreshTick++
    }

  val bgPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      refreshTick++
    }

  val fineGranted = remember(refreshTick) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  }
  val bgGranted = remember(refreshTick) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
  val activityGranted = remember(refreshTick) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
  val notifGranted = remember(refreshTick) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
  val batteryExcluded = remember(refreshTick) {
    val pm = context.getSystemService(PowerManager::class.java)
    pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.permissions_title)) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("permissions_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.close),
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
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PermissionStepCard(
          title = stringResource(R.string.perm_fine_location),
          granted = fineGranted,
          onGrant = {
            permLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
              )
            )
          },
          tag = "grant_fine_location_btn",
        )

        PermissionStepCard(
          title = stringResource(R.string.perm_background_location),
          granted = bgGranted,
          onGrant = { showBgDisclosure = true },
          tag = "grant_bg_location_btn",
        )

        PermissionStepCard(
          title = stringResource(R.string.perm_activity_recognition),
          granted = activityGranted,
          onGrant = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              permLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            }
          },
          tag = "grant_activity_btn",
        )

        PermissionStepCard(
          title = stringResource(R.string.perm_notifications),
          granted = notifGranted,
          onGrant = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
          },
          tag = "grant_notifications_btn",
        )

        PermissionStepCard(
          title = stringResource(R.string.perm_battery_exclusion),
          granted = batteryExcluded,
          onGrant = {
            runCatching {
              val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
              context.startActivity(intent)
            }
            refreshTick++
          },
          tag = "grant_battery_exclusion_btn",
        )
      }
    }
  }

  if (showBgDisclosure) {
    LocationDisclosureDialog(
      onAccept = {
        showBgDisclosure = false
        onDisclosureAccepted()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          runCatching {
            val intent =
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
              }
            context.startActivity(intent)
          }
          refreshTick++
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
      },
      onDismiss = { showBgDisclosure = false },
    )
  }
}

@Composable
private fun PermissionStepCard(
  title: String,
  granted: Boolean,
  onGrant: () -> Unit,
  tag: String,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f).padding(end = 12.dp),
      )
      if (granted) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = stringResource(R.string.perm_granted),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      } else {
        Button(onClick = onGrant, modifier = Modifier.testTag(tag)) {
          Text(stringResource(R.string.perm_grant))
        }
      }
    }
  }
}
