@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sextant.app.ui.theme

import android.app.Activity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sextant.app.data.ThemePreference

/**
 * 一款主题的全部色值。与 Material 3 的 `ColorScheme` 并存：`ColorScheme` 喂给底层组件，
 * 这个 palette 供 `SextantColors` 按语义读取，页面不直接碰色值。
 */
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
  accent = Color(0xFFA6532D),
  accentStrong = Color(0xFF874023),
  accentSoft = Color(0xFFF3E7DF),
  accentMuted = Color(0xFFD9B49F),
  onAccent = Color.White,
  secondary = Color(0xFF666666),
  secondaryStrong = Color(0xFF424242),
  secondarySoft = Color(0xFFEEEEEE),
  tertiary = Color(0xFF8B6B43),
  tertiaryStrong = Color(0xFF654A2D),
  tertiarySoft = Color(0xFFF3ECE3),
  surface = Color(0xFFF7F7F7),
  surfaceAlt = Color(0xFFF0F0F0),
  surfaceStrong = Color(0xFFDADADA),
  background = Color.White,
  backgroundSoft = Color(0xFFE8E8E8),
  text = Color(0xFF262626),
  textMuted = Color(0xFF595959),
  textSubtle = Color(0xFF616161),
  border = Color(0xFFDADADA),
  success = Color(0xFF2F7D5B),
  danger = Color(0xFFB64B45),
  warning = Color(0xFF865B18),
  info = Color(0xFF3E708F),
  tabInactive = Color(0xFF757575),
)

private val DarkPalette = SextantPalette(
  accent = Color(0xFFE6A078),
  accentStrong = Color(0xFFF0B895),
  accentSoft = Color(0xFF352A24),
  accentMuted = Color(0xFF80543B),
  onAccent = Color(0xFF241B16),
  secondary = Color(0xFFB9B7AF),
  secondaryStrong = Color(0xFFDBD8D0),
  secondarySoft = Color(0xFF32332F),
  tertiary = Color(0xFFC1A27A),
  tertiaryStrong = Color(0xFFE1C7A5),
  tertiarySoft = Color(0xFF493C2E),
  surface = Color(0xFF20211F),
  surfaceAlt = Color(0xFF292B27),
  surfaceStrong = Color(0xFF3B3D37),
  background = Color(0xFF171815),
  backgroundSoft = Color(0xFF242621),
  text = Color(0xFFEEECE5),
  textMuted = Color(0xFFC4C5BC),
  textSubtle = Color(0xFFA0A398),
  border = Color(0xFF383B33),
  success = Color(0xFF70B493),
  danger = Color(0xFFD88079),
  warning = Color(0xFFD0A660),
  info = Color(0xFF78A8C3),
  tabInactive = Color(0xFFA0A398),
)

/**
 * 语义色 token。
 *
 * 除了原有的扁平语义色，这里补一条**色调阶梯**（`SurfaceContainerLowest` … `SurfaceContainerHighest`）。
 * Expressive 版界面靠这条阶梯区分层级，而不是靠 1dp 描边；两套主题各自单调：
 * 浅色主题从白色页面逐级过渡到中性灰容器，深色主题阶越高越亮。
 * 阶梯上的每个色值都来自 palette，铜橙仅用于强调和选中状态。
 */
private val LocalSextantPalette = staticCompositionLocalOf { LightPalette }

/** RemoteViews 在桌面进程绘制，不能读取 CompositionLocal；在此复用同一色板。 */
data class SextantWidgetPalette(
  val surface: Int, val text: Int, val muted: Int, val accent: Int,
  val onAccent: Int, val track: Int, val danger: Int,
)

fun sextantWidgetPalette(dark: Boolean): SextantWidgetPalette {
  val colors = if (dark) DarkPalette else LightPalette
  return SextantWidgetPalette(colors.surface.toArgb(), colors.text.toArgb(), colors.textMuted.toArgb(),
    colors.accent.toArgb(), colors.onAccent.toArgb(), colors.surfaceStrong.toArgb(), colors.danger.toArgb())
}

object SextantColors {
  private val palette: SextantPalette
    @Composable @ReadOnlyComposable get() = LocalSextantPalette.current

