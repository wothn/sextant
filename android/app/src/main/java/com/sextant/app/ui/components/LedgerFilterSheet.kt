package com.sextant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionType
import com.sextant.app.data.dayRangeOf
import com.sextant.app.data.localMillisToUtcPickerMillis
import com.sextant.app.data.toRange
import com.sextant.app.data.utcPickerMillisToLocalStartOfDay
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.label
import com.sextant.app.ui.util.parseAmountCents
import com.sextant.app.ui.util.rangeLabel
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.util.Locale

/** 快捷时间范围。 */
private enum class RangePreset(val label: String) {
  ThisMonth("本月"),
  LastMonth("上月"),
  Last3Months("近3个月"),
  ThisYear("今年"),
  LastYear("去年"),
  ;

  fun range(): Pair<Long, Long> {
    val now = YearMonth.now()
    return when (this) {
      ThisMonth -> now.toRange()
      LastMonth -> now.minusMonths(1).toRange()
      Last3Months -> now.minusMonths(2).toRange().first to now.toRange().second
      ThisYear -> YearMonth.of(now.year, 1).toRange().first to YearMonth.of(now.year + 1, 1).toRange().first
      LastYear -> YearMonth.of(now.year - 1, 1).toRange().first to YearMonth.of(now.year, 1).toRange().first
    }
  }

  fun matches(filter: LedgerFilter): Boolean {
    val (start, end) = range()
    return filter.rangeStart == start && filter.rangeEnd == end
  }
}

