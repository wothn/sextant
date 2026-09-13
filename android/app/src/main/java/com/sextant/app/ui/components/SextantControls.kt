@file:OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalFoundationApi::class,
  ExperimentalMaterial3ExpressiveApi::class,
)

package com.sextant.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantFieldShape
import com.sextant.app.ui.theme.SextantFullShape
import com.sextant.app.ui.theme.SextantMotion
import com.sextant.app.ui.theme.SextantRadius
import com.sextant.app.ui.theme.SextantSheetShape

/**
 * 全部控件的产品默认外观都在这一个文件里。调用方只负责状态、文案、校验和事件。
 *
 * 这一版把「材质与描边」换成了「色调分层 + 形状」：
 * - 不再用 1dp 描边划分区块，改用 `SextantColors` 的色调阶梯（Lowest → Highest）；
 * - 可点的东西默认是胶囊（[SextantFullShape]），形状本身表达「这是操作」；
 * - 状态变化用 expressive 弹簧做形状形变（圆角、指示条、按钮按压），而不是瞬间切换。
 *
 * 能交给官方 M3 Expressive 组件的都交了：按钮尺寸与按压形状来自 `ButtonDefaults`，
 * 分段选择来自 `ButtonGroup`，加载与波浪进度来自 `LoadingIndicator` /
 * `LinearWavyProgressIndicator`。只有导航栏是自己画的，原因见 [SextantNavigationBar]。
 *
 * 页面不得退回未包装的 Material 3 控件：Material 默认取 `surfaceContainer` /
 * `outlineVariant`，与 SextantColors 并不相等，两套主题会立刻跑偏。
 */

// ---------------------------------------------------------------------------
// 尺寸阶梯
// ---------------------------------------------------------------------------

/**
 * 按钮尺寸。高度、内边距、图标尺寸与图标间距全部取自 M3 Expressive 的
 * `ButtonDefaults` 尺寸阶梯（extraSmall 32 / small 40 / medium 56 / large 96dp），
 * 这里只是把「一档尺寸」打包成一个语义名，供调用方和 [SextantButton] 共用。
 *
 * 触摸区域一律不小于 48dp：低于 48dp 的两档由 Material 的
 * `minimumInteractiveComponentSize` 补足点击范围，视觉仍然紧凑。
 */
enum class SextantButtonSize(
  internal val height: Dp,
  internal val contentPadding: PaddingValues,
  internal val iconSize: Dp,
  internal val iconSpacing: Dp,
) {
  ExtraSmall(
    height = ButtonDefaults.ExtraSmallContainerHeight,
    contentPadding = ButtonDefaults.ExtraSmallContentPadding,
    iconSize = ButtonDefaults.ExtraSmallIconSize,
    iconSpacing = ButtonDefaults.ExtraSmallIconSpacing,
  ),
  Small(
    height = ButtonDefaults.MinHeight,
    contentPadding = ButtonDefaults.SmallContentPadding,
    iconSize = ButtonDefaults.SmallIconSize,
    iconSpacing = ButtonDefaults.IconSpacing,
  ),
  Medium(
    height = ButtonDefaults.MediumContainerHeight,
    contentPadding = ButtonDefaults.MediumContentPadding,
    iconSize = ButtonDefaults.MediumIconSize,
    iconSpacing = ButtonDefaults.MediumIconSpacing,
  ),
  Large(
    height = ButtonDefaults.LargeContainerHeight,
    contentPadding = ButtonDefaults.LargeContentPadding,
    iconSize = ButtonDefaults.LargeIconSize,
    iconSpacing = ButtonDefaults.LargeIconSpacing,
  ),
  ;

  /**
   * 未按下是胶囊（shape），按下收成方圆角（pressedShape）——官方给的形状对。
   * `shapesFor` 要读主题，所以必须留在 composable 里调用。
   */
  @Composable
  internal fun shapes(): ButtonShapes = ButtonDefaults.shapesFor(height)
}

// ---------------------------------------------------------------------------
// 菜单
// ---------------------------------------------------------------------------

/** 菜单：Surface 底、medium 圆角、无描边，靠 3dp 阴影浮在内容之上。 */
@Composable
fun SextantDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest,
    modifier = Modifier.widthIn(min = 184.dp), shape = MaterialTheme.shapes.medium,
    containerColor = SextantColors.SurfaceContainerLow, tonalElevation = 0.dp, shadowElevation = 3.dp,
    border = null, content = content)
}