  val Accent @Composable @ReadOnlyComposable get() = palette.accent
  val AccentStrong @Composable @ReadOnlyComposable get() = palette.accentStrong
  val AccentSoft @Composable @ReadOnlyComposable get() = palette.accentSoft
  val AccentMuted @Composable @ReadOnlyComposable get() = palette.accentMuted
  val OnAccent @Composable @ReadOnlyComposable get() = palette.onAccent
  val Secondary @Composable @ReadOnlyComposable get() = palette.secondary
  val SecondaryStrong @Composable @ReadOnlyComposable get() = palette.secondaryStrong
  val SecondarySoft @Composable @ReadOnlyComposable get() = palette.secondarySoft
  val Tertiary @Composable @ReadOnlyComposable get() = palette.tertiary
  val TertiaryStrong @Composable @ReadOnlyComposable get() = palette.tertiaryStrong
  val TertiarySoft @Composable @ReadOnlyComposable get() = palette.tertiarySoft
  val Surface @Composable @ReadOnlyComposable get() = palette.surface
  val SurfaceAlt @Composable @ReadOnlyComposable get() = palette.surfaceAlt
  val SurfaceStrong @Composable @ReadOnlyComposable get() = palette.surfaceStrong
  val Background @Composable @ReadOnlyComposable get() = palette.background
  val BackgroundSoft @Composable @ReadOnlyComposable get() = palette.backgroundSoft
  val Text @Composable @ReadOnlyComposable get() = palette.text
  val TextMuted @Composable @ReadOnlyComposable get() = palette.textMuted
  val TextSubtle @Composable @ReadOnlyComposable get() = palette.textSubtle
  val Border @Composable @ReadOnlyComposable get() = palette.border
  val Success @Composable @ReadOnlyComposable get() = palette.success
  val Danger @Composable @ReadOnlyComposable get() = palette.danger
  val Warning @Composable @ReadOnlyComposable get() = palette.warning
  val Info @Composable @ReadOnlyComposable get() = palette.info
  val TabInactive @Composable @ReadOnlyComposable get() = palette.tabInactive

  /** 页面底色，阶梯的最低一级。 */
  val SurfaceContainerLowest @Composable @ReadOnlyComposable get() = palette.background

  /** 浮在页面之上的卡片、面板、菜单、弹窗。 */
  val SurfaceContainerLow @Composable @ReadOnlyComposable get() = palette.surface

  /** 卡片内部的凹槽：输入框、chip、图标底块所在的层级。 */
  val SurfaceContainer @Composable @ReadOnlyComposable get() = if (palette == DarkPalette) palette.backgroundSoft else palette.surfaceAlt

  /** 凹槽里的凹槽：嵌套分组、备注行。 */
  val SurfaceContainerHigh @Composable @ReadOnlyComposable get() = if (palette == DarkPalette) palette.surfaceAlt else palette.backgroundSoft

  /** 最重的一级：轨道、进度槽、装饰条。 */
  val SurfaceContainerHighest @Composable @ReadOnlyComposable get() = palette.surfaceStrong

  /** 选中态：铜橙淡底，与 [AccentSoft] 同源，读起来更像「被点亮的表面」而不是「描了边的框」。 */
  val SelectedContainer @Composable @ReadOnlyComposable get() = palette.accentSoft
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
  surfaceContainerLow = LightPalette.surface,
  surfaceContainerLowest = LightPalette.background,
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
  surfaceContainer = DarkPalette.backgroundSoft,
  surfaceContainerHigh = DarkPalette.surfaceAlt,
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

  CompositionLocalProvider(LocalSextantPalette provides if (isDarkTheme) DarkPalette else LightPalette) {
    MaterialExpressiveTheme(
      colorScheme = colorScheme,
      typography = SextantTypography,
      shapes = SextantShapes,
      motionScheme = MotionScheme.expressive(),
      content = content,
    )
  }
}

fun argbColor(value: Long): Color {
  return Color(value.toInt())
}

/**
 * Sextant 的形状比例。
 *
 * 取值全部来自 M3 Expressive 的官方形状 token，但整体比基线**高一档**：
 * extraSmall 用官方的 small(8dp)，small 用 medium(12dp)，medium 用 large(16dp)，
 * large 用 largeIncreased(20dp)，extraLarge 用 extraLarge(28dp)。
 * 一档的提升不会让任何控件失控，却足以让面板、卡片和弹窗一眼看出比旧版更圆。
 */
private val SextantShapes = Shapes(
  extraSmall = ShapeDefaults.Small,
  small = ShapeDefaults.Medium,
  medium = ShapeDefaults.Large,
  large = ShapeDefaults.LargeIncreased,
  extraLarge = ShapeDefaults.ExtraLarge,
)

/**
 * 形状阶梯的数值形式。
 *
 * [SextantShapes] 是给 Material 组件用的 `Shape`，但做形状形变时需要在两个圆角之间插值，
 * 插值对象必须是 `Dp` 而不是 `Shape`。数值与上面一一对应，改一处就要改另一处。
 *
 * 这里没有用 `MaterialShapes` 的形状库（Cookie9Sided 之类）：那些是有机形状，用在选中态或
 * 面板上纯属装饰，违背「形状只在表达状态变化时形变」。真正需要有机形状形变的地方是加载
 * 指示器，交给官方的 `LoadingIndicator` 处理。
 */
