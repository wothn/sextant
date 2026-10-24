package com.sextant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.key
import com.sextant.app.data.Category
import com.sextant.app.ui.util.displayName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.ui.components.SextantAlertDialog
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
  AllTime("全部时间"),
  ThisMonth("本月"),
  LastMonth("上月"),
  ThisYear("今年"),
  LastYear("去年"),
  ;

  fun range(): Pair<Long, Long> {
    val now = YearMonth.now()
    return when (this) {
      AllTime -> Long.MIN_VALUE to Long.MAX_VALUE
      ThisMonth -> now.toRange()
      LastMonth -> now.minusMonths(1).toRange()
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
  var draft by remember { mutableStateOf(filter) }
  var queryText by remember { mutableStateOf(filter.query) }
  var minAmountText by remember { mutableStateOf(filter.minAmountCents.toAmountText()) }
  var maxAmountText by remember { mutableStateOf(filter.maxAmountCents.toAmountText()) }
  var picking by remember { mutableStateOf<DateField?>(null) }
  var choosingCategory by remember { mutableStateOf(false) }
  var showDiscard by remember { mutableStateOf(false) }
  val dirty = draft != filter
  val requestDismiss = {
    if (dirty) showDiscard = true else onDismiss()
  }
  val minAmount = draft.minAmountCents
  val maxAmount = draft.maxAmountCents
  val amountRangeInvalid = minAmount != null && maxAmount != null && minAmount > maxAmount

  val focus = LocalFocusManager.current
  val scrollState = rememberScrollState()
  val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

  val typeCategories = state.categories.filter { draft.type == null || it.type == draft.type }
  val selectedCategory = typeCategories.firstOrNull { it.id == draft.categoryId }
  val selectedMethod = state.paymentMethods.firstOrNull { it.id == draft.paymentMethodId }

  fun resetConditions() {
    draft = draft.cleared()
    queryText = ""
    minAmountText = ""
    maxAmountText = ""
  }

  SextantBottomSheet(
    onDismissRequest = requestDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    if (choosingCategory) {
      FilterCategoryPage(
        categories = typeCategories,
        selectedId = draft.categoryId,
        modifier = Modifier.fillMaxWidth().height(sheetHeight * 0.78f),
        onBack = { choosingCategory = false },
        onSelect = { id ->
          draft = draft.copy(categoryId = id)
          choosingCategory = false
        },
      )
    } else {
      Column(Modifier.fillMaxWidth().heightIn(max = sheetHeight)) {
        // 标题固定，长条件列表滚动时始终可见。
        Row(
          Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SextantHeading("筛选流水", modifier = Modifier.weight(1f))
          SextantIconButton(onClick = requestDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭筛选")
          }
          SextantTextButton(onClick = { resetConditions() }) { Text("清除条件") }
        }

        // 已启用的条件即时可见，点一下就能单独清除，不用滚回对应分区。
        ActiveFilterChips(
          draft = draft,
          categoryName = selectedCategory?.name,
          methodName = selectedMethod?.name,
          onClearRange = {
            val current = LedgerFilter.allTime()
            draft = draft.copy(rangeStart = current.rangeStart, rangeEnd = current.rangeEnd)
          },
          onClearQuery = {
            queryText = ""
            draft = draft.copy(query = "")
          },
          onClearType = {
            val keepCategory = state.categories.any { it.id == draft.categoryId && it.type == draft.type }
            draft = draft.copy(type = null, categoryId = draft.categoryId.takeIf { keepCategory })
          },
          onClearCategory = {
            draft = draft.copy(categoryId = null)
          },
          onClearMethod = { draft = draft.copy(paymentMethodId = null) },
          onClearAmount = {
            minAmountText = ""
            maxAmountText = ""
            draft = draft.copy(minAmountCents = null, maxAmountCents = null)
          },
        )

        // 只有条件区滚动，底部按钮始终固定在弹窗底部，不用把内容全部滑到底。
        Column(
          Modifier
            .weight(1f, fill = false)
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          FilterSection("时间范围") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              RangePreset.entries.forEach { preset ->
                SextantFilterChip(
                  selected = preset.matches(draft),
                  onClick = {
                    val (start, end) = preset.range()
                    draft = draft.copy(rangeStart = start, rangeEnd = end)
                  },
                  label = { Text(preset.label) },
                )
              }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              DateFieldTile(
                label = "开始日期",
                value = if (draft.isAllTime) "不限" else formatDay(draft.rangeStart),
                modifier = Modifier.weight(1f),
                onClick = { picking = DateField.Start },
              )
              DateFieldTile(
                label = "结束日期",
                value = if (draft.isAllTime) "不限" else formatDay((draft.rangeEnd - 1).coerceAtLeast(draft.rangeStart)),
                modifier = Modifier.weight(1f),
                onClick = { picking = DateField.End },
              )
            }
          }

          Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "选择分类") {
              focus.clearFocus()
              choosingCategory = true
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("分类", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = SextantColors.TextMuted)
            Text(
              selectedCategory?.displayName(state.categories) ?: "全部分类",
              modifier = Modifier.weight(2f), textAlign = TextAlign.End,
              color = SextantColors.AccentStrong, style = MaterialTheme.typography.bodyMedium,
              maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ChevronRight, null, tint = SextantColors.TextSubtle, modifier = Modifier.size(18.dp))
          }

          FilterSection("收支类型") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SextantFilterChip(
                selected = draft.type == null,
                onClick = { draft = draft.copy(type = null) },
                label = { Text("全部") },
              )
              TransactionType.entries.forEach { type ->
                SextantFilterChip(
                  selected = draft.type == type,
                  onClick = {
                    val keepCategory = state.categories.any { it.id == draft.categoryId && it.type == type }
                    draft = draft.copy(type = type, categoryId = draft.categoryId.takeIf { keepCategory })
                  },
                  label = { Text(type.label) },
                )
              }
            }
          }

          FilterSection("账单来源") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SextantFilterChip(
                selected = draft.paymentMethodId == null,
                onClick = { draft = draft.copy(paymentMethodId = null) },
                label = { Text("全部来源") },
              )
              state.paymentMethods.forEach { method ->
                SextantFilterChip(
                  selected = draft.paymentMethodId == method.id,
                  onClick = { draft = draft.copy(paymentMethodId = method.id) },
                  label = { Text(method.name) },
                )
              }
            }
          }

          FilterSection("备注关键词") {
            SextantTextField(
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
            val min = minAmount
            val max = maxAmount
            Text(
              text = when {
                min != null && max != null && min > max -> "最低金额比最高金额还大，将查不到记录"
                min != null && max != null -> "只看 ${formatAmountPlain(min)} - ${formatAmountPlain(max)} 元"
                min != null -> "只看 ${formatAmountPlain(min)} 元以上"
                max != null -> "只看 ${formatAmountPlain(max)} 元以下"
                else -> "留空表示不限金额"
              },
              color = if (amountRangeInvalid) SextantColors.Danger else SextantColors.TextSubtle,
              style = MaterialTheme.typography.bodySmall,
            )
          }

          Spacer(Modifier.height(2.dp))
        }

        SextantDivider(color = SextantColors.SurfaceContainer)

        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SextantOutlinedButton(onClick = requestDismiss,
            modifier = Modifier.weight(1f),
          ) { Text("取消") }
          SextantButton(onClick = { onApply(draft) },
            enabled = !amountRangeInvalid,
            modifier = Modifier.weight(1.6f),
          ) { Text("查看结果") }
        }
      }
    }
  }

  if (showDiscard) {
    SextantAlertDialog(
      onDismissRequest = { showDiscard = false },
      title = { Text("放弃筛选条件？") },
      text = { Text("刚才的修改还没有应用。") },
      confirmButton = {
        SextantTextButton(
          onClick = {
            showDiscard = false
            onDismiss()
          },
          contentColor = SextantColors.Danger,
        ) { Text("放弃") }
      },
      dismissButton = { SextantTextButton(onClick = { showDiscard = false }) { Text("继续编辑") } },
    )
  }

  when (picking) {
    DateField.Start -> {
      val pickerState = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcPickerMillis(if (draft.isAllTime) System.currentTimeMillis() else draft.rangeStart))
      SextantDatePickerDialog(
        onDismissRequest = { picking = null },
        confirmButton = {
          SextantTextButton(onClick = {
            pickerState.selectedDateMillis?.let { picked ->
              val dayStart = utcPickerMillisToLocalStartOfDay(picked)
              draft = draft.copy(
                rangeStart = dayStart,
                rangeEnd = if (draft.isAllTime) dayRangeOf(maxOf(dayStart, System.currentTimeMillis())).second else maxOf(draft.rangeEnd, dayRangeOf(dayStart).second),
              )
            }
            picking = null
          }) { Text("确定") }
        },
        dismissButton = { SextantTextButton(onClick = { picking = null }) { Text("取消") } },
      ) { SextantDatePicker(state = pickerState) }
    }

    DateField.End -> {
      val pickerState = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcPickerMillis(if (draft.isAllTime) System.currentTimeMillis() else draft.rangeEnd - 1))
      SextantDatePickerDialog(
        onDismissRequest = { picking = null },
        confirmButton = {
          SextantTextButton(onClick = {
            pickerState.selectedDateMillis?.let { picked ->
              val dayStart = utcPickerMillisToLocalStartOfDay(picked)
              draft = draft.copy(
                rangeStart = if (draft.isAllTime) dayRangeOf(dayStart).first else minOf(draft.rangeStart, dayStart),
                rangeEnd = dayRangeOf(dayStart).second,
              )
            }
            picking = null
          }) { Text("确定") }
        },
        dismissButton = { SextantTextButton(onClick = { picking = null }) { Text("取消") } },
      ) { SextantDatePicker(state = pickerState) }
    }

    null -> Unit
  }
}