@Composable
fun SextantDropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, enabled: Boolean = true) {
  DropdownMenuItem(text = { ProvideTextStyle(MaterialTheme.typography.bodyLarge, text) },
    onClick = onClick, enabled = enabled,
    colors = MenuDefaults.itemColors(textColor = SextantColors.Text, disabledTextColor = SextantColors.TextSubtle),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp))
}

// ---------------------------------------------------------------------------
// 弹窗
// ---------------------------------------------------------------------------

/** 确认弹窗：Surface 底、extraLarge(28dp) 圆角、无色调叠加，长内容可滚动。 */
@Composable
fun SextantAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  dismissButton: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
) {
  AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton,
    title = title, text = text?.let { body -> { Column(Modifier.verticalScroll(rememberScrollState())) { body() } } },
    shape = MaterialTheme.shapes.extraLarge, containerColor = SextantColors.SurfaceContainerLow,
    titleContentColor = SextantColors.Text, textContentColor = SextantColors.TextMuted, tonalElevation = 0.dp)
}

// ---------------------------------------------------------------------------
// 按钮
// ---------------------------------------------------------------------------

/**
 * 主按钮：Accent 底、OnAccent 内容、全圆角胶囊。
 *
 * 按下时圆角从胶囊收成方圆角再弹回，形状对（`shape` / `pressedShape`）由
 * M3 Expressive 的 `ButtonDefaults.shapesFor(高度)` 决定，不自己插值圆角。
 */
@Composable
fun SextantButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  size: SextantButtonSize = SextantButtonSize.Small,
  colors: ButtonColors = ButtonDefaults.buttonColors(
    containerColor = SextantColors.Accent,
    contentColor = SextantColors.OnAccent,
    disabledContainerColor = SextantColors.SurfaceContainerHighest,
    disabledContentColor = SextantColors.TextSubtle,
  ),
  content: @Composable RowScope.() -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val shapes = size.shapes()
  Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = size.height),
    enabled = enabled,
    shapes = shapes,
    colors = colors,
    elevation = null,
    contentPadding = size.contentPadding,
    interactionSource = interactionSource,
    content = content,
  )
}

/**
 * 次按钮：中性色调填充，不用描边也不用品牌色。
 *
 * 之前次按钮是「1dp 边框 + 铜橙文字」，在淡化边框的方向下改成 tonal：SurfaceContainerHigh
 * 底 + 正文色，跟主按钮并排时层级明确，但不会两个按钮都在喊。
 */
@Composable
fun SextantTonalButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  size: SextantButtonSize = SextantButtonSize.Small,
  content: @Composable RowScope.() -> Unit,
) {
  SextantButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    size = size,
    colors = ButtonDefaults.buttonColors(
      containerColor = SextantColors.SurfaceContainerHigh,
      contentColor = SextantColors.Text,
      disabledContainerColor = SextantColors.SurfaceContainerHighest,
      disabledContentColor = SextantColors.TextSubtle,
    ),
    content = content,
  )
}

/**
 * 描边按钮。描边是它唯一的定义，因此这是最后一个还在用 1dp 边框的控件，
 * 保留给「取消 / 重置」这类需要和填充按钮拉开距离的操作。
 */
@Composable
fun SextantOutlinedButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  size: SextantButtonSize = SextantButtonSize.Small,
  content: @Composable RowScope.() -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val shapes = size.shapes()
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = size.height),
    enabled = enabled,
    shapes = shapes,
    border = BorderStroke(1.dp, if (enabled) SextantColors.Border else SextantColors.Border.copy(alpha = 0.5f)),
    colors = ButtonDefaults.outlinedButtonColors(
      contentColor = SextantColors.Text,
      disabledContentColor = SextantColors.TextSubtle,
    ),
    contentPadding = size.contentPadding,
    interactionSource = interactionSource,
    content = content,
  )
}

/** 文字按钮：无底色，只有内容色。点击高度仍按 48dp 保证可达。 */
@Composable
fun SextantTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
  TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
    shape = SextantFullShape,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    colors = ButtonDefaults.textButtonColors(
      contentColor = SextantColors.AccentStrong,
      disabledContentColor = SextantColors.TextSubtle,
    ), content = content)
}

