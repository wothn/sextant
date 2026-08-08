package com.sextant.app.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.argbColor
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

  ModalBottomSheet(
    onDismissRequest = onDismiss,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding()
        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          if (editing == null) "记一笔" else "编辑记录",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        if (onDelete != null) TextButton(onClick = onDelete) { Text("删除") }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionType.entries.forEach { option ->
          FilterChip(
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
        onValueChange = {
          amountText = it
          amountError = false
        },
        label = { Text("金额") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = amountError,
        supportingText = if (amountError) ({ Text("请输入最多两位小数的有效金额") }) else null,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
          Text(SimpleDateFormat("M月d日", Locale.CHINA).format(transactionDate))
        }
        TextButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
          Text(SimpleDateFormat("HH:mm", Locale.CHINA).format(transactionDate))
        }
      }
      Text("分类", style = MaterialTheme.typography.labelLarge)
      CategoryPicker(
        parentCategories = parentCategories,
        typeCategories = typeCategories,
        selectedParentId = selectedParent?.id,
        selectedCategoryId = categoryId,
        onSelect = { category -> categoryId = category.id },
      )
      Text("来源", style = MaterialTheme.typography.labelLarge)
      FlowChips(paymentMethodOptions, paymentMethodId, { it.name }, { argbColor(it.color) }) { paymentMethodId = it.id }
      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("备注（可选）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        onClick = {
          val amountCents = parseAmountCents(amountText)
          amountError = amountCents == null
          if (amountCents != null) {
            onSubmit(type, amountCents, categoryId, paymentMethodId, description.trim(), transactionDate)
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (editing == null) "保存记录" else "保存修改")
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
