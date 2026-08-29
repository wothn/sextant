package com.sextant.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sextant.app.data.ThemePreference

private data class SextantPalette(
  val accent: Color,
  val accentStrong: Color,
  val accentSoft: Color,
  val accentMuted: Color,
  val onAccent: Color,
  val secondary: Color,
  val secondaryStrong: Color,
  val secondarySoft: Color,
  val tertiary: Color,
  val tertiaryStrong: Color,
  val tertiarySoft: Color,
  val surface: Color,
  val surfaceAlt: Color,
  val surfaceStrong: Color,
  val background: Color,
  val backgroundSoft: Color,
  val text: Color,
  val textMuted: Color,
  val textSubtle: Color,
  val border: Color,
  val success: Color,
  val danger: Color,
  val warning: Color,
  val info: Color,
  val tabInactive: Color,
)

private val LightPalette = SextantPalette(
  accent = Color(0xFF3867D6),
  accentStrong = Color(0xFF2851B8),
  accentSoft = Color(0xFFE9EFFD),
  accentMuted = Color(0xFFC7D5F7),
  onAccent = Color.White,
  secondary = Color(0xFF526170),
  secondaryStrong = Color(0xFF354250),
  secondarySoft = Color(0xFFE9EDF1),
  tertiary = Color(0xFF8B6B43),
  tertiaryStrong = Color(0xFF654A2D),
  tertiarySoft = Color(0xFFF3ECE3),
  surface = Color.White,
  surfaceAlt = Color(0xFFF1F3F5),
  surfaceStrong = Color(0xFFE2E5E9),
  background = Color(0xFFF7F8FA),
  backgroundSoft = Color(0xFFEEF0F3),
  text = Color(0xFF17202D),
  textMuted = Color(0xFF4D5968),
  textSubtle = Color(0xFF75808E),
  border = Color(0xFFE2E5E9),
  success = Color(0xFF2F7D5B),
  danger = Color(0xFFB64B45),
  warning = Color(0xFF9A6B21),
  info = Color(0xFF3E708F),
  tabInactive = Color(0xFF8A95A3),
)

private val DarkPalette = SextantPalette(
  accent = Color(0xFF7E9FE9),
  accentStrong = Color(0xFFA8BFF2),
  accentSoft = Color(0xFF243555),
  accentMuted = Color(0xFF4266A8),
  onAccent = Color(0xFF111722),
  secondary = Color(0xFFA8B1BC),
  secondaryStrong = Color(0xFFD0D6DD),
  secondarySoft = Color(0xFF30363E),
  tertiary = Color(0xFFC1A27A),
  tertiaryStrong = Color(0xFFE1C7A5),
  tertiarySoft = Color(0xFF493C2E),
  surface = Color(0xFF171A20),
  surfaceAlt = Color(0xFF22262E),
  surfaceStrong = Color(0xFF343A44),
  background = Color(0xFF111319),
  backgroundSoft = Color(0xFF1A1E25),
  text = Color(0xFFF1F3F6),
  textMuted = Color(0xFFC0C7D0),
  textSubtle = Color(0xFF8E98A6),
  border = Color(0xFF303641),
  success = Color(0xFF70B493),
  danger = Color(0xFFD88079),
  warning = Color(0xFFD0A660),
  info = Color(0xFF78A8C3),
  tabInactive = Color(0xFF8E98A6),
)

object SextantColors {
  private val isDarkTheme = mutableStateOf(false)
  private val palette: SextantPalette
    get() = if (isDarkTheme.value) DarkPalette else LightPalette

  internal fun updateTheme(isDark: Boolean) {
    isDarkTheme.value = isDark
  }

  val Accent get() = palette.accent
  val AccentStrong get() = palette.accentStrong
  val AccentSoft get() = palette.accentSoft
  val AccentMuted get() = palette.accentMuted
  val OnAccent get() = palette.onAccent
  val Secondary get() = palette.secondary
  val SecondaryStrong get() = palette.secondaryStrong
  val SecondarySoft get() = palette.secondarySoft
  val Tertiary get() = palette.tertiary
  val TertiaryStrong get() = palette.tertiaryStrong
  val TertiarySoft get() = palette.tertiarySoft
  val Surface get() = palette.surface
  val SurfaceAlt get() = palette.surfaceAlt
  val SurfaceStrong get() = palette.surfaceStrong
  val Background get() = palette.background
  val BackgroundSoft get() = palette.backgroundSoft
  val Text get() = palette.text
  val TextMuted get() = palette.textMuted
  val TextSubtle get() = palette.textSubtle
  val Border get() = palette.border
  val Success get() = palette.success
  val Danger get() = palette.danger
  val Warning get() = palette.warning
  val Info get() = palette.info
  val TabInactive get() = palette.tabInactive
}

