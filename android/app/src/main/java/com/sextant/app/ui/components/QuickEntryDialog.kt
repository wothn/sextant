package com.sextant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.Category
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayName
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.label
import com.sextant.app.ui.util.parseAmountCents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class EntryPanel { Form, Category, Source }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuickEntryDialog(
  state: AppState,
  editing: TransactionListItem? = null,
  initialPaymentMethodId: String? = null,
  onDismiss: () -> Unit,
  onDelete: (() -> Unit)? = null,
  onSubmit: (TransactionType, Long, String?, String?, String, Long) -> Unit,
) {
  var type by remember(editing) { mutableStateOf(editing?.transaction?.type ?: TransactionType.Expense) }
  var amountText by remember(editing) { mutableStateOf(editing?.transaction?.amountCents?.let(::formatAmountPlain).orEmpty()) }
  var categoryId by remember(editing) { mutableStateOf(editing?.transaction?.categoryId ?: state.categories.firstOrNull { it.type == TransactionType.Expense }?.id) }
  var paymentMethodId by remember(editing) { mutableStateOf(editing?.transaction?.paymentMethodId ?: initialPaymentMethodId ?: state.paymentMethods.firstOrNull()?.id) }
  var description by remember(editing) { mutableStateOf(editing?.transaction?.description.orEmpty()) }
  var transactionDate by remember(editing) { mutableStateOf(editing?.transaction?.transactionDate ?: System.currentTimeMillis()) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }
  var amountError by remember { mutableStateOf(false) }
  var panel by remember { mutableStateOf(EntryPanel.Form) }
  val typeCategories = state.categories.filter { it.type == type }
  val selectedCategory = typeCategories.firstOrNull { it.id == categoryId }
  val paymentMethodOptions = state.paymentMethods.toMutableList().apply {
    val inactive = editing?.transaction?.paymentMethodId?.let { id ->
      state.allPaymentMethods.firstOrNull { it.id == id && !it.isActive }
    }
    if (inactive != null) add(inactive)
  }
  val selectedMethod = paymentMethodOptions.firstOrNull { it.id == paymentMethodId }

  SextantBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    // Keep boundary flings inside selection lists instead of moving the sheet.
    sheetGesturesEnabled = panel == EntryPanel.Form,
  ) {
    val focus = LocalFocusManager.current
    BackHandler(enabled = panel != EntryPanel.Form) { panel = EntryPanel.Form }
    val panelHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Column(
      Modifier.fillMaxWidth()
        .then(if (panel == EntryPanel.Form) Modifier.heightIn(max = panelHeight) else Modifier.height(panelHeight))
        .padding(horizontal = 24.dp),
    ) {
      Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (panel != EntryPanel.Form) {
          SextantIconButton(onClick = { panel = EntryPanel.Form }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回记账")
          }
        }
        SextantHeading(
          when (panel) {
            EntryPanel.Form -> if (editing == null) "记一笔" else "编辑记录"
            EntryPanel.Category -> "选择分类"
            EntryPanel.Source -> "选择来源"
          },
          modifier = Modifier.weight(1f),
        )
        if (panel == EntryPanel.Form && onDelete != null) {
          SextantTextButton(onClick = onDelete) { Text("删除", color = SextantColors.Danger) }
        }
        SextantIconButton(onClick = { if (panel == EntryPanel.Form) onDismiss() else panel = EntryPanel.Form }) {
          Icon(Icons.Outlined.Close, contentDescription = if (panel == EntryPanel.Form) "关闭记账" else "关闭选择",
            modifier = Modifier.size(20.dp))
        }
      }
      // Only the body scrolls. The action stays within reach when the IME or a large font reduces space.
      key(panel) {
        Column(
          Modifier.weight(1f, fill = panel != EntryPanel.Form).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          when (panel) {
            EntryPanel.Form -> {
              SextantSegmentedButtonGroup(
                options = TransactionType.entries,
                selected = type,
                onSelect = { option ->
                  type = option
                  categoryId = state.categories.firstOrNull { it.type == option && it.parentCategoryId == null }?.id
                },
                label = { it.label },
                modifier = Modifier.fillMaxWidth(),
              )
              Column(Modifier.padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${type.label}金额", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text("¥", fontSize = 28.sp, color = SextantColors.TextMuted, fontWeight = FontWeight.Medium)
                  BasicTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = false },
                    modifier = Modifier.weight(1f).heightIn(min = 60.dp).semantics { contentDescription = "金额" },
                    textStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 42.sp,
                      fontWeight = FontWeight.SemiBold, color = SextantColors.Text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(SextantColors.Accent),
                    singleLine = true,
                    decorationBox = { inner ->
                      Box(contentAlignment = Alignment.CenterStart) {
                        if (amountText.isEmpty()) Text("0.00", fontSize = 42.sp, fontWeight = FontWeight.SemiBold,
                          color = SextantColors.TextSubtle.copy(alpha = 0.55f))
                        inner()
                      }
                    },
                  )
                }
                SextantDivider(color = if (amountError) SextantColors.Danger else SextantColors.SurfaceContainerHighest)
                if (amountError) Text("请输入最多两位小数的有效金额", color = SextantColors.Danger,
                  style = MaterialTheme.typography.bodySmall)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SextantDetailTile("分类", selectedCategory?.displayName(state.categories) ?: "选择分类",
                  categoryIcon(selectedCategory?.name.orEmpty()), Modifier.weight(1f)) {
                  focus.clearFocus()
                  panel = EntryPanel.Category
                }
                SextantDetailTile("来源", selectedMethod?.name ?: "选择来源", Icons.Outlined.AccountBalanceWallet,
                  Modifier.weight(1f)) {
                  focus.clearFocus()
                  panel = EntryPanel.Source
                }
              }
              SextantSurface(color = SextantColors.SurfaceContainer) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                  Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(21.dp))
                    BasicTextField(
                      value = description, onValueChange = { description = it }, singleLine = true,
                      textStyle = MaterialTheme.typography.bodyMedium.copy(color = SextantColors.Text),
                      cursorBrush = SolidColor(SextantColors.Accent),
                      modifier = Modifier.weight(1f).padding(vertical = 16.dp).semantics { contentDescription = "备注（可选）" },
                      decorationBox = { inner ->
                        Box {
                          if (description.isEmpty()) Text("添加备注…", color = SextantColors.TextSubtle,
                            style = MaterialTheme.typography.bodyMedium)
                          inner()
                        }
                      },
                    )
                  }
                  SextantDivider(color = SextantColors.SurfaceContainerHighest)
                  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(20.dp))
                    SextantTextButton(onClick = { focus.clearFocus(); showDatePicker = true }, modifier = Modifier.weight(1f)) {
                      Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(transactionDate), color = SextantColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium)
                    }
                    SextantTextButton(onClick = { focus.clearFocus(); showTimePicker = true }) {
                      Text(SimpleDateFormat("HH:mm", Locale.CHINA).format(transactionDate), color = SextantColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium)
                    }
                  }
                }
              }
            }
            EntryPanel.Category -> {
              Text("${type.label}分类", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelMedium)
              CategoryBrowser(categories = typeCategories, selectedId = categoryId, onSelect = { category ->
                categoryId = category?.id
                panel = EntryPanel.Form
              })
            }
            EntryPanel.Source -> {
              Text("选择这笔记录的支付来源", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodyMedium)
              paymentMethodOptions.forEach { method ->
                val selected = method.id == paymentMethodId
                SextantSelectableSurface(
                  selected = selected,
                  onClick = { paymentMethodId = method.id; panel = EntryPanel.Form },
                  role = Role.RadioButton,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CategoryIcon(Icons.Outlined.AccountBalanceWallet, argbColor(method.color))
                    Text(method.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = "已选择", tint = SextantColors.AccentStrong,
                      modifier = Modifier.size(20.dp))
                  }
                }
              }
              if (paymentMethodOptions.isEmpty()) Text("暂无可选来源", color = SextantColors.TextMuted)
            }
          }
          Spacer(Modifier.height(4.dp))
        }
      }
      SextantButton(onClick = {
          if (panel != EntryPanel.Form) {
            panel = EntryPanel.Form
          } else {
            val cents = parseAmountCents(amountText)
            amountError = cents == null
            if (cents != null) onSubmit(type, cents, categoryId, paymentMethodId, description.trim(), transactionDate)
          }
        },
        size = SextantButtonSize.Medium,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
      ) {
        Text(if (panel != EntryPanel.Form) "返回记账" else if (editing == null) "记好了" else "保存修改",
          style = MaterialTheme.typography.titleSmall)
      }
    }
  }
  if (showDatePicker) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = transactionDate)
    SextantDatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        SextantTextButton(onClick = {
          pickerState.selectedDateMillis?.let { selected ->
            val old = Calendar.getInstance().apply { timeInMillis = transactionDate }
            transactionDate = Calendar.getInstance().apply {
              timeInMillis = selected
              set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
              set(Calendar.MINUTE, old.get(Calendar.MINUTE))
            }.timeInMillis
          }
          showDatePicker = false
        }) { Text("确定") }
      },
      dismissButton = { SextantTextButton(onClick = { showDatePicker = false }) { Text("取消") } },
    ) { SextantDatePicker(state = pickerState) }
  }
  if (showTimePicker) {
    val current = Calendar.getInstance().apply { timeInMillis = transactionDate }
    val timeState = rememberTimePickerState(current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true)
    SextantTimePickerDialog(
      onDismissRequest = { showTimePicker = false },
      confirmButton = {
        SextantTextButton(onClick = {
          transactionDate = Calendar.getInstance().apply {
            timeInMillis = transactionDate
            set(Calendar.HOUR_OF_DAY, timeState.hour)
            set(Calendar.MINUTE, timeState.minute)
          }.timeInMillis
          showTimePicker = false
        }) { Text("确定") }
      },
      dismissButton = { SextantTextButton(onClick = { showTimePicker = false }) { Text("取消") } },
    ) { SextantTimePicker(state = timeState) }
  }
}

