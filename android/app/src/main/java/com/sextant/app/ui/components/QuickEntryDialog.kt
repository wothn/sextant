package com.sextant.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.displayName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.util.label
import com.sextant.app.ui.util.parseAmountCents
import com.sextant.app.ui.util.formatAmountPlain
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar

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
  var showCategories by remember { mutableStateOf(false) }
  var showSources by remember { mutableStateOf(false) }
  var amountError by remember { mutableStateOf(false) }
  val typeCategories = state.categories.filter { it.type == type }
  val parentCategories = typeCategories.filter { it.parentCategoryId == null }
  val selectedCategory = typeCategories.firstOrNull { it.id == categoryId }
  val selectedParent = parentCategories.firstOrNull { it.id == selectedCategory?.parentCategoryId }
    ?: parentCategories.firstOrNull { it.id == selectedCategory?.id }
  val paymentMethodOptions = state.paymentMethods.toMutableList().apply {
    val editingMethod = editing?.transaction?.paymentMethodId?.let { id ->
      state.allPaymentMethods.firstOrNull { it.id == id && !it.isActive }
    }
    if (editingMethod != null) add(editingMethod)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize().imePadding(),
      contentWindowInsets = WindowInsets.safeDrawing,
      containerColor = SextantColors.Background,
      topBar = {
        TopAppBar(
          title = { Text(if (editing == null) "记一笔" else "编辑记录", fontWeight = FontWeight.SemiBold) },
          navigationIcon = {
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭记账") }
          },
          actions = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = SextantColors.Danger) }
          },
        )
      },
      bottomBar = {
        Surface(color = SextantColors.Background) {
          Button(
            onClick = {
              val amountCents = parseAmountCents(amountText)
              amountError = amountCents == null
              if (amountCents != null) {
                onSubmit(type, amountCents, categoryId, paymentMethodId, description.trim(), transactionDate)
              }
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
              .padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
            shape = RoundedCornerShape(16.dp),
          ) { Text(if (editing == null) "保存记录" else "保存修改") }
        }
      },
    ) { padding ->
      Column(
        modifier = Modifier.padding(padding).fillMaxSize()
          .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TransactionType.entries.forEach { option ->
            FilterChip(
              modifier = Modifier.weight(1f),
              selected = type == option,
              onClick = {
                type = option
                categoryId = state.categories.firstOrNull { it.type == option && it.parentCategoryId == null }?.id
              },
              label = { Text(option.label) },
              colors = sextantFilterChipColors(),
            )
          }
        }
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it; amountError = false },
          label = { Text("金额") },
          prefix = { Text("¥") },
          textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          isError = amountError,
          supportingText = if (amountError) ({ Text("请输入最多两位小数的有效金额") }) else null,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        Surface(color = SextantColors.Surface, shape = RoundedCornerShape(16.dp)) {
          Column {
            EntrySelectionRow("分类", selectedCategory?.displayName(state.categories) ?: "选择分类") {
              showCategories = true
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = SextantColors.Border)
            Box {
              EntrySelectionRow("来源", paymentMethodOptions.firstOrNull { it.id == paymentMethodId }?.name ?: "选择来源") {
                showSources = true
              }
              DropdownMenu(
                expanded = showSources,
                onDismissRequest = { showSources = false },
                modifier = Modifier.heightIn(max = 320.dp),
              ) {
                if (paymentMethodOptions.isEmpty()) {
                  DropdownMenuItem(text = { Text("暂无可选来源") }, onClick = {}, enabled = false)
                }
                paymentMethodOptions.forEach { method ->
                  DropdownMenuItem(
                    text = { Text(method.name, fontWeight = if (paymentMethodId == method.id) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { paymentMethodId = method.id; showSources = false },
                  )
                }
              }
            }
          }
        }
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("备注（可选）") },
          placeholder = { Text("记录这笔消费的用途") },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text("时间", modifier = Modifier.weight(1f), color = SextantColors.TextMuted)
          TextButton(onClick = { showDatePicker = true }) {
            Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(transactionDate))
          }
          TextButton(onClick = { showTimePicker = true }) {
            Text(SimpleDateFormat("HH:mm", Locale.CHINA).format(transactionDate))
          }
        }
      }
    }

    if (showCategories) {
      ModalBottomSheet(
        onDismissRequest = { showCategories = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SextantColors.Background,
      ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("选择分类", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { showCategories = false }) { Text("完成") }
          }
          Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            CategoryPicker(
              parentCategories = parentCategories,
              typeCategories = typeCategories,
              selectedParentId = selectedParent?.id,
              selectedCategoryId = categoryId,
              onSelect = { category -> categoryId = category.id },
            )
          }
        }
      }
    }
  }

  if (showDatePicker) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = transactionDate)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
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
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
    ) { DatePicker(state = pickerState) }
  }
  if (showTimePicker) {
    val current = Calendar.getInstance().apply { timeInMillis = transactionDate }
    val timeState = rememberTimePickerState(current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true)
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      confirmButton = {
        TextButton(onClick = {
          transactionDate = Calendar.getInstance().apply {
            timeInMillis = transactionDate
            set(Calendar.HOUR_OF_DAY, timeState.hour)
            set(Calendar.MINUTE, timeState.minute)
          }.timeInMillis
          showTimePicker = false
        }) { Text("确定") }
      },
      dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
      text = { TimePicker(state = timeState) },
    )
  }
}


@Composable
private fun EntrySelectionRow(label: String, value: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = SextantColors.TextMuted)
    Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium,
      maxLines = 2, overflow = TextOverflow.Ellipsis)
    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
      tint = SextantColors.TextSubtle, modifier = Modifier.size(20.dp))
  }
}