/**
 * 返回、关闭、更多等图标动作。Material 3 已保证 48dp 点击区域，图标本身保持紧凑。
 */
@Composable
fun SextantIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tint: Color = SextantColors.TextMuted,
  content: @Composable () -> Unit,
) {
  IconButton(onClick = onClick, modifier = modifier, enabled = enabled,
    colors = IconButtonDefaults.iconButtonColors(
      contentColor = tint, disabledContentColor = SextantColors.TextSubtle), content = content)
}

/**
 * 相连按钮组（M3 Expressive 的 [ButtonGroup]）。
 *
 * 用于同一维度、互斥、只有两三个选项的切换——记账面板的收支方向。形状、选中态的形变、
 * 按压缩放以及放不下时的溢出菜单全部交给官方组件：选中项会用 `connectedButtonCheckedShape`
 * （胶囊）并略微展开，这是 M3 定义的形态。
 *
 * `toggleableItem` 本身允许多选，这里靠 `checked = 是否等于当前值` 把它收成单选。
 */
@Composable
fun <T> SextantSegmentedButtonGroup(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  label: (T) -> String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  ButtonGroup(
    overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState = menuState) },
    modifier = modifier,
  ) {
    options.forEach { option ->
      toggleableItem(
        checked = option == selected,
        label = label(option),
        onCheckedChange = { checked -> if (checked) onSelect(option) },
        weight = 1f,
        enabled = enabled,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 输入
// ---------------------------------------------------------------------------

/**
 * 文本输入：填充式、无描边。
 *
 * 之前是 OutlinedTextField 加 SurfaceAlt 底——既有填充又有边框，等于说了两遍。Expressive
 * 方向下填充本身承担边界，只留一条会随焦点变色的底线：未聚焦时透明，聚焦时 2dp 铜橙，
 * 出错时 Danger。顶部 4dp 圆角是 M3 填充输入框的规定形状。
 */
@Composable
fun SextantTextField(
  value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
  label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
  prefix: (@Composable () -> Unit)? = null, suffix: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
  singleLine: Boolean = false, isError: Boolean = false, enabled: Boolean = true,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
  TextField(value = value, onValueChange = onValueChange, modifier = modifier,
    label = label, placeholder = placeholder, prefix = prefix, suffix = suffix, supportingText = supportingText,
    singleLine = singleLine, isError = isError, enabled = enabled, keyboardOptions = keyboardOptions,
    shape = SextantFieldShape, textStyle = MaterialTheme.typography.bodyLarge,
    colors = TextFieldDefaults.colors(
      focusedContainerColor = SextantColors.SurfaceContainer,
      unfocusedContainerColor = SextantColors.SurfaceContainer,
      errorContainerColor = SextantColors.SurfaceContainer,
      disabledContainerColor = SextantColors.SurfaceContainer,
      focusedIndicatorColor = SextantColors.Accent,
      unfocusedIndicatorColor = Color.Transparent,
      errorIndicatorColor = SextantColors.Danger,
      disabledIndicatorColor = Color.Transparent,
      cursorColor = SextantColors.Accent,
      focusedTextColor = SextantColors.Text,
      unfocusedTextColor = SextantColors.Text,
      disabledTextColor = SextantColors.TextSubtle,
      focusedLabelColor = SextantColors.AccentStrong,
      unfocusedLabelColor = SextantColors.TextMuted,
      disabledLabelColor = SextantColors.TextSubtle,
      errorLabelColor = SextantColors.Danger,
      focusedPlaceholderColor = SextantColors.TextSubtle,
      unfocusedPlaceholderColor = SextantColors.TextSubtle,
      focusedSupportingTextColor = SextantColors.TextSubtle,
      unfocusedSupportingTextColor = SextantColors.TextSubtle,
      errorSupportingTextColor = SextantColors.Danger,
      focusedPrefixColor = SextantColors.TextMuted,
      unfocusedPrefixColor = SextantColors.TextMuted,
      focusedSuffixColor = SextantColors.TextMuted,
      unfocusedSuffixColor = SextantColors.TextMuted,
    ))
}

// ---------------------------------------------------------------------------
// 表面
// ---------------------------------------------------------------------------

/**
 * 分组面板与卡片：Surface 底、medium 圆角、零阴影、**无描边**。
 *
 * 边界靠色调分层表达——面板浮在 Background 上，面板里的凹槽用
 * [SextantColors.SurfaceContainer]，不再需要一条 1dp 的线去划分。
 */
@Composable
fun SextantCard(
  modifier: Modifier = Modifier,
  colors: CardColors = CardDefaults.cardColors(containerColor = SextantColors.SurfaceContainerLow),
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(modifier = modifier, shape = MaterialTheme.shapes.medium, colors = colors,
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), content = content)
}

/** 分组面板：与 [SextantCard] 同一套外观，但内容布局由调用方决定。 */
@Composable
fun SextantSurface(
  modifier: Modifier = Modifier,
  color: Color = SextantColors.SurfaceContainerLow,
  shape: Shape = MaterialTheme.shapes.medium,
  content: @Composable () -> Unit,
) {
  Surface(modifier = modifier, shape = shape, color = color, contentColor = SextantColors.Text,
    tonalElevation = 0.dp, shadowElevation = 0.dp, content = content)
}

/**
 * 可选行 / 可选格（分类格、分类列表行、来源行）。
 *
 * 选中态只有一种写法：AccentSoft 填充 + AccentStrong 内容，**不再画 1dp 描边**——
 * 那圈线在深色主题里几乎不可见，在浅色主题里又和卡片的边缘抢注意力，填充已经说清楚了。
 * 选中时圆角从 medium 长到 large，形状变化本身就是反馈。
 */
@Composable
fun SextantSelectableSurface(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  role: Role = Role.Button,
  onClickLabel: String? = null,
  content: @Composable () -> Unit,
) {
  val container by animateColorAsState(
    targetValue = if (selected) SextantColors.SelectedContainer else SextantColors.SurfaceContainerLow,
    animationSpec = SextantMotion.defaultEffects(), label = "selectableContainer",
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) SextantColors.AccentStrong else SextantColors.Text,
    animationSpec = SextantMotion.defaultEffects(), label = "selectableContent",
  )
  Surface(
    modifier = modifier
      .semantics { this.selected = selected }
      .clickable(enabled = enabled, role = role, onClickLabel = onClickLabel, onClick = onClick),
    shape = morphCorner(selected, SextantRadius.Large, SextantRadius.Medium),
    color = container,
    contentColor = contentColor,
    border = null,
    tonalElevation = 0.dp,
    content = content,
  )
}

/** 行分隔线。默认 Border 色。淡化边框之后只在真正需要「同一块里的并列项」时使用。 */
@Composable
fun SextantDivider(modifier: Modifier = Modifier, color: Color = SextantColors.Border, thickness: Dp = 1.dp) {
  HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}

// ---------------------------------------------------------------------------
// 选择控件
// ---------------------------------------------------------------------------

/** 筛选 chip 的配色。未选中是凹槽色，选中是铜橙淡底，两者都不描边。 */
@Composable
fun sextantFilterChipColors() = FilterChipDefaults.filterChipColors(
  containerColor = SextantColors.SurfaceContainer,
  labelColor = SextantColors.Text,
  iconColor = SextantColors.TextMuted,
  selectedContainerColor = SextantColors.AccentSoft,
  selectedLabelColor = SextantColors.AccentStrong,
  selectedLeadingIconColor = SextantColors.AccentStrong,
  selectedTrailingIconColor = SextantColors.AccentStrong,
  disabledContainerColor = SextantColors.SurfaceContainer.copy(alpha = 0.5f),
  disabledLabelColor = SextantColors.Text.copy(alpha = 0.38f),
  disabledSelectedContainerColor = SextantColors.AccentSoft.copy(alpha = 0.5f),
)

/**
 * 筛选 chip：胶囊形状，未选中 SurfaceContainer 淡底，选中 AccentSoft 底，都不描边。
 *
 * 未选中不能用纯白 Surface 底：浅色主题下白底比页面背景更亮，会让「没选」看起来像「选了」。
 * 选中时保留前导图标并补一个勾——只靠颜色区分选中对色觉障碍不友好。
 */
@Composable
fun SextantFilterChip(
  selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
  modifier: Modifier = Modifier, enabled: Boolean = true,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  colors: SelectableChipColors = sextantFilterChipColors(),
) {
  FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier,
    enabled = enabled,
    leadingIcon = if (selected) {
      {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          leadingIcon?.invoke()
          Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        }
      }
    } else {
      leadingIcon
    },
    trailingIcon = trailingIcon,
    shape = SextantFullShape, colors = colors, border = null)
}

