package com.sextant.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.BudgetProgress
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.FlowChips
import com.sextant.app.ui.components.SectionTitle
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayName
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.parseAmountCents
import java.util.Calendar
import kotlin.math.max

@Composable
fun BudgetScreen(state: AppState, repository: SextantRepository, onSaved: () -> Unit) {
  var editingTotal by remember { mutableStateOf(state.monthlyBudgetCents == null) }
  var editingCategory by remember { mutableStateOf(false) }
  var totalText by remember(state.monthlyBudgetCents) { mutableStateOf(state.monthlyBudgetCents?.let { "%.2f".format(it / 100.0) }.orEmpty()) }
  var selectedCategoryId by remember { mutableStateOf(state.categories.firstOrNull { it.type == TransactionType.Expense && it.parentCategoryId == null && it.includeInSpending }?.id) }
  var categoryAmountText by remember { mutableStateOf("") }
  val expenseCategories = state.categories.filter { it.type == TransactionType.Expense && it.parentCategoryId == null && it.includeInSpending }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("月度预算")
        Text("控制总支出，再为重要分类分配额度。", color = SextantColors.TextSubtle)
      }
    }
    item { BudgetOverview(state, onEdit = { editingTotal = !editingTotal }) }
    item {
      AnimatedVisibility(editingTotal) {
        BudgetEditor(
          title = if (state.monthlyBudgetCents == null) "设置本月总预算" else "调整本月总预算",
          amountText = totalText,
          onAmountChanged = { totalText = it },
          onSave = {
            parseAmountCents(totalText)?.let {
              repository.setMonthlyBudget(it)
              editingTotal = false
              onSaved()
            }
          },
        )
      }
    }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("分类预算", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text("${state.budgets.size} 个分类已设置", color = SextantColors.TextSubtle)
        }
        OutlinedButton(onClick = { editingCategory = !editingCategory }) {
          Icon(Icons.Outlined.Add, null)
          Spacer(Modifier.width(6.dp))
          Text("添加")
        }
      }
    }
    item {
      AnimatedVisibility(editingCategory) {
        Column(
          Modifier.fillMaxWidth().background(SextantColors.SurfaceAlt, RoundedCornerShape(20.dp)).padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("选择一个主要消费分类", fontWeight = FontWeight.SemiBold)
          FlowChips(
            items = expenseCategories,
            selectedId = selectedCategoryId,
            label = { it.displayName(state.categories) },
            color = { argbColor(it.color) },
            onSelect = { selectedCategoryId = it.id },
          )
          OutlinedTextField(
            value = categoryAmountText,
            onValueChange = { categoryAmountText = it },
            label = { Text("每月额度") },
            prefix = { Text("¥ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )
          Button(
            onClick = {
              val categoryId = selectedCategoryId
              val amount = parseAmountCents(categoryAmountText)
              if (categoryId != null && amount != null) {
                repository.upsertBudget(categoryId, amount)
                categoryAmountText = ""
                editingCategory = false
                onSaved()
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("保存分类预算") }
        }
      }
    }
    if (state.budgets.isEmpty()) {
      item {
        Text("还没有分类预算。可以先为餐饮、购物等波动较大的支出设置额度。", color = SextantColors.TextMuted)
      }
    } else {
      items(state.budgets) { CategoryBudgetRow(it) }
    }
  }
}

@Composable
private fun BudgetOverview(state: AppState, onEdit: () -> Unit) {
  val total = state.monthlyBudgetCents
  val spent = state.monthSummary.expenseCents
  val remaining = max(0, (total ?: 0) - spent)
  val percent = if (total == null || total <= 0) 0f else (spent.toFloat() / total).coerceIn(0f, 1f)
  val calendar = Calendar.getInstance()
  val daysRemaining = max(1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH) - calendar.get(Calendar.DAY_OF_MONTH) + 1)

  Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (total == null) "尚未设置总预算" else "本月剩余", color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
        Text(if (total == null) "—" else formatCurrency(remaining), fontSize = 38.sp, fontWeight = FontWeight.Bold)
      }
      OutlinedButton(onClick = onEdit) {
        Icon(Icons.Outlined.Edit, null)
        Spacer(Modifier.width(6.dp))
        Text(if (total == null) "设置" else "调整")
      }
    }
    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(SextantColors.SurfaceStrong)) {
      Box(Modifier.fillMaxWidth(percent).height(10.dp).clip(CircleShape).background(if (percent >= 0.8f) SextantColors.Warning else SextantColors.Accent))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      BudgetMetric("已支出", formatCurrency(spent))
      BudgetMetric("总预算", total?.let(::formatCurrency) ?: "未设置")
      BudgetMetric("日均可用", if (total == null) "—" else formatCurrency(remaining / daysRemaining))
    }
  }
}

@Composable
private fun BudgetMetric(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = SextantColors.TextSubtle)
    Text(value, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun BudgetEditor(title: String, amountText: String, onAmountChanged: (String) -> Unit, onSave: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().background(SextantColors.AccentSoft, RoundedCornerShape(20.dp)).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(title, fontWeight = FontWeight.Bold)
    OutlinedTextField(
      value = amountText,
      onValueChange = onAmountChanged,
      label = { Text("预算金额") },
      prefix = { Text("¥ ") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      onClick = onSave,
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(containerColor = SextantColors.Accent),
    ) { Text("保存总预算") }
  }
}

@Composable
private fun CategoryBudgetRow(item: BudgetProgress) {
  val percent = if (item.amountCents <= 0) 0f else (item.spentCents.toFloat() / item.amountCents).coerceIn(0f, 1f)
  val color = if (percent >= 1f) SextantColors.Danger else if (percent >= 0.8f) SextantColors.Warning else argbColor(item.color)
  Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(item.categoryName, fontWeight = FontWeight.SemiBold)
      Text("${formatCurrency(item.spentCents)} / ${formatCurrency(item.amountCents)}", color = SextantColors.TextMuted)
    }
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(SextantColors.SurfaceStrong)) {
      Box(Modifier.fillMaxWidth(percent).height(8.dp).clip(CircleShape).background(color))
    }
    Text(
      if (item.spentCents > item.amountCents) "已超出 ${formatCurrency(item.spentCents - item.amountCents)}" else "还可使用 ${formatCurrency(item.amountCents - item.spentCents)}",
      color = color,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}