private val SextantLightScheme: ColorScheme = lightColorScheme(
  primary = LightPalette.accent,
  onPrimary = LightPalette.onAccent,
  primaryContainer = LightPalette.accentSoft,
  onPrimaryContainer = LightPalette.accentStrong,
  inversePrimary = LightPalette.accentMuted,
  secondary = LightPalette.secondary,
  onSecondary = Color.White,
  secondaryContainer = LightPalette.secondarySoft,
  onSecondaryContainer = LightPalette.secondaryStrong,
  tertiary = LightPalette.tertiary,
  onTertiary = Color.White,
  tertiaryContainer = LightPalette.tertiarySoft,
  onTertiaryContainer = LightPalette.tertiaryStrong,
  error = LightPalette.danger,
  onError = Color.White,
  errorContainer = Color(0xFFFEE2E2),
  onErrorContainer = Color(0xFF991B1B),
  background = LightPalette.background,
  onBackground = LightPalette.text,
  surface = LightPalette.surface,
  onSurface = LightPalette.text,
  surfaceVariant = LightPalette.surfaceAlt,
  onSurfaceVariant = LightPalette.textMuted,
  surfaceTint = LightPalette.accent,
  inverseSurface = LightPalette.text,
  inverseOnSurface = LightPalette.surface,
  outline = LightPalette.border,
  outlineVariant = LightPalette.surfaceStrong,
  scrim = Color(0x9917202D),
  surfaceBright = LightPalette.surface,
  surfaceContainer = LightPalette.surfaceAlt,
  surfaceContainerHigh = LightPalette.backgroundSoft,
  surfaceContainerHighest = LightPalette.surfaceStrong,
  surfaceContainerLow = LightPalette.background,
  surfaceContainerLowest = LightPalette.surface,
  surfaceDim = LightPalette.surfaceStrong,
)

private val SextantDarkScheme: ColorScheme = darkColorScheme(
  primary = DarkPalette.accent,
  onPrimary = DarkPalette.onAccent,
  primaryContainer = DarkPalette.accentSoft,
  onPrimaryContainer = DarkPalette.accentStrong,
  inversePrimary = DarkPalette.accentMuted,
  secondary = DarkPalette.secondary,
  onSecondary = Color(0xFF003733),
  secondaryContainer = DarkPalette.secondarySoft,
  onSecondaryContainer = DarkPalette.secondaryStrong,
  tertiary = DarkPalette.tertiary,
  onTertiary = Color(0xFF422006),
  tertiaryContainer = DarkPalette.tertiarySoft,
  onTertiaryContainer = DarkPalette.tertiaryStrong,
  error = DarkPalette.danger,
  onError = Color(0xFF450A0A),
  errorContainer = Color(0xFF7F1D1D),
  onErrorContainer = Color(0xFFFECACA),
  background = DarkPalette.background,
  onBackground = DarkPalette.text,
  surface = DarkPalette.surface,
  onSurface = DarkPalette.text,
  surfaceVariant = DarkPalette.surfaceAlt,
  onSurfaceVariant = DarkPalette.textMuted,
  surfaceTint = DarkPalette.accent,
  inverseSurface = DarkPalette.text,
  inverseOnSurface = DarkPalette.surface,
  outline = DarkPalette.border,
  outlineVariant = DarkPalette.surfaceStrong,
  scrim = Color(0x99000000),
  surfaceBright = DarkPalette.surfaceAlt,
  surfaceContainer = DarkPalette.surfaceAlt,
  surfaceContainerHigh = DarkPalette.surfaceStrong,
  surfaceContainerHighest = DarkPalette.surfaceStrong,
  surfaceContainerLow = DarkPalette.surface,
  surfaceContainerLowest = DarkPalette.background,
  surfaceDim = DarkPalette.background,
)

@Composable
fun SextantTheme(
  themePreference: ThemePreference = ThemePreference.System,
  content: @Composable () -> Unit,
) {
  val isDarkTheme = when (themePreference) {
    ThemePreference.System -> isSystemInDarkTheme()
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
  }
  val colorScheme = if (isDarkTheme) SextantDarkScheme else SextantLightScheme
  val view = LocalView.current

  SideEffect {
    SextantColors.updateTheme(isDarkTheme)
    val activity = view.context as? Activity
    if (activity != null) {
      activity.window.statusBarColor = colorScheme.background.toArgb()
      activity.window.navigationBarColor = colorScheme.surface.toArgb()
      WindowCompat.getInsetsController(activity.window, view).apply {
        isAppearanceLightStatusBars = !isDarkTheme
        isAppearanceLightNavigationBars = !isDarkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography(),
    shapes = Shapes(),
    content = content,
  )
}

fun argbColor(value: Long): Color {
  return Color(value.toInt())
}
