package com.sextant.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.Category
import com.sextant.app.data.PaymentMethod
import com.sextant.app.ui.theme.sextantEmphasized
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantFullShape

/**
 * 跨页面复用的展示型组件。
 *
 * 这里只放「没有交互状态」的东西——有空状态、区块标题、色点、chip 选择器。
 * 带交互的控件（按钮、输入框、可选项、面板）在 `SextantControls.kt`，
 * 两者共用同一套 token，但职责分开。
 */

/** 空状态：凹槽色底，不再描边——空状态本来就是「没有内容」，不该比有内容时更抢眼。 */
@Composable
fun EmptyCard(title: String, description: String) {
  SextantSurface(color = SextantColors.SurfaceContainer) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = SextantColors.Text)
      Text(description, style = MaterialTheme.typography.bodyMedium, color = SextantColors.TextMuted)
    }
  }
}

/**
 * 页面级标题：六分仪标记 + headlineMedium。
 *
 * 不带滚动收起的页面（设置子页、自动记账、分类管理）用它；主页面用
 * [SextantFlexibleHeader]，两者字号与标记一致，切换页面时标题不会跳。
 */
@Composable
fun SectionTitle(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    SextantMark()
    Text(text, style = MaterialTheme.typography.headlineMedium.sextantEmphasized(), color = SextantColors.Text)
  }
}

/**
 * 页面内区块标题（明细 / 分类预算 / 筛选流水 / 面板标题）。
 *
 * 字重来自主题的 titleLarge，调用处不再叠加 fontWeight。
 */
@Composable
fun SextantHeading(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    modifier = modifier,
    style = MaterialTheme.typography.titleLarge,
    color = SextantColors.Text,
  )
}

/** 汇总指标：标签与数值共用排版层级，核心金额由调用方选择 headlineLarge。 */
@Composable
fun SextantMetric(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueColor: Color = SextantColors.Text,
  valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
  primary: Boolean = false,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      label,
      color = SextantColors.TextSubtle,
      style = if (primary) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
    )
    if (primary) {
      BoxWithConstraints {
        val measurer = rememberTextMeasurer()
        val minFontSize = MaterialTheme.typography.titleLarge.fontSize
        val maxWidthPx = constraints.maxWidth
        // 核心金额优先保持完整一行；最低保留次级金额字号，更长时允许完整换行。
        val fittedStyle = remember(value, valueStyle, minFontSize, maxWidthPx, measurer) {
          var style = valueStyle
          while (style.fontSize > minFontSize &&
            measurer.measure(value, style = style, softWrap = false).size.width > maxWidthPx) {
            val size = (style.fontSize.value - 1f).coerceAtLeast(minFontSize.value)
            style = valueStyle.copy(
              fontSize = size.sp,
              lineHeight = (valueStyle.lineHeight.value * size / valueStyle.fontSize.value).sp,
            )
          }
          style
        }
        Text(value, style = fittedStyle, color = valueColor)
      }
    } else {
      Text(value, style = valueStyle, color = valueColor)
    }
  }
}

/** 小计数徽标，用在筛选入口这类「有条件的数量」提示上。 */
@Composable
fun SextantBadge(count: Int, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.sizeIn(minWidth = 18.dp, minHeight = 18.dp)
      .clip(SextantFullShape).background(SextantColors.Accent).padding(horizontal = 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = count.toString(),
      color = SextantColors.OnAccent,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
    )
  }
}

@Composable
fun ColorDot(color: Color, size: Dp = 8.dp) {
  Canvas(Modifier.size(size)) {
    drawCircle(color)
  }
}

/**
 * 可换行的 chip 选择器，用于分类颜色、来源颜色和上级分类等紧凑选择。
 *
 * [icon] 未提供时前导元素是颜色圆点，适合「挑一个颜色」；提供时改为分类图标底块，
 * 适合「挑一个分类」——分类在两处必须长得一样，只有纯颜色选择才用圆点。
 */
@Composable
fun <T> FlowChips(
  items: List<T>,
  selectedId: String?,
  label: (T) -> String,
  color: @Composable (T) -> Color,
  modifier: Modifier = Modifier,
  icon: ((T) -> ImageVector)? = null,
  onSelect: (T) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    items.forEach { item ->
      val id = when (item) {
        is Category -> item.id
        is PaymentMethod -> item.id
        is Pair<*, *> -> item.first.toString()
        null -> null
        else -> ""
      }
      val isSelected = id == selectedId
      val itemColor = color(item)
      SextantFilterChip(
        selected = isSelected,
        onClick = { onSelect(item) },
        label = { Text(label(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { FlowChipLeading(itemColor, icon?.invoke(item)) },
        showSelectionCheck = icon != null,
      )
    }
  }
}

@Composable
private fun FlowChipLeading(color: Color, icon: ImageVector?) {
  if (icon == null) {
    ColorDot(color, size = 16.dp)
  } else {
    CategoryIcon(icon, color, size = 20.dp, iconSize = 12.dp)
  }
}

/** 胶囊形状。chip、已选条件标签这类小标签共用。 */
val SextantChipShape: Shape = SextantFullShape

/** 管理子页的固定标题栏，标题可随字体缩放换行。 */
@Composable
fun SextantManagementHeader(
  title: String,
  backDescription: String,
  onNavigateBack: () -> Unit,
  actionLabel: String,
  onAction: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SextantIconButton(onClick = onNavigateBack) {
      Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backDescription)
    }
    SextantMark()
    Text(title, modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.headlineMedium.sextantEmphasized(),
      maxLines = 2, overflow = TextOverflow.Ellipsis)
    SextantTextButton(onClick = onAction) { Text(actionLabel) }
  }
}