object SextantRadius {
  val ExtraSmall = 8.dp
  val Small = 12.dp
  val Medium = 16.dp
  val Large = 20.dp
  val ExtraLarge = 28.dp

  /**
   * 胶囊的数值替身。
   *
   * [SextantFullShape] 是百分比圆角，没法参与 `Dp` 插值；64dp 以下的控件用 32dp 就是
   * 完全圆头的效果，与百分比写法视觉等价。
   */
  val Full = 32.dp
}

/**
 * 胶囊：按钮、chip、分段控件和导航选中指示共用。
 *
 * Sextant 为按钮、chip 和导航指示选用全圆角，形状本身承担了「这是操作」的语义，
 * 所以它不能跟着 [SextantShapes] 一起缩水。
 */
val SextantFullShape: Shape = RoundedCornerShape(percent = 50)

/** 填充式输入框：只有上面两角收口，下面贴合所在表面。M3 的 extraSmall(4dp) 顶部圆角。 */
val SextantFieldShape: Shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

/** 底部面板与弹窗的顶部圆角，取 M3 的 extraLarge(28dp)。 */
val SextantSheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * 图表柱的顶部圆角：只圆上沿，底边保持平直贴合坐标轴，因此不能直接用 [SextantShapes]
 * 的四角圆角。数值取 M3 的 small（8dp），与面板内其它圆角同源。
 */
val SextantBarShape: Shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)

/**
 * M3 Expressive 的动效 token，直接读主题里的 [MotionScheme]。
 *
 * `SextantTheme` 用 `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())` 装好，
 * 这里只是给调用方一组有语义的名字。expressive 的空间弹簧比 standard 软得多
 * （刚度 380 对 700、阻尼 0.8 对 0.9），所以位移类动画会带一点回弹；
 * 效果弹簧两边都是临界阻尼，颜色和透明度不过冲。
 *
 * 规则：位移、尺寸、旋转、圆角用 `*Spatial`；颜色和透明度用 `*Effects`。
 * 这些函数是 `@Composable` 的，因为弹簧来自 CompositionLocal——在非 composable 的
 * lambda（比如 `AnimatedContent` 的 `transitionSpec`）里用，要先把值取出来。
 */
object SextantMotion {
  /** 小控件（按钮、开关、指示条）的即时反馈。 */
  @Composable
  @ReadOnlyComposable
  fun <T> fastSpatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

  /** 大多数位移、尺寸与圆角形变。 */
  @Composable
  @ReadOnlyComposable
  fun <T> defaultSpatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

  /** 大面积展开。 */
  @Composable
  @ReadOnlyComposable
  fun <T> slowSpatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

  @Composable
  @ReadOnlyComposable
  fun <T> fastEffects(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

  @Composable
  @ReadOnlyComposable
  fun <T> defaultEffects(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

  @Composable
  @ReadOnlyComposable
  fun <T> slowEffects(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowEffectsSpec()
}

/** 手机上的紧凑排版阶梯；保留系统字体缩放、常规字重和等宽数字。 */
private val SextantTypography: Typography = Typography().let { base ->
  Typography(
    displayLarge = base.displayLarge.tnum(),
    displayMedium = base.displayMedium.tnum(),
    displaySmall = base.displaySmall.tnum(),
    headlineLarge = base.headlineLargeEmphasized.tnum().copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = base.headlineMedium.tnum().copy(fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = base.headlineSmall.tnum().copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = base.titleLarge.tnum().copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = base.titleMedium.tnum().copy(fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.tnum(),
    bodyLarge = base.bodyLarge.tnum().copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.tnum().copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = base.bodySmall.tnum(),
    labelLarge = base.labelLarge.tnum().copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = base.labelMedium.tnum(),
    labelSmall = base.labelSmall.tnum(),
  )
}

private fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * 把任意语义样式提到 M3 Expressive 的 emphasized 档位，用于临时加强某处。
 *
 * Sextant 的局部强调约定：常规字重提到 Medium，Medium 及以上提到 Bold。
 * 只用于关键标题和状态，基础排版保留未强调的层级。
 */
fun TextStyle.sextantEmphasized(): TextStyle {
  val weight = fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal
  val emphasized = if (weight.weight >= 500) {
    androidx.compose.ui.text.font.FontWeight.Bold
  } else {
    androidx.compose.ui.text.font.FontWeight.Medium
  }
  return copy(fontWeight = emphasized)
}
