package com.sextant.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SextantColors {
  val Accent = Color(0xFF3B82F6)
  val AccentStrong = Color(0xFF1D4ED8)
  val AccentSoft = Color(0xFFDBEAFE)
  val AccentMuted = Color(0xFFBFDBFE)
  val OnAccent = Color.White
  val Secondary = Color(0xFF0F766E)
  val SecondaryStrong = Color(0xFF115E59)
  val SecondarySoft = Color(0xFFCCFBF1)
  val Tertiary = Color(0xFFD97706)
  val TertiaryStrong = Color(0xFF92400E)
  val TertiarySoft = Color(0xFFFEF3C7)
  val Surface = Color.White
  val SurfaceAlt = Color(0xFFF1F5F9)
  val SurfaceStrong = Color(0xFFE2E8F0)
  val Background = Color(0xFFF8FAFC)
  val BackgroundSoft = Color(0xFFEEF2F7)
  val Text = Color(0xFF0F172A)
  val TextMuted = Color(0xFF475569)
  val TextSubtle = Color(0xFF64748B)
  val Border = Color(0xFFE2E8F0)
  val Success = Color(0xFF22C55E)
  val Danger = Color(0xFFEF4444)
  val Warning = Color(0xFFF59E0B)
  val Info = Color(0xFF0EA5E9)
  val TabInactive = Color(0xFF94A3B8)
}

private val SextantLightScheme: ColorScheme = lightColorScheme(
  primary = SextantColors.Accent,
  onPrimary = SextantColors.OnAccent,
  primaryContainer = SextantColors.AccentSoft,
  onPrimaryContainer = SextantColors.AccentStrong,
  inversePrimary = SextantColors.AccentMuted,
  secondary = SextantColors.Secondary,
  onSecondary = Color.White,
  secondaryContainer = SextantColors.SecondarySoft,
  onSecondaryContainer = SextantColors.SecondaryStrong,
  tertiary = SextantColors.Tertiary,
  onTertiary = Color.White,
  tertiaryContainer = SextantColors.TertiarySoft,
  onTertiaryContainer = SextantColors.TertiaryStrong,
  error = SextantColors.Danger,
  onError = Color.White,
  errorContainer = Color(0xFFFEE2E2),
  onErrorContainer = Color(0xFF991B1B),
  background = SextantColors.Background,
  onBackground = SextantColors.Text,
  surface = SextantColors.Surface,
  onSurface = SextantColors.Text,
  surfaceVariant = SextantColors.SurfaceAlt,
  onSurfaceVariant = SextantColors.TextMuted,
  surfaceTint = SextantColors.Accent,
  inverseSurface = SextantColors.Text,
  inverseOnSurface = SextantColors.Surface,
  outline = SextantColors.Border,
  outlineVariant = SextantColors.SurfaceStrong,
  scrim = Color(0x990F172A),
  surfaceBright = SextantColors.Surface,
  surfaceContainer = SextantColors.SurfaceAlt,
  surfaceContainerHigh = SextantColors.BackgroundSoft,
  surfaceContainerHighest = SextantColors.SurfaceStrong,
  surfaceContainerLow = SextantColors.Background,
  surfaceContainerLowest = SextantColors.Surface,
  surfaceDim = SextantColors.SurfaceStrong,
)

@Composable
fun SextantTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = SextantLightScheme,
    typography = Typography(),
    shapes = Shapes(),
    content = content,
  )
}

fun argbColor(value: Long): Color {
  return Color(value.toInt())
}