/** 已启用的条件。点击即可单独清除。 */
@Composable
private fun ActiveFilterChips(
  draft: LedgerFilter,
  categoryName: String?,
  methodName: String?,
  onClearRange: () -> Unit,
  onClearQuery: () -> Unit,
  onClearType: () -> Unit,
  onClearCategory: () -> Unit,
  onClearMethod: () -> Unit,
  onClearAmount: () -> Unit,
) {
  val isDefaultRange = draft.isAllTime
  val hasAmount = draft.minAmountCents != null || draft.maxAmountCents != null

  if (!draft.hasConditions && isDefaultRange) {
    return
  }

  FlowRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (!isDefaultRange) {
      ActiveFilterChip(label = draft.rangeLabel(), onClick = onClearRange)
    }
    if (draft.type != null) {
      ActiveFilterChip(label = draft.type.label, onClick = onClearType)
    }
    if (draft.categoryId != null) {
      ActiveFilterChip(label = categoryName ?: "已选分类", onClick = onClearCategory)
    }
    if (draft.paymentMethodId != null) {
      ActiveFilterChip(label = methodName ?: "已选来源", onClick = onClearMethod)
    }
    if (hasAmount) {
      ActiveFilterChip(label = amountLabel(draft), onClick = onClearAmount)
    }
    if (draft.query.isNotBlank()) {
      ActiveFilterChip(label = "备注：${draft.query.trim()}", onClick = onClearQuery)
    }
  }
}