@Composable
fun sextantSwitchColors() = SwitchDefaults.colors(
  checkedThumbColor = SextantColors.OnAccent,
  checkedTrackColor = SextantColors.Accent,
  checkedBorderColor = SextantColors.Accent,
  checkedIconColor = SextantColors.Accent,
  uncheckedThumbColor = SextantColors.Surface,
  uncheckedTrackColor = SextantColors.SurfaceContainerHighest,
  uncheckedBorderColor = SextantColors.SurfaceContainerHighest,
  uncheckedIconColor = SextantColors.TextSubtle,
  disabledCheckedThumbColor = SextantColors.Surface,
  disabledCheckedTrackColor = SextantColors.Accent.copy(alpha = 0.38f),
  disabledCheckedBorderColor = Color.Transparent,
  disabledUncheckedThumbColor = SextantColors.Surface,
  disabledUncheckedTrackColor = SextantColors.SurfaceContainerHighest.copy(alpha = 0.5f),
  disabledUncheckedBorderColor = Color.Transparent,
)

@Composable
fun SextantSwitch(
  checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
  enabled: Boolean = true, colors: SwitchColors = sextantSwitchColors(),
) {
  Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = colors)
}

@Composable
fun SextantRadioButton(
  selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true,
) {
  RadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled,
    colors = RadioButtonDefaults.colors(
      selectedColor = SextantColors.Accent,
      unselectedColor = SextantColors.TextMuted,
      disabledSelectedColor = SextantColors.Accent.copy(alpha = 0.38f),
      disabledUnselectedColor = SextantColors.TextMuted.copy(alpha = 0.38f)))
}

