package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryGuideScreen(
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val manufacturer = Build.MANUFACTURER.orEmpty()
  val model = Build.MODEL.orEmpty()
  val steps = manufacturerSteps(manufacturer)

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.battery_guide_title)) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("battery_guide_back")) {
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.battery_guide_device, manufacturer, model),
              style = MaterialTheme.typography.titleMedium,
            )
            steps.forEachIndexed { index, step ->
              Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyLarge,
              )
            }
          }
        }

        Button(
          onClick = {
            runCatching {
              val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
              context.startActivity(intent)
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("request_battery_exclusion_btn"),
        ) {
          Text(stringResource(R.string.battery_guide_request_exclusion))
        }

        OutlinedButton(
          onClick = {
            runCatching {
              val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
              context.startActivity(intent)
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("open_app_battery_settings_btn"),
        ) {
          Text(stringResource(R.string.battery_guide_open_app_info))
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

private fun manufacturerSteps(manufacturer: String): List<String> {
  val m = manufacturer.lowercase()
  return when {
    m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
      listOf(
        "Open Settings > Apps > Manage apps > WayPulse.",
        "Enable Autostart.",
        "Battery saver > Select 'No restrictions'.",
        "Lock WayPulse in Recents so MIUI/HyperOS does not kill background tasks.",
      )
    m.contains("samsung") ->
      listOf(
        "Open Settings > Apps > WayPulse > Battery.",
        "Select 'Unrestricted'.",
        "In Device Care > Battery > Background usage limits, ensure WayPulse is in 'Never sleeping apps'.",
      )
    m.contains("huawei") || m.contains("honor") ->
      listOf(
        "Open Settings > Battery > App launch.",
        "Find WayPulse and toggle 'Manage automatically' OFF.",
        "Enable Auto-launch, Secondary launch, and Run in background.",
      )
    m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
      listOf(
        "Open Settings > Battery > More battery settings > Optimize battery use > WayPulse > Don't optimize.",
        "Allow Background activity and Auto-launch in App Management.",
      )
    m.contains("vivo") ->
      listOf(
        "Open Settings > Battery > Background power consumption management > WayPulse > High background power usage.",
      )
    else ->
      listOf(
        "Open App Info > Battery > Select 'Unrestricted' or 'Don't optimize'.",
        "Ensure Adaptive Battery does not restrict WayPulse while tracking is enabled.",
      )
  }
}
