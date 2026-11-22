package com.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppLanguage
import com.example.remote.RemoteConfigAction
import com.example.remote.RemoteConfigParser
import com.example.ui.components.PasswordPromptDialog
import com.example.ui.components.RemoteConfigConfirmDialog
import com.example.ui.screens.BatteryGuideScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.PermissionsWizardScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.theme.WayPulseTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.StatusViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private object Routes {
  const val MAIN = "main"
  const val SETTINGS = "settings"
  const val STATUS = "status"
  const val QR_SCANNER = "qr_scanner"
  const val BATTERY_GUIDE = "battery_guide"
  const val PERMISSIONS = "permissions"
}

class MainActivity : ComponentActivity() {

  private var pendingDeepLinkAction by mutableStateOf<RemoteConfigAction?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleDeepLinkIntent(intent)

    val app = application as WayPulseApplication
    val container = app.container

    setContent {
      val mainViewModel: MainViewModel =
        viewModel(
          factory =
            MainViewModel.Factory(
              container.preferencesRepository,
              container.trackingController,
              container.positionDispatcher,
              container.statusLogRepository,
            )
        )
      val settingsViewModel: SettingsViewModel =
        viewModel(
          factory =
            SettingsViewModel.Factory(
              container.preferencesRepository,
              container.statusLogRepository,
            )
        )
      val statusViewModel: StatusViewModel =
        viewModel(factory = StatusViewModel.Factory(container.statusLogRepository))

      val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()
      val baseContext = LocalContext.current
      val localizedContext = remember(baseContext, prefs.language) {
        applyLanguage(baseContext, prefs.language)
      }

      CompositionLocalProvider(LocalContext provides localizedContext) {
        WayPulseTheme(themeMode = prefs.themeMode) {
          WayPulseAppHost(
            mainViewModel = mainViewModel,
            settingsViewModel = settingsViewModel,
            statusViewModel = statusViewModel,
            pendingDeepLinkAction = pendingDeepLinkAction,
            onConsumeDeepLink = { pendingDeepLinkAction = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLinkIntent(intent)
  }

  private fun handleDeepLinkIntent(intent: Intent?) {
    val dataString = intent?.dataString ?: return
    val parsed = RemoteConfigParser.parseUriString(dataString)
    if (parsed !is RemoteConfigAction.Invalid) {
      pendingDeepLinkAction = parsed
    }
  }

  private fun applyLanguage(context: Context, language: AppLanguage): Context {
    val tag = language.languageTag ?: return context
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    return context.createConfigurationContext(config)
  }
}

@Composable
private fun WayPulseAppHost(
  mainViewModel: MainViewModel,
  settingsViewModel: SettingsViewModel,
  statusViewModel: StatusViewModel,
  pendingDeepLinkAction: RemoteConfigAction?,
  onConsumeDeepLink: () -> Unit,
) {
  val navController = rememberNavController()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()

  var pendingRemoteValues by remember { mutableStateOf<Map<String, String>?>(null) }
  var pendingLinkTrackingToggle by remember { mutableStateOf<Boolean?>(null) }
  var linkPasswordError by remember { mutableStateOf<String?>(null) }

  when (pendingDeepLinkAction) {
    RemoteConfigAction.StartTracking -> {
      onConsumeDeepLink()
      if (prefs.hasPassword) {
        linkPasswordError = null
        pendingLinkTrackingToggle = true
      } else {
        mainViewModel.setTrackingEnabled(true)
      }
    }
    RemoteConfigAction.StopTracking -> {
      onConsumeDeepLink()
      if (prefs.hasPassword) {
        linkPasswordError = null
        pendingLinkTrackingToggle = false
      } else {
        mainViewModel.setTrackingEnabled(false)
      }
    }
    is RemoteConfigAction.ApplySettings -> {
      pendingRemoteValues = pendingDeepLinkAction.values
      onConsumeDeepLink()
    }
    RemoteConfigAction.Invalid,
    null -> {}
  }

  NavHost(navController = navController, startDestination = Routes.MAIN) {
    composable(Routes.MAIN) {
      MainScreen(
        viewModel = mainViewModel,
        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
        onOpenStatus = { navController.navigate(Routes.STATUS) },
        onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
        onOpenBatteryGuide = { navController.navigate(Routes.BATTERY_GUIDE) },
      )
    }

    composable(Routes.SETTINGS) {
      SettingsScreen(
        viewModel = settingsViewModel,
        onBack = { navController.popBackStack() },
        onOpenQrScanner = { navController.navigate(Routes.QR_SCANNER) },
        onOpenBatteryGuide = { navController.navigate(Routes.BATTERY_GUIDE) },
        onOpenPermissionsWizard = { navController.navigate(Routes.PERMISSIONS) },
      )
    }

    composable(Routes.STATUS) {
      StatusScreen(
        viewModel = statusViewModel,
        onBack = { navController.popBackStack() },
      )
    }

    composable(Routes.QR_SCANNER) {
      val invalidMsg = stringResource(R.string.qr_invalid)
      QrScannerScreen(
        onQrScanned = { raw ->
          when (val parsed = RemoteConfigParser.parseUriString(raw)) {
            is RemoteConfigAction.ApplySettings -> {
              pendingRemoteValues = parsed.values
              navController.popBackStack()
            }
            RemoteConfigAction.StartTracking -> {
              mainViewModel.setTrackingEnabled(true)
              navController.popBackStack()
            }
            RemoteConfigAction.StopTracking -> {
              mainViewModel.setTrackingEnabled(false)
              navController.popBackStack()
            }
            RemoteConfigAction.Invalid -> {
              Toast.makeText(context, invalidMsg, Toast.LENGTH_SHORT).show()
              navController.popBackStack()
            }
          }
        },
        onBack = { navController.popBackStack() },
      )
    }

    composable(Routes.BATTERY_GUIDE) {
      BatteryGuideScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.PERMISSIONS) {
      PermissionsWizardScreen(
        onDisclosureAccepted = { mainViewModel.acceptLocationDisclosure() },
        onBack = { navController.popBackStack() },
      )
    }
  }

  pendingRemoteValues?.let { values ->
    RemoteConfigConfirmDialog(
      values = values,
      onConfirm = {
        mainViewModel.applyRemoteConfig(values)
        pendingRemoteValues = null
      },
      onDismiss = { pendingRemoteValues = null },
    )
  }

  pendingLinkTrackingToggle?.let { enable ->
    val wrongPwd = stringResource(R.string.password_prompt_error)
    PasswordPromptDialog(
      errorText = linkPasswordError,
      onVerify = { candidate ->
        scope.launch {
          if (mainViewModel.verifyPassword(candidate)) {
            pendingLinkTrackingToggle = null
            linkPasswordError = null
            mainViewModel.setTrackingEnabled(enable)
          } else {
            linkPasswordError = wrongPwd
          }
        }
      },
      onDismiss = {
        pendingLinkTrackingToggle = null
        linkPasswordError = null
      },
    )
  }
}
