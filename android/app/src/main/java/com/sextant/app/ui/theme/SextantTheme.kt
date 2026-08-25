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
  accent = Color(0xFF3B82F6),
  accentStrong = Color(0xFF1D4ED8),
  accentSoft = Color(0xFFDBEAFE),
  accentMuted = Color(0xFFBFDBFE),
  onAccent = Color.White,
  secondary = Color(0xFF0F766E),
  secondaryStrong = Color(0xFF115E59),
  secondarySoft = Color(0xFFCCFBF1),
  tertiary = Color(0xFFD97706),
  tertiaryStrong = Color(0xFF92400E),
  tertiarySoft = Color(0xFFFEF3C7),
  surface = Color.White,
  surfaceAlt = Color(0xFFF1F5F9),
  surfaceStrong = Color(0xFFE2E8F0),
  background = Color(0xFFF8FAFC),
  backgroundSoft = Color(0xFFEEF2F7),
  text = Color(0xFF0F172A),
  textMuted = Color(0xFF475569),
  textSubtle = Color(0xFF64748B),
  border = Color(0xFFE2E8F0),
  success = Color(0xFF22C55E),
  danger = Color(0xFFEF4444),
  warning = Color(0xFFF59E0B),
  info = Color(0xFF0EA5E9),
  tabInactive = Color(0xFF94A3B8),
)

private val DarkPalette = SextantPalette(
  accent = Color(0xFF60A5FA),
  accentStrong = Color(0xFF93C5FD),
  accentSoft = Color(0xFF1E3A5F),
  accentMuted = Color(0xFF3B82F6),
  onAccent = Color(0xFF0F172A),
  secondary = Color(0xFF5EEAD4),
  secondaryStrong = Color(0xFF99F6E4),
  secondarySoft = Color(0xFF134E4A),
  tertiary = Color(0xFFFBBF24),
  tertiaryStrong = Color(0xFFFDE68A),
  tertiarySoft = Color(0xFF78350F),
  surface = Color(0xFF111827),
  surfaceAlt = Color(0xFF1F2937),
  surfaceStrong = Color(0xFF374151),
  background = Color(0xFF0B1120),
  backgroundSoft = Color(0xFF111827),
  text = Color(0xFFF8FAFC),
  textMuted = Color(0xFFCBD5E1),
  textSubtle = Color(0xFF94A3B8),
  border = Color(0xFF334155),
  success = Color(0xFF4ADE80),
  danger = Color(0xFFF87171),
  warning = Color(0xFFFBBF24),
  info = Color(0xFF38BDF8),
  tabInactive = Color(0xFF94A3B8),
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
  scrim = Color(0x990F172A),
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
