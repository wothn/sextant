package com.sextant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.label

@Composable
fun QuickEntryDialog(
  state: AppState,
  onDismiss: () -> Unit,
  onSubmit: (TransactionType, Double, String?, String?, String) -> Unit,
) {
  var type by remember { mutableStateOf(TransactionType.Expense) }
  var amountText by remember { mutableStateOf("") }
  var categoryId by remember { mutableStateOf(state.categories.firstOrNull { it.type == TransactionType.Expense }?.id) }
  var paymentMethodId by remember { mutableStateOf(state.paymentMethods.firstOrNull()?.id) }
  var description by remember { mutableStateOf("") }
  val typeCategories = state.categories.filter { it.type == type }
  val parentCategories = typeCategories.filter { it.parentCategoryId == null }
  val selectedCategory = typeCategories.firstOrNull { it.id == categoryId }
  val selectedParent = parentCategories.firstOrNull { it.id == selectedCategory?.parentCategoryId }
    ?: parentCategories.firstOrNull { it.id == selectedCategory?.id }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(onClick = {
        val amount = amountText.toDoubleOrNull()
        if (amount != null && amount > 0) {
          onSubmit(type, amount, categoryId, paymentMethodId, description)
        }
      }) {
        Text("保存")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    title = { Text("快速记账") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
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
          onValueChange = { amountText = it },
          label = { Text("金额") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
        CategoryPicker(
          parentCategories = parentCategories,
          typeCategories = typeCategories,
          selectedParentId = selectedParent?.id,
          selectedCategoryId = categoryId,
          onSelect = { category -> categoryId = category.id },
        )
        FlowChips(state.paymentMethods, paymentMethodId, { it.name }, { argbColor(it.color) }) { paymentMethodId = it.id }
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("备注") },
          singleLine = true,
        )
      }
    },
  )
}