// ---------------------------------------------------------------------------
// 进度与加载
// ---------------------------------------------------------------------------

/**
 * 波浪进度条（不确定进度）。用官方的 [LinearWavyProgressIndicator]：波形、振幅、
 * 波长和动画都由 M3 决定，不自己画正弦曲线。
 *
 * 用在「正在导出 / 恢复备份」这类没有真实百分比的等待上——比转圈更能说明「还在动」，
 * 也不会假装有进度。
 */
@Composable
fun SextantWavyProgressIndicator(
  modifier: Modifier = Modifier,
  color: Color = SextantColors.Accent,
  trackColor: Color = SextantColors.SurfaceContainerHighest,
) {
  LinearWavyProgressIndicator(
    modifier = modifier,
    color = color,
    trackColor = trackColor,
  )
}

/**
 * 不确定进度指示器。用官方的 [ContainedLoadingIndicator]：它在一组 `MaterialShapes`
 * 多边形之间做形状形变（SoftBurst → Cookie9Sided → Pentagon → Pill → Sunny → Cookie4Sided
 * → Oval），每 650ms 一步。只在真的没有进度可报时用，不要拿它当装饰。
 *
 * [size] 默认 48dp（M3 的容器尺寸）；行内使用可以调到 20–24dp。
 */
@Composable
fun SextantLoadingIndicator(
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
  color: Color = SextantColors.Accent,
  containerColor: Color = SextantColors.SurfaceContainer,
) {
  ContainedLoadingIndicator(
    modifier = modifier.size(size),
    containerColor = containerColor,
    indicatorColor = color,
  )
}

// ---------------------------------------------------------------------------
// 浮动操作按钮
// ---------------------------------------------------------------------------

/**
 * 浮动操作按钮：56dp、medium(16dp) 圆角、铜橙填充。
 *
 * Expressive 里主操作从导航栏里搬出来变成 FAB，所以它比过去的居中图标按钮更大、更靠手。
 *
 * 没有展开态：Sextant 的记账入口直接进表单，收支方向和类型都在表单里选。把
 * 「记支出 / 记收入」提到 FAB 菜单，只会让高频的记支出每次多一次点击，而且它和表单顶部的
 * 三段选择重复、还少一个转账入口。
 */
@Composable
fun SextantFab(
  onClick: () -> Unit,
  icon: ImageVector,
  contentDescription: String,
  modifier: Modifier = Modifier,
  containerColor: Color = SextantColors.Accent,
  contentColor: Color = SextantColors.OnAccent,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.size(56.dp),
    shape = MaterialTheme.shapes.medium,
    color = containerColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 3.dp,
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
  }
}

