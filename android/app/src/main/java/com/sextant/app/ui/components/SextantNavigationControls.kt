@file:OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalFoundationApi::class,
  ExperimentalMaterial3ExpressiveApi::class,
)

package com.sextant.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.sextant.app.ui.theme.sextantEmphasized
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantFullShape
import com.sextant.app.ui.theme.SextantMotion

// ---------------------------------------------------------------------------
// 浮动操作按钮
// ---------------------------------------------------------------------------

/**
 * 浮动操作按钮：48dp 正圆、铜橙填充。
 *
 * 用 [SextantFullShape] 而不是 [MaterialTheme.shapes] 的一档：正方形的 FAB 取胶囊圆角就是
 * 正圆，形状本身说明「这是主操作」，与同样全圆角的按钮、chip 一致。
 *
 * Sextant 将记账主操作放在独立 FAB 中，让入口易于触达。
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
    modifier = modifier.size(48.dp),
    shape = SextantFullShape,
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
 * 铺了记账 FAB 的列表（目前只有概览）底部要再预留的空白：48dp 按钮 + 16dp 下边距 + 16dp 呼吸。
 *
 * 起算点是**底栏上沿**，所以页面把它和 [LocalBottomBarInset] 一起加进 `contentPadding.bottom`：
 * 再加上底栏那一份，最后一行就不会停在 FAB 底下——明细的金额正在右边，也正是 FAB 待的位置。
 * 没有 FAB 的页面只加 [LocalBottomBarInset]。
 */
val SextantFabClearance: Dp = 80.dp

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
 * 内容视口底部到底栏上沿的距离。
 *
 * 底栏是悬浮在内容之上的：内容从它下方流过，被盖住的只有条本身和两条留白，两侧和上方的
 * 空隙都能看到明细在滚。代价是滚动内容要自己预留结尾——页面把这个值加到 `LazyColumn`
 * 的 `contentPadding.bottom`（概览还要再加上 [SextantFabClearance]），最后一项才能完整
 * 滚到栏上方，而不是停在栏底下。
 *
 * 值由 `SextantApp` 用 `Scaffold` 量到的底栏高度推出，所以字体放大、底栏变高时页面
 * 的预留会跟着变；日历页没有底栏，那里是 0。
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** 悬浮胶囊底栏；选中底块同时包住图标与标签。 */
@Composable
fun SextantNavigationBar(
  selectedIndex: Int,
  itemCount: Int,
  modifier: Modifier = Modifier,
  containerColor: Color = SextantColors.SurfaceContainerLow,
  content: @Composable RowScope.() -> Unit,
) {
  Surface(
    modifier = modifier,
    color = containerColor,
    contentColor = SextantColors.Text,
    shape = SextantFullShape,
    tonalElevation = 0.dp,
    shadowElevation = 2.dp,
  ) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
      val slotWidth = maxWidth / itemCount.coerceAtLeast(1)
      val lastIndex = (itemCount - 1).coerceAtLeast(0)
      val indicatorOffset by animateDpAsState(
        targetValue = slotWidth * selectedIndex.coerceIn(0, lastIndex),
        animationSpec = SextantMotion.fastSpatial(),
        label = "navIndicatorOffset",
      )
      val density = LocalDensity.current
      // One persistent indicator moves behind the items; it never fades out on release.
      Box(Modifier.matchParentSize()) {
        Box(
          Modifier
            .offset {
              IntOffset(with(density) {
                indicatorOffset.coerceIn(0.dp, slotWidth * lastIndex).toPx().roundToInt()
              }, 0)
            }
            .width(slotWidth)
            .fillMaxHeight()
            .background(SextantColors.AccentSoft, SextantFullShape),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
      )
    }
  }
}

/** 选中底块由底栏统一移动；按下只轻微缩放内容，键盘聚焦用描边表达。 */
@Composable
fun RowScope.SextantNavigationItem(
  selected: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val focused by interactionSource.collectIsFocusedAsState()
  val contentScale by animateFloatAsState(
    targetValue = if (pressed) 0.96f else 1f,
    animationSpec = SextantMotion.fastEffects(),
    label = "navContentScale",
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) SextantColors.AccentStrong else SextantColors.TextSubtle,
    animationSpec = SextantMotion.defaultEffects(),
    label = "navContentColor",
  )
  Box(
    modifier = modifier
      .weight(1f)
      .heightIn(min = 48.dp)
      .clip(SextantFullShape)
      .selectable(
        selected = selected,
        interactionSource = interactionSource,
        indication = null,
        role = Role.Tab,
        onClick = onClick,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(SextantFullShape)
        .then(if (focused) Modifier.border(1.dp, SextantColors.Accent, SextantFullShape) else Modifier)
        .heightIn(min = 48.dp)
        .padding(horizontal = 4.dp, vertical = 2.dp)
        .graphicsLayer { scaleX = contentScale; scaleY = contentScale },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
      CompositionLocalProvider(LocalContentColor provides contentColor) {
        icon()
        label()
      }
    }
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
 * 展开高度跟着内容走（见 [expandedHeight] 的默认值）：没有副标题的页面不会为一行不存在的
 * 文字留出额外空气，而有副标题的页面也不会被压扁。两种情况下标题行都在离头部顶端 12dp 处，
 * 所以在四个主页面之间切换时标题不会上下跳。
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
  // 一行标题 48dp（动作按钮撑起来的行高）+ 上下 6dp 内边距 + 每侧 6dp 呼吸 = 72dp；
  // 有副标题时再加一行 bodyMedium 的 18dp = 90dp。收起高度固定 60dp，正好是
  // 「48dp 标题行 + 12dp 内边距」，也就是没有空气的一行标题。
  expandedHeight: Dp = if (subtitle == null) 72.dp else 90.dp,
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
    MaterialTheme.typography.headlineMedium.sextantEmphasized(),
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
// 一级切换
// ---------------------------------------------------------------------------

/** 页面内一级切换：选中用下划线，按下加宽，聚焦时也显示指示条。 */
@Composable
fun <T> SextantTabRow(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  label: (T) -> String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().selectableGroup(),
    verticalAlignment = Alignment.Bottom,
  ) {
    options.forEach { option ->
      SextantTabItem(
        selected = option == selected,
        onClick = { onSelect(option) },
        label = label(option),
      )
    }
  }
}

/**
 * 一级切换条目。
 *
 * 指示条宽度从 0 长到 24dp，所以未选中时它完全消失、不留下一条空槽位；条目高度至少 48dp，
 * 即使指示条淡出，点击区域也不缩水。
 */
@Composable
private fun RowScope.SextantTabItem(
  selected: Boolean,
  onClick: () -> Unit,
  label: String,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val focused by interactionSource.collectIsFocusedAsState()
  val indicatorWidth by animateDpAsState(
    targetValue = if (pressed) 32.dp else if (selected || focused) 24.dp else 0.dp,
    animationSpec = SextantMotion.defaultSpatial(),
    label = "tabIndicatorWidth",
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) SextantColors.AccentStrong else SextantColors.TextSubtle,
    animationSpec = SextantMotion.defaultEffects(),
    label = "tabContentColor",
  )
  Column(
    modifier = Modifier
      .weight(1f)
      .heightIn(min = 48.dp)
      .selectable(
        selected = selected,
        interactionSource = interactionSource,
        indication = null,
        role = Role.Tab,
        onClick = onClick,
      ),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      color = contentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Box(
      modifier = Modifier
        .width(indicatorWidth)
        .height(3.dp)
        .clip(SextantFullShape)
        .background(SextantColors.Accent),
    )
  }
}