private enum class DateField { Start, End }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerFilterSheet(
  state: AppState,
  filter: LedgerFilter,
  onApply: (LedgerFilter) -> Unit,
  onDismiss: () -> Unit,
) {
  var draft by remember(filter) { mutableStateOf(filter) }
  var queryText by remember(filter) { mutableStateOf(filter.query) }
  var minAmountText by remember(filter) { mutableStateOf(filter.minAmountCents.toAmountText()) }
  var maxAmountText by remember(filter) { mutableStateOf(filter.maxAmountCents.toAmountText()) }
  var picking by remember { mutableStateOf<DateField?>(null) }

  val typeCategories = state.categories.filter { draft.type == null || it.type == draft.type }
  val parentCategories = typeCategories.filter { it.parentCategoryId == null }
  val selectedCategory = typeCategories.firstOrNull { it.id == draft.categoryId }
  val selectedParentId = selectedCategory?.parentCategoryId ?: selectedCategory?.id

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("筛选流水", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = {
          draft = draft.cleared()
          queryText = ""
          minAmountText = ""
          maxAmountText = ""
        }) { Text("清除条件") }
      }

      FilterSection("时间范围 · 快捷") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          RangePreset.entries.forEach { preset ->
            FilterChip(
              selected = preset.matches(draft),
              onClick = {
                val (start, end) = preset.range()
                draft = draft.copy(rangeStart = start, rangeEnd = end)
              },
              label = { Text(preset.label) },
              colors = sextantFilterChipColors(),
            )
          }
        }
      }

      FilterSection("时间范围 · 自定义") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DateFieldTile(
            label = "开始日期",
            value = formatDay(draft.rangeStart),
            modifier = Modifier.weight(1f),
            onClick = { picking = DateField.Start },
          )
          DateFieldTile(
            label = "结束日期",
            value = formatDay((draft.rangeEnd - 1).coerceAtLeast(draft.rangeStart)),
            modifier = Modifier.weight(1f),
            onClick = { picking = DateField.End },
          )
        }
        Text("已选 ${draft.rangeLabel()}", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
      }

      FilterSection("备注关键词") {
        OutlinedTextField(
          value = queryText,
          onValueChange = {
            queryText = it
            draft = draft.copy(query = it)
          },
          label = { Text("搜索备注") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      FilterSection("收支类型") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = draft.type == null,
            onClick = { draft = draft.copy(type = null) },
            label = { Text("全部") },
            colors = sextantFilterChipColors(),
          )
          TransactionType.entries.forEach { type ->
            FilterChip(
              selected = draft.type == type,
              onClick = {
                val keepCategory = state.categories.any { it.id == draft.categoryId && it.type == type }
                draft = draft.copy(type = type, categoryId = draft.categoryId.takeIf { keepCategory })
              },
              label = { Text(type.label) },
              colors = sextantFilterChipColors(),
            )
          }
        }
      }

      FilterSection("分类") {
        FilterChip(
          selected = draft.categoryId == null,
          onClick = { draft = draft.copy(categoryId = null) },
          label = { Text("全部分类") },
          colors = sextantFilterChipColors(),
        )
        CategoryPicker(
          parentCategories = parentCategories,
          typeCategories = typeCategories,
          selectedParentId = selectedParentId,
          selectedCategoryId = draft.categoryId,
          onSelect = { draft = draft.copy(categoryId = it.id) },
        )
      }

      FilterSection("账单来源") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = draft.paymentMethodId == null,
            onClick = { draft = draft.copy(paymentMethodId = null) },
            label = { Text("全部来源") },
            colors = sextantFilterChipColors(),
          )
          state.paymentMethods.forEach { method ->
            FilterChip(
              selected = draft.paymentMethodId == method.id,
              onClick = { draft = draft.copy(paymentMethodId = method.id) },
              label = { Text(method.name) },
              colors = sextantFilterChipColors(),
            )
          }
        }
      }

      FilterSection("金额范围（元）") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          AmountField(
            label = "最低",
            value = minAmountText,
            modifier = Modifier.weight(1f),
          ) { text ->
            minAmountText = text.toAmountInput()
            draft = draft.copy(minAmountCents = parseAmountCents(minAmountText))
          }
          Text("—", color = SextantColors.TextSubtle)
          AmountField(
            label = "最高",
            value = maxAmountText,
            modifier = Modifier.weight(1f),
          ) { text ->
            maxAmountText = text.toAmountInput()
            draft = draft.copy(maxAmountCents = parseAmountCents(maxAmountText))
          }
        }
        val min = draft.minAmountCents
        val max = draft.maxAmountCents
        Text(
          text = when {
            min != null && max != null && min > max -> "最低金额比最高金额还大，将查不到记录"
            min != null && max != null -> "只看 ${formatAmountPlain(min)} - ${formatAmountPlain(max)} 元"
            min != null -> "只看 ${formatAmountPlain(min)} 元以上"
            max != null -> "只看 ${formatAmountPlain(max)} 元以下"
            else -> "留空表示不限金额"
          },
          color = if (min != null && max != null && min > max) SextantColors.Danger else SextantColors.TextSubtle,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      HorizontalDivider(color = SextantColors.Border)

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = {
            draft = LedgerFilter.currentMonth()
            queryText = ""
            minAmountText = ""
            maxAmountText = ""
          },
          modifier = Modifier.weight(1f),
        ) { Text("回到本月") }
        Button(
          onClick = { onApply(draft) },
          modifier = Modifier.weight(2f),
          colors = ButtonDefaults.buttonColors(containerColor = SextantColors.Accent, contentColor = SextantColors.OnAccent),
        ) { Text("查看结果") }
      }
    }
  }

  when (picking) {
    DateField.Start -> {
      val pickerState = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcPickerMillis(draft.rangeStart))
      DatePickerDialog(
        onDismissRequest = { picking = null },
        confirmButton = {
          TextButton(onClick = {
            pickerState.selectedDateMillis?.let { picked ->
              val dayStart = utcPickerMillisToLocalStartOfDay(picked)
              draft = draft.copy(
                rangeStart = dayStart,
                rangeEnd = maxOf(draft.rangeEnd, dayRangeOf(dayStart).second),
              )
            }
            picking = null
          }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = { picking = null }) { Text("取消") } },
      ) { DatePicker(state = pickerState) }
    }

    DateField.End -> {
      val pickerState = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcPickerMillis(draft.rangeEnd - 1))
      DatePickerDialog(
        onDismissRequest = { picking = null },
        confirmButton = {
          TextButton(onClick = {
            pickerState.selectedDateMillis?.let { picked ->
              val dayStart = utcPickerMillisToLocalStartOfDay(picked)
              draft = draft.copy(
                rangeStart = minOf(draft.rangeStart, dayStart),
                rangeEnd = dayRangeOf(dayStart).second,
              )
            }
            picking = null
          }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = { picking = null }) { Text("取消") } },
      ) { DatePicker(state = pickerState) }
    }

    null -> Unit
  }
}

@Composable
private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = SextantColors.TextMuted)
    content()
  }
}

@Composable
private fun DateFieldTile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(SextantColors.SurfaceAlt)
      .clickable(onClickLabel = "选择$label", onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(18.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelSmall)
      Text(value, color = SextantColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun AmountField(label: String, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = modifier,
  )
}

private fun Long?.toAmountText(): String = this?.let { "%.2f".format(Locale.CHINA, it / 100.0) }.orEmpty()

private fun String.toAmountInput(): String {
  val filtered = filter { it.isDigit() || it == '.' }
  val firstDot = filtered.indexOf('.')
  if (firstDot < 0) return filtered
  return filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).filter { it.isDigit() }
}

private fun formatDay(millis: Long): String = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(millis)