/**
 * 铺了记账 FAB 的列表（目前只有概览）底部要预留的空白：56dp 按钮 + 16dp 下边距 + 16dp 呼吸。
 *
 * 页面把它交给 `LazyColumn` 的 `contentPadding.bottom`，最后一行就不会停在 FAB 底下——
 * 明细的金额正在右边，也正是 FAB 待的位置。没有 FAB 的页面不需要预留。
 */
val SextantFabClearance: Dp = 88.dp

/**
 * 会随滚动收起的记账 FAB。
 *
 * 记账入口放在右下角就会压住明细最右列——也就是金额。让它跟着滚动方向进出，是「入口常在」
 * 和「金额不被遮」之间唯一不用二选一的解法：翻看明细时它让开，停下或往回滚时它立刻回来。
 *
 * 入口只出现在概览，显隐由调用方从当前目的地和滚动状态一起推导。位移用空间弹簧、透明度用
 * 效果弹簧，和主题里其它进出场一致；[visible] 为 false 时整块离开组合，不会留下一个看不见
 * 却还能点的区域。
 */
@Composable
fun SextantCollapsibleFab(
  visible: Boolean,
  onClick: () -> Unit,
  icon: ImageVector,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = slideInVertically(
      animationSpec = SextantMotion.defaultSpatial(),
      initialOffsetY = { height -> height },
    ) + fadeIn(animationSpec = SextantMotion.defaultEffects()),
    exit = slideOutVertically(
      animationSpec = SextantMotion.defaultSpatial(),
      targetOffsetY = { height -> height },
    ) + fadeOut(animationSpec = SextantMotion.defaultEffects()),
    label = "quickEntryFab",
  ) {
    SextantFab(onClick = onClick, icon = icon, contentDescription = contentDescription)
  }
}

// ---------------------------------------------------------------------------
// 底部导航
// ---------------------------------------------------------------------------

/**
 * 底部导航栏容器：Surface 底、无描边、内容横向等分。
 *
 * 为什么不用 material3 的 `ShortNavigationBar`：它只有在作为 `bottomBar` 的直接内容时才按
 * 条目数布局；一旦被包进外层 Layout（哪怕给足整屏宽度），就只渲染一个居中的条目——
 * 4 个目的地只剩 1 个可见。在 1.4.0 stable 与 1.5.0-alpha18 上都能复现，所以按 M3 短导航栏
 * 的规格自己实现——64dp 高、药丸形选中指示、图标 24dp、标签 labelMedium。
 */
@Composable
fun SextantNavigationBar(
  modifier: Modifier = Modifier,
  containerColor: Color = SextantColors.SurfaceContainerLow,
  content: @Composable RowScope.() -> Unit,
) {
  Surface(
    modifier = modifier,
    color = containerColor,
    contentColor = SextantColors.Text,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).selectableGroup()
        .padding(horizontal = 4.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      content = content,
    )
  }
}

/**
 * 导航条目。选中态是药丸指示条加铜橙图标与标签，指示条宽度用空间弹簧在
 * 40dp 与 64dp 之间形变，未选中退到次级文字色。
 *
 * `icon` / `label` 不需要自己指定颜色：内容色通过 `LocalContentColor` 传下来，
 * 选中切换时图标和文字会一起过渡，不会出现「图标变了文字没变」的中间态。
 */
@Composable
fun RowScope.SextantNavigationItem(
  selected: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  val indicatorWidth by animateDpAsState(
    targetValue = if (selected) 64.dp else 40.dp,
    animationSpec = SextantMotion.defaultSpatial(),
    label = "navIndicatorWidth",
  )
  val indicatorColor by animateColorAsState(
    targetValue = if (selected) SextantColors.AccentSoft else Color.Transparent,
    animationSpec = SextantMotion.defaultEffects(),
    label = "navIndicatorColor",
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) SextantColors.AccentStrong else SextantColors.TextSubtle,
    animationSpec = SextantMotion.defaultEffects(),
    label = "navContentColor",
  )
  Column(
    modifier = modifier
      .weight(1f)
      .heightIn(min = 64.dp)
      .selectable(selected = selected, role = Role.Tab, onClick = onClick)
      .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
  ) {
    Box(
      modifier = Modifier
        .width(indicatorWidth)
        .height(32.dp)
        .clip(SextantFullShape)
        .background(indicatorColor),
      contentAlignment = Alignment.Center,
    ) {
      CompositionLocalProvider(LocalContentColor provides contentColor) { icon() }
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) { label() }
  }
}

