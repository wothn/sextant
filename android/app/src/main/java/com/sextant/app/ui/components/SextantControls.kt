@file:OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalFoundationApi::class,
  ExperimentalMaterial3ExpressiveApi::class,
)

package com.sextant.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantFieldShape
import com.sextant.app.ui.theme.SextantFullShape
import com.sextant.app.ui.theme.SextantMotion
import com.sextant.app.ui.theme.SextantRadius

/** 基础控件的主题与行为包装；导航和弹窗分别在 SextantNavigationControls、SextantDialogs。 */

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

/** 并列的工具入口共用淡底、24dp 图标与 48dp 点击区域；生效条件使用强调色。 */
@Composable
fun SextantTonalIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
  content: @Composable () -> Unit,
) {
  FilledTonalIconButton(
    onClick = onClick,
    modifier = modifier.size(48.dp),
    shape = SextantFullShape,
    colors = IconButtonDefaults.filledTonalIconButtonColors(
      containerColor = if (highlighted) SextantColors.AccentSoft else SextantColors.SurfaceContainer,
      contentColor = if (highlighted) SextantColors.AccentStrong else SextantColors.TextMuted,
    ),
    content = content,
  )
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
 *
 * 圆角同时夹住按下反馈：`clickable` 的指示层画在自己的节点上，而 `Surface` 的 `clip(shape)`
 * 排在它之后（更内层），只把背景和内容裁圆。所以这里要在 `clickable` 之前先 `clip` 一次，
 * 否则日期的灰色水波会是一个方角矩形，和圆角格子对不上。
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
  val shape = morphCorner(selected, SextantRadius.Large, SextantRadius.Medium)
  Surface(
    modifier = modifier
      .clip(shape)
      .semantics { this.selected = selected }
      .clickable(enabled = enabled, role = role, onClickLabel = onClickLabel, onClick = onClick),
    shape = shape,
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

/**
 * 表单里的「点开一个选择器」磁贴：左上角是字段名，下面是当前值加一个箭头。
 *
 * 记账面板的分类与来源用的就是它，周期支出编辑器继续复用——同一个 App 里「点开选一个」
 * 应该是同一个外观，否则用户要学两遍。原本它私有在 `QuickEntryDialog.kt`，出现第二个
 * 调用方后按职责提到共享控件。
 */
@Composable
fun SextantDetailTile(
  label: String,
  value: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  SextantSurface(
    modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    color = SextantColors.SurfaceContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SextantColors.TextSubtle)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
          maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(16.dp))
      }
    }
  }
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
