package com.sextant.app.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.sextant.app.ui.components.SextantBottomSheet
import com.sextant.app.ui.components.SextantButton
import com.sextant.app.ui.components.SextantTonalButton
import com.sextant.app.ui.components.SextantTextField

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import com.sextant.app.ui.theme.SextantMotion
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.sextant.app.ui.components.rememberSaveAction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.BudgetProgress
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.BudgetScale
import com.sextant.app.ui.components.FlowChips
import androidx.compose.foundation.layout.size
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.categoryIcon
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayName
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.parseAmountCents
import java.util.Calendar
import kotlin.math.max

/**
 * 「计划」页的预算视图。
 *
 * 页面标题、副标题和一级切换都由 [PlansScreen] 承担，这里只负责列表本身。滚动状态由上层
 * 持有并传进来，切到周期支出再切回来时，预算才会停在原来的位置。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BudgetPane(
  listState: LazyListState,
  state: AppState,
  repository: SextantRepository,

) {
  var editingTotal by rememberSaveable { mutableStateOf(false) }
  var editingCategory by rememberSaveable { mutableStateOf(false) }
  var totalText by rememberSaveable(state.monthlyBudgetCents) { mutableStateOf(state.monthlyBudgetCents?.let(::formatAmountPlain).orEmpty()) }
  var selectedCategoryId by rememberSaveable { mutableStateOf(state.categories.firstOrNull { it.type == TransactionType.Expense && it.parentCategoryId == null && it.includeInSpending }?.id) }
  var categoryAmountText by rememberSaveable { mutableStateOf("") }
  // 写预算是挂起调用（Room 不允许主线程访问数据库），而底部面板的保存回调不是协程。
  val saveAction = rememberSaveAction("budget") {
    editingTotal = false
    editingCategory = false
    categoryAmountText = ""
  }
  val expenseCategories = state.categories.filter { it.type == TransactionType.Expense && it.parentCategoryId == null && it.includeInSpending }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item { BudgetOverview(state, onEdit = { editingTotal = true }) }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          SextantHeading("分类预算")
          Text("${state.budgets.size} 个分类已设置", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
        }
        SextantTonalButton(onClick = { editingCategory = true }) {
          Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("添加")
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

  if (editingTotal) {
    SextantBottomSheet(onDismissRequest = { editingTotal = false }) {
      BudgetEditor(
        title = if (state.monthlyBudgetCents == null) "设置本月总预算" else "调整本月总预算",
        amountText = totalText,
        onAmountChanged = { totalText = it },
        onSave = {
          parseAmountCents(totalText)?.let {
            saveAction.launch {
              repository.setMonthlyBudget(it)
            }
          }
        },
      )
    }
  }

  if (editingCategory) {
    SextantBottomSheet(onDismissRequest = { editingCategory = false }) {
      Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          SextantHeading("添加分类预算")
          Text("为波动较大的消费设置单独额度", color = SextantColors.TextSubtle)
        }
        Text("消费分类", style = MaterialTheme.typography.labelLarge)
        FlowChips(
          items = expenseCategories,
          selectedId = selectedCategoryId,
          label = { it.displayName(state.categories) },
          color = { argbColor(it.color) },
          icon = { categoryIcon(it.name) },
          onSelect = { selectedCategoryId = it.id },
        )
        SextantTextField(
          value = categoryAmountText,
          onValueChange = { categoryAmountText = it },
          label = { Text("每月额度") },
          isError = categoryAmountText.isNotEmpty() && parseAmountCents(categoryAmountText) == null,
          supportingText = { Text("请输入最多两位小数的有效金额") },
          prefix = { Text("¥ ") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )
        SextantButton(onClick = {
            val categoryId = selectedCategoryId
            val amount = parseAmountCents(categoryAmountText)
            if (categoryId != null && amount != null) {
              saveAction.launch {
                repository.upsertBudget(categoryId, amount)
              }
            }
          },
          enabled = selectedCategoryId != null && parseAmountCents(categoryAmountText) != null,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("保存分类预算") }
      }
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

  Column(Modifier.fillMaxWidth().animateContentSize(animationSpec = SextantMotion.defaultSpatial()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (total == null) "尚未设置总预算" else if (spent > total) "本月已超支" else "本月剩余", color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
        Text(if (total == null) "—" else formatCurrency(if (spent > total) spent - total else remaining), style = MaterialTheme.typography.headlineLarge)
      }
      SextantTonalButton(onClick = onEdit) {
        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (total == null) "设置" else "调整")
      }
    }
    BudgetScale(percent, when {
      percent >= 1f -> SextantColors.Danger
      percent >= 0.8f -> SextantColors.Warning
      else -> SextantColors.Accent
    })
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SextantHeading(title)
    Text("额度会用于首页进度和月底预测", color = SextantColors.TextSubtle)
    SextantTextField(
      value = amountText,
      onValueChange = onAmountChanged,
      label = { Text("预算金额") },
      isError = amountText.isNotEmpty() && parseAmountCents(amountText) == null,
      supportingText = { Text("请输入最多两位小数的有效金额") },
      prefix = { Text("¥ ") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.fillMaxWidth(),
    )
    SextantButton(onClick = onSave,
      enabled = parseAmountCents(amountText) != null,
      modifier = Modifier.fillMaxWidth(),
    ) { Text("保存总预算") }
  }
}

@Composable
private fun CategoryBudgetRow(item: BudgetProgress) {
  val percent = if (item.amountCents <= 0) 0f else (item.spentCents.toFloat() / item.amountCents).coerceIn(0f, 1f)
  val color = if (percent >= 1f) SextantColors.Danger else if (percent >= 0.8f) SextantColors.Warning else argbColor(item.color)
  Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(item.categoryName, fontWeight = FontWeight.SemiBold)
      Text("${formatCurrency(item.spentCents)} / ${formatCurrency(item.amountCents)}", color = SextantColors.TextMuted)
    }
    BudgetScale(percent, color)
    Text(
      if (item.spentCents > item.amountCents) "已超出 ${formatCurrency(item.spentCents - item.amountCents)}" else "还可使用 ${formatCurrency(item.amountCents - item.spentCents)}",
      color = color,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}
