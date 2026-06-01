package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.FlowChips
import com.sextant.app.ui.components.ProgressRow
import com.sextant.app.ui.components.SectionTitle
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayName

@Composable
fun BudgetScreen(state: AppState, repository: SextantRepository, onSaved: () -> Unit) {
  var selectedCategoryId by remember { mutableStateOf(state.categories.firstOrNull { it.type == TransactionType.Expense }?.id) }
  var amountText by remember { mutableStateOf("") }
  val expenseCategories = state.categories.filter { it.type == TransactionType.Expense }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { SectionTitle("月度预算") }
    item {
      FlowChips(
        items = expenseCategories,
        selectedId = selectedCategoryId,
        label = { it.displayName(state.categories) },
        color = { argbColor(it.color) },
        onSelect = { selectedCategoryId = it.id },
      )
    }
    item {
      OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it },
        label = { Text("预算金额") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    item {
      Button(
        onClick = {
          val categoryId = selectedCategoryId
          val amount = amountText.toDoubleOrNull()
          if (categoryId != null && amount != null && amount > 0) {
            repository.upsertBudget(categoryId, amount)
            amountText = ""
            onSaved()
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("保存预算")
      }
    }
    items(state.budgets) { item ->
      ProgressRow(item.categoryName, item.spent, item.amount, argbColor(item.color))
    }
  }
}