// ---------------------------------------------------------------------------
// 页面头部
// ---------------------------------------------------------------------------

/**
 * 会随滚动收起的页面标题。
 *
 * 用法：放在列表**之外**，和 `LazyColumn` 共用同一个 [LazyListState]。展开时是
 * headlineMedium 加六分仪标记，副标题可见；收起后压成 titleMedium 的单行标题，
 * 标记与副标题淡出。整条头部有背景色，不会透出下面的明细。
 *
 * 官方的 `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar` 需要 `TopAppBarScrollBehavior`
 * 与 `nestedScroll` 配合，并且会把标题放进 `TopAppBar` 里；Sextant 的头部就是列表外的一个
 * `Column`，用列表的滚动位置直接驱动收起，行为更可控，也少一层嵌套滚动。
 */
@Composable
fun SextantFlexibleHeader(
  title: String,
  scrollState: LazyListState,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  collapsedHeight: Dp = 60.dp,
  expandedHeight: Dp = 104.dp,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val collapseDistance = with(LocalDensity.current) { (expandedHeight - collapsedHeight).toPx() }
  val collapse = if (scrollState.firstVisibleItemIndex > 0) {
    1f
  } else {
    (scrollState.firstVisibleItemScrollOffset / collapseDistance.coerceAtLeast(1f)).coerceIn(0f, 1f)
  }
  val height = expandedHeight - (expandedHeight - collapsedHeight) * collapse
  val titleStyle: TextStyle = lerp(
    MaterialTheme.typography.headlineMedium,
    MaterialTheme.typography.titleMedium,
    collapse,
  )
  val markAlpha = (1f - collapse * 1.6f).coerceIn(0f, 1f)
  val subtitleAlpha = (1f - collapse * 2.2f).coerceIn(0f, 1f)

  Column(
    modifier
      .fillMaxWidth()
      .background(SextantColors.SurfaceContainerLowest)
      // heightIn 而不是 height：标题与副标题各限一行，但大字体下这一行会变高，
      // 固定高度会把文字裁掉。给下界既保证收起时的紧凑，也不会截断。
      .heightIn(min = height)
      .padding(vertical = 6.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (markAlpha > 0.01f) {
          Box(Modifier.alpha(markAlpha)) { SextantMark() }
        }
        Text(
          text = title,
          style = titleStyle,
          color = SextantColors.Text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      actions()
    }
    if (subtitle != null && subtitleAlpha > 0.01f) {
      Text(
        text = subtitle,
        modifier = Modifier.alpha(subtitleAlpha),
        style = MaterialTheme.typography.bodyMedium,
        color = SextantColors.TextSubtle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 底部面板与日期时间
// ---------------------------------------------------------------------------

/** 底部面板：28dp 顶部圆角、Surface 底、统一拖动柄、无描边。 */
@Composable
fun SextantBottomSheet(
  onDismissRequest: () -> Unit,
  sheetState: SheetState = rememberModalBottomSheetState(),
  sheetGesturesEnabled: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, sheetGesturesEnabled = sheetGesturesEnabled,
    shape = SextantSheetShape,
    containerColor = SextantColors.SurfaceContainerLow, contentColor = SextantColors.Text, tonalElevation = 0.dp,
    dragHandle = { BottomSheetDefaults.DragHandle(color = SextantColors.SurfaceContainerHighest) }, content = content)
}

/**
 * 日期选择器的配色。弹窗外壳与内部日历必须共用同一套 palette：只给 [DatePickerDialog]
 * 传 `containerColor` 的话，日历仍会留在 Material 的 `surfaceContainer` 上，
 * 表现出灰底嵌在白卡里的割裂感。
 */
@Composable
fun sextantDatePickerColors() = DatePickerDefaults.colors(
  containerColor = SextantColors.SurfaceContainerLow,
  titleContentColor = SextantColors.TextSubtle,
  headlineContentColor = SextantColors.Text,
  weekdayContentColor = SextantColors.TextSubtle,
  subheadContentColor = SextantColors.TextSubtle,
  navigationContentColor = SextantColors.TextMuted,
  yearContentColor = SextantColors.Text,
  currentYearContentColor = SextantColors.AccentStrong,
  selectedYearContentColor = SextantColors.OnAccent,
  selectedYearContainerColor = SextantColors.Accent,
  dayContentColor = SextantColors.Text,
  selectedDayContentColor = SextantColors.OnAccent,
  selectedDayContainerColor = SextantColors.Accent,
  todayContentColor = SextantColors.AccentStrong,
  todayDateBorderColor = SextantColors.AccentMuted,
  dividerColor = SextantColors.Border,
)

/**
 * 时间选择器的配色。
 *
 * 参数名在 material3 的 alpha 之间改过：1.5.0-alpha18 用 `*Unselected*`，更新的 alpha28
 * 改成了 `clockDialContentColor` / `periodSelectorContainerColor` 等。升级 material3 时先看这里。
 */
@Composable
fun sextantTimePickerColors() = TimePickerDefaults.colors(
  containerColor = SextantColors.SurfaceContainerLow,
  clockDialColor = SextantColors.SurfaceContainer,
  clockDialSelectedContentColor = SextantColors.OnAccent,
  clockDialUnselectedContentColor = SextantColors.Text,
  selectorColor = SextantColors.Accent,
  periodSelectorBorderColor = SextantColors.Border,
  periodSelectorSelectedContainerColor = SextantColors.AccentSoft,
  periodSelectorUnselectedContainerColor = SextantColors.SurfaceContainer,
  periodSelectorSelectedContentColor = SextantColors.AccentStrong,
  periodSelectorUnselectedContentColor = SextantColors.TextMuted,
  timeSelectorSelectedContainerColor = SextantColors.AccentSoft,
  timeSelectorUnselectedContainerColor = SextantColors.SurfaceContainer,
  timeSelectorSelectedContentColor = SextantColors.AccentStrong,
  timeSelectorUnselectedContentColor = SextantColors.TextMuted,
)

/** [SextantDatePickerDialog] 的日历主体。调用方仍然持有 picker state。 */
@Composable
fun SextantDatePicker(state: DatePickerState, modifier: Modifier = Modifier, showModeToggle: Boolean = false) {
  DatePicker(state = state, modifier = modifier, showModeToggle = showModeToggle,
    colors = sextantDatePickerColors())
}

/** [SextantTimePickerDialog] 的时钟主体。调用方仍然持有 picker state。 */
@Composable
fun SextantTimePicker(state: TimePickerState, modifier: Modifier = Modifier) {
  TimePicker(state = state, modifier = modifier, colors = sextantTimePickerColors())
}

@Composable
fun SextantDatePickerDialog(
  onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
  dismissButton: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
  DatePickerDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton,
    shape = MaterialTheme.shapes.extraLarge, colors = sextantDatePickerColors(),
    tonalElevation = 0.dp, content = content)
}

/** 与 [SextantDatePickerDialog] 同壳的时间弹窗；内容是 [SextantTimePicker]。 */
@Composable
fun SextantTimePickerDialog(
  onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
  dismissButton: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
  AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton,
    shape = MaterialTheme.shapes.extraLarge, containerColor = SextantColors.SurfaceContainerLow,
    titleContentColor = SextantColors.Text, textContentColor = SextantColors.Text,
    tonalElevation = 0.dp, text = content)
}

// ---------------------------------------------------------------------------
// 形状形变
// ---------------------------------------------------------------------------

/**
 * 在两个圆角之间按 expressive 空间弹簧形变。
 *
 * 圆角本身是可以插值的数值，选中态的圆角生长用它。传 `Dp` 而不是 `Shape`，因为百分比圆角
 * 没法插值——胶囊用 [SextantRadius.Full] 代替。
 *
 * 按钮按下不需要这个：官方 `ButtonDefaults.shapesFor()` 已经给好了 `shape` / `pressedShape`。
 * 也没有用 `MaterialShapes`：那些是有机形状，用在选中态或面板上纯属装饰，真正需要它的
 * 加载指示器已经交给官方组件。
 */
@Composable
fun morphCorner(active: Boolean, activeRadius: Dp, inactiveRadius: Dp): Shape {
  val radius by animateDpAsState(
    targetValue = if (active) activeRadius else inactiveRadius,
    animationSpec = SextantMotion.defaultSpatial(),
    label = "morphCorner",
  )
  return RoundedCornerShape(radius)
}
