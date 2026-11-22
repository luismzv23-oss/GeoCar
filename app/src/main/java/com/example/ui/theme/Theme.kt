package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.model.ThemeMode

private val WayPulseDarkColorScheme =
  darkColorScheme(
    primary = ElectricTeal,
    onPrimary = MidnightNavy,
    primaryContainer = SoftTealContainer,
    onPrimaryContainer = ElectricTeal,
    secondary = SignalAmber,
    onSecondary = MidnightNavy,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = SignalAmber,
    error = EmergencyCrimson,
    onError = Color.White,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = EmergencyCrimson,
    background = MidnightNavy,
    onBackground = Color(0xFFF1F5F9),
    surface = SlateSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = LightMutedText,
    outline = OutlineDark,
  )

private val WayPulseLightColorScheme =
  lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = LightTealContainer,
    onPrimaryContainer = Color(0xFF003830),
    secondary = DeepAmber,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = Color(0xFF3E2900),
    error = DeepCrimson,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = DeepCrimson,
    background = PorcelainBg,
    onBackground = DarkText,
    surface = CrispWhite,
    onSurface = DarkText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = OutlineLight,
  )

@Composable
fun WayPulseTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val useDark =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
    }

  MaterialTheme(
    colorScheme = if (useDark) WayPulseDarkColorScheme else WayPulseLightColorScheme,
    typography = AppTypography,
    content = content,
  )
}
