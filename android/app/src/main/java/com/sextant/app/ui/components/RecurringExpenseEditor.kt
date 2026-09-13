package com.sextant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.RecurringCycle
import com.sextant.app.data.RecurringExpense
import com.sextant.app.data.RecurringExpenseDraft
import com.sextant.app.data.TransactionType
import com.sextant.app.data.localMillisToUtcPickerMillis
import com.sextant.app.data.toStartOfDayMillis
import com.sextant.app.data.utcPickerMillisToLocalStartOfDay
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayName
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.label
import com.sextant.app.ui.util.parseAmountCents
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale

private enum class RecurringPanel { Form, Category, Source }

private enum class DateTarget { Anchor, Ended }

/**
 * 周期支出的新增 / 编辑面板。
 *
 * 结构照抄记账面板：只有 body 滚动，主操作固定在底部；分类与来源各自打开一个子面板，
 * 避免把两级选择硬塞进一个长表单。表单里不放「下一次扣费」这类派生结果——它由
 * `RecurringSchedule.kt` 推出来，编辑过程中反复变，展示在列表上更稳。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecurringExpenseEditor(
  state: AppState,
  editing: RecurringExpense? = null,
  onDismiss: () -> Unit,
  onDelete: (() -> Unit)? = null,
  onSubmit: (RecurringExpenseDraft) -> Unit,
) {
  var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
  var amountText by remember(editing) { mutableStateOf(editing?.amountCents?.let(::formatAmountPlain).orEmpty()) }
  var cycle by remember(editing) { mutableStateOf(editing?.cycle ?: RecurringCycle.Monthly) }
  var customDaysText by remember(editing) { mutableStateOf(editing?.customDays?.toString().orEmpty()) }
  var anchorDate by remember(editing) { mutableStateOf(editing?.anchorDate ?: todayStart()) }
  var endedAt by remember(editing) { mutableStateOf(editing?.endedAt) }
  var categoryId by remember(editing) {
    mutableStateOf(editing?.categoryId ?: state.categories.firstOrNull { it.type == TransactionType.Expense }?.id)
  }
  var paymentMethodId by remember(editing) { mutableStateOf(editing?.paymentMethodId ?: state.paymentMethods.firstOrNull()?.id) }
  var note by remember(editing) { mutableStateOf(editing?.note.orEmpty()) }
  var isActive by remember(editing) { mutableStateOf(editing?.isActive ?: true) }
  var panel by remember { mutableStateOf(RecurringPanel.Form) }
  var dateTarget by remember { mutableStateOf<DateTarget?>(null) }

  val expenseCategories = state.categories.filter { it.type == TransactionType.Expense }
  val selectedCategory = expenseCategories.firstOrNull { it.id == categoryId }
  val paymentMethodOptions = state.paymentMethods.toMutableList().apply {
    val inactive = editing?.paymentMethodId?.let { id -> state.allPaymentMethods.firstOrNull { it.id == id && !it.isActive } }
    if (inactive != null) add(inactive)
  }
  val selectedMethod = paymentMethodOptions.firstOrNull { it.id == paymentMethodId }

  val parsedAmount = parseAmountCents(amountText)
  val parsedCustomDays = customDaysText.trim().toIntOrNull()
  val customDaysValid = cycle != RecurringCycle.CustomDays || (parsedCustomDays != null && parsedCustomDays > 0)
  val canSubmit = name.trim().isNotEmpty() && parsedAmount != null && customDaysValid

  SextantBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetGesturesEnabled = panel == RecurringPanel.Form,
  ) {
    val focus = LocalFocusManager.current
    BackHandler(enabled = panel != RecurringPanel.Form) { panel = RecurringPanel.Form }
    val panelHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Column(
      Modifier
        .fillMaxWidth()
        .then(if (panel == RecurringPanel.Form) Modifier.heightIn(max = panelHeight) else Modifier.height(panelHeight))
        .padding(horizontal = 24.dp),
    ) {
      Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (panel != RecurringPanel.Form) {
          SextantIconButton(onClick = { panel = RecurringPanel.Form }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回周期支出")
          }
        }
        SextantHeading(
          when (panel) {
            RecurringPanel.Form -> if (editing == null) "添加周期支出" else "编辑周期支出"
            RecurringPanel.Category -> "选择分类"
            RecurringPanel.Source -> "选择来源"
          },
          modifier = Modifier.weight(1f),
        )
        if (panel == RecurringPanel.Form && onDelete != null) {
          SextantTextButton(onClick = onDelete) { Text("删除", color = SextantColors.Danger) }
        }
        SextantIconButton(onClick = { if (panel == RecurringPanel.Form) onDismiss() else panel = RecurringPanel.Form }) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = if (panel == RecurringPanel.Form) "关闭周期支出" else "关闭选择",
            modifier = Modifier.size(20.dp),
          )
        }
      }
      key(panel) {
        Column(
          Modifier.weight(1f, fill = panel != RecurringPanel.Form).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          when (panel) {
            RecurringPanel.Form -> {
              SextantTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("房租、视频会员…") },
                isError = name.isNotEmpty() && name.isBlank(),
                supportingText = { Text("名称用于在列表里区分同一分类下的多条支出") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              SextantTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("每期金额") },
                isError = amountText.isNotEmpty() && parsedAmount == null,
                supportingText = { Text("请输入最多两位小数的有效金额") },
                prefix = { Text("¥ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
              )

              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("扣费周期", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  RecurringCycle.entries.forEach { option ->
                    SextantFilterChip(
                      selected = option == cycle,
                      onClick = { cycle = option },
                      label = { Text(option.label(parsedCustomDays)) },
                    )
                  }
                }
              }
              if (cycle == RecurringCycle.CustomDays) {
                SextantTextField(
                  value = customDaysText,
                  onValueChange = { customDaysText = it.filter(Char::isDigit) },
                  label = { Text("间隔天数") },
                  isError = customDaysText.isNotEmpty() && !customDaysValid,
                  supportingText = { Text("例如 45 表示每 45 天扣一次") },
                  suffix = { Text("天") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.fillMaxWidth(),
                )
              }

              SextantDetailTile(
                label = "首次扣费日",
                value = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(anchorDate),
                icon = Icons.Outlined.CalendarToday,
                modifier = Modifier.fillMaxWidth(),
              ) {
                focus.clearFocus()
                dateTarget = DateTarget.Anchor
              }
              Text(
                "之后的每一期都从这一天推算：1 月 31 日的月度支出，2 月落在月末而不是 3 月 3 日。",
                color = SextantColors.TextSubtle,
                style = MaterialTheme.typography.bodySmall,
              )

              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SextantDetailTile(
                  "分类",
                  selectedCategory?.displayName(state.categories) ?: "选择分类",
                  categoryIcon(selectedCategory?.name.orEmpty()),
                  Modifier.weight(1f),
                ) {
                  focus.clearFocus()
                  panel = RecurringPanel.Category
                }
                SextantDetailTile(
                  "来源",
                  selectedMethod?.name ?: "选择来源",
                  Icons.Outlined.AccountBalanceWallet,
                  Modifier.weight(1f),
                ) {
                  focus.clearFocus()
                  panel = RecurringPanel.Source
                }
              }

              SextantDetailTile(
                label = "停止日期",
                value = endedAt?.let { SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(it) } ?: "一直继续",
                icon = Icons.Outlined.CalendarToday,
                modifier = Modifier.fillMaxWidth(),
              ) {
                focus.clearFocus()
                dateTarget = DateTarget.Ended
              }
              if (endedAt != null) {
                SextantTextButton(onClick = { endedAt = null }) { Text("清除停止日期") }
              }

              SextantTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text("仍在扣费", fontWeight = FontWeight.Medium)
                  Text("关掉后保留记录，但不再计入每月固定支出", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
                }
                SextantSwitch(checked = isActive, onCheckedChange = { isActive = it })
              }
            }

            RecurringPanel.Category -> {
              Text("这笔周期支出记入哪个分类", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodyMedium)
              CategoryBrowser(
                categories = expenseCategories,
                selectedId = categoryId,
                onSelect = { category ->
                  categoryId = category?.id
                  panel = RecurringPanel.Form
                },
              )
            }

            RecurringPanel.Source -> {
              Text("这笔周期支出从哪个来源扣", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodyMedium)
              paymentMethodOptions.forEach { method ->
                val selected = method.id == paymentMethodId
                SextantSelectableSurface(
                  selected = selected,
                  onClick = { paymentMethodId = method.id; panel = RecurringPanel.Form },
                  role = Role.RadioButton,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CategoryIcon(Icons.Outlined.AccountBalanceWallet, argbColor(method.color))
                    Text(method.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = "已选择", tint = SextantColors.AccentStrong, modifier = Modifier.size(20.dp))
                  }
                }
              }
              if (paymentMethodOptions.isEmpty()) Text("暂无可选来源", color = SextantColors.TextMuted)
            }
          }
          Spacer(Modifier.height(4.dp))
        }
      }
      SextantButton(
        onClick = {
          if (panel != RecurringPanel.Form) {
            panel = RecurringPanel.Form
          } else {
            parsedAmount?.let { amount ->
              onSubmit(
                RecurringExpenseDraft(
                  name = name.trim(),
                  amountCents = amount,
                  cycle = cycle,
                  customDays = if (cycle == RecurringCycle.CustomDays) parsedCustomDays else null,
                  anchorDate = anchorDate,
                  categoryId = categoryId,
                  paymentMethodId = paymentMethodId,
                  endedAt = endedAt,
                  isActive = isActive,
                  note = note.trim(),
                ),
              )
            }
          }
        },
        size = SextantButtonSize.Medium,
        enabled = panel != RecurringPanel.Form || canSubmit,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
      ) {
        Text(
          if (panel != RecurringPanel.Form) "返回周期支出" else if (editing == null) "添加" else "保存修改",
          style = MaterialTheme.typography.titleSmall,
        )
      }
    }
  }

  dateTarget?.let { target ->
    val current = if (target == DateTarget.Anchor) anchorDate else endedAt ?: anchorDate
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcPickerMillis(current))
    SextantDatePickerDialog(
      onDismissRequest = { dateTarget = null },
      confirmButton = {
        SextantTextButton(onClick = {
          pickerState.selectedDateMillis?.let { selected ->
            val local = utcPickerMillisToLocalStartOfDay(selected)
            if (target == DateTarget.Anchor) anchorDate = local else endedAt = local
          }
          dateTarget = null
        }) { Text("确定") }
      },
      dismissButton = { SextantTextButton(onClick = { dateTarget = null }) { Text("取消") } },
    ) { SextantDatePicker(state = pickerState) }
  }
}

/** 今天本地时区的零点。日期选择器要的是 UTC 当天零点，两边的换算见 `LedgerDateRanges.kt`。 */
private fun todayStart(): Long = LocalDate.now().toStartOfDayMillis()