@Composable
private fun ActiveFilterChip(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .heightIn(min = 48.dp)
      .clip(SextantChipShape)
      .background(SextantColors.AccentSoft)
      .clickable(onClickLabel = "清除条件 $label", onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      fontWeight = FontWeight.SemiBold,
      color = SextantColors.AccentStrong,
    )
    Icon(
      Icons.Outlined.Close,
      contentDescription = null,
      tint = SextantColors.AccentStrong,
      modifier = Modifier.size(13.dp),
    )
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
private fun FilterCategoryPage(
  categories: List<Category>,
  selectedId: String?,
  modifier: Modifier,
  onBack: () -> Unit,
  onSelect: (String?) -> Unit,
) {
  BackHandler { onBack() }
  var browsingParentId by rememberSaveable { mutableStateOf<String?>(null) }
  Column(modifier.navigationBarsPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      SextantIconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回筛选")
      }
      SextantHeading("选择分类", modifier = Modifier.weight(1f))
      SextantTextButton(onClick = onBack) { Text("取消") }
    }
    Text("选择后返回筛选，点击查看结果生效", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
      style = MaterialTheme.typography.bodySmall, color = SextantColors.TextSubtle)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
      CategoryBrowser(categories = categories, parentId = browsingParentId,
        onParentChange = { browsingParentId = it },
        selectedId = selectedId, allowAll = true,
        onSelect = { onSelect(it?.id) })
    }
  }
}
@Composable
private fun DateFieldTile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
  Row(
    modifier = modifier
      .clip(MaterialTheme.shapes.large)
      .background(SextantColors.SurfaceContainer)
      .clickable(onClickLabel = "选择$label", onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(18.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelSmall)
      Text(value, color = SextantColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun AmountField(label: String, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
  SextantTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = modifier,
  )
}

/** 金额条件的简短描述，用在已选条件标签上。 */
private fun amountLabel(filter: LedgerFilter): String {
  val min = filter.minAmountCents
  val max = filter.maxAmountCents
  return when {
    min != null && max != null -> "${formatAmountPlain(min)}-${formatAmountPlain(max)} 元"
    min != null -> "${formatAmountPlain(min)} 元以上"
    max != null -> "${formatAmountPlain(max)} 元以下"
    else -> "不限金额"
  }
}

private fun Long?.toAmountText(): String = this?.let { "%.2f".format(Locale.CHINA, it / 100.0) }.orEmpty()

private fun String.toAmountInput(): String {
  val filtered = filter { it.isDigit() || it == '.' }
  val firstDot = filtered.indexOf('.')
  if (firstDot < 0) return filtered
  return filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).filter { it.isDigit() }
}

private fun formatDay(millis: Long): String = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(millis)
