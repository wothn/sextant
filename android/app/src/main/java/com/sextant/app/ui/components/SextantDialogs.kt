@file:OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalFoundationApi::class,
  ExperimentalMaterial3ExpressiveApi::class,
)

package com.sextant.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantSheetShape

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
    // 顶部安全区固定在面板外，避免拖动时内容高度随 top inset 改变，反复重建拖拽锚点。
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
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
fun SextantDatePicker(state: DatePickerState, modifier: Modifier = Modifier, showModeToggle: Boolean = true) {
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

