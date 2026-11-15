package com.sextant.app.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.sextant.app.ui.components.SextantBottomSheet
import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.components.SextantButton
import com.sextant.app.ui.components.SextantTonalButton
import com.sextant.app.ui.components.SextantTextField

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.BudgetScope
import androidx.compose.material3.FilterChip
import com.sextant.app.data.BudgetProgress
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.BudgetScale
import com.sextant.app.ui.components.FlowChips
import androidx.compose.foundation.layout.size
import com.sextant.app.ui.components.LocalBottomBarInset
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.CategoryIcon
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.SextantMetric
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.ui.components.SextantTextButton
import com.sextant.app.ui.theme.sextantEmphasized
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
  var editingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
  var pendingClear by rememberSaveable { mutableStateOf(false) }
  var clearingTotal by rememberSaveable { mutableStateOf(false) }
  var categoryScope by rememberSaveable { mutableStateOf(BudgetScope.FromThisMonth) }
  var totalScope by rememberSaveable { mutableStateOf(BudgetScope.FromThisMonth) }
  var totalText by rememberSaveable(state.monthlyBudgetCents) { mutableStateOf(state.monthlyBudgetCents?.let(::formatAmountPlain).orEmpty()) }
  var selectedCategoryId by rememberSaveable { mutableStateOf(state.categories.firstOrNull { it.type == TransactionType.Expense && it.parentCategoryId == null && it.includeInSpending }?.id) }
  var categoryAmountText by rememberSaveable { mutableStateOf("") }
  // 写预算是挂起调用（Room 不允许主线程访问数据库），而底部面板的保存回调不是协程。
  val saveAction = rememberSaveAction("budget") {
    editingTotal = false
    editingCategory = false
    editingCategoryId = null
    categoryAmountText = ""
  }
  val expenseCategories = state.categories.filter { it.type == TransactionType.Expense && it.parentCategoryId == null && it.includeInSpending }
  val editingBudget = editingCategoryId?.let { id ->
    state.budgets.firstOrNull { it.categoryId == id }
  }
  val openCategoryEditor: (BudgetProgress?) -> Unit = { budget ->
    categoryScope = BudgetScope.FromThisMonth
    selectedCategoryId = budget?.categoryId ?: expenseCategories.firstOrNull()?.id
    categoryAmountText = budget?.amountCents?.let(::formatAmountPlain).orEmpty()
    editingCategoryId = budget?.categoryId
    editingCategory = true
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp + LocalBottomBarInset.current),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    item { BudgetOverview(state, onEdit = { totalScope = BudgetScope.FromThisMonth; editingTotal = true }) }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          SextantHeading("分类预算")
          Text("${state.budgets.size} 个分类已设置", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
        }
        SextantTonalButton(onClick = { openCategoryEditor(null) }) {
          Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("添加")
        }
      }
    }
    state.monthlyBudgetCents?.let { total ->
      item {
        val allocated = state.budgets.sumOf { it.amountCents }
        Text(
          if (allocated > total) "分类额度合计超出总预算 ${formatCurrency(allocated - total)}"
          else "已分配 ${formatCurrency(allocated)} · 未分配 ${formatCurrency(total - allocated)}",
          color = if (allocated > total) SextantColors.Warning else SextantColors.TextMuted,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    if (state.budgets.isEmpty()) {
      item {
        EmptyCard(
          title = "还没有分类预算",
          description = "为餐饮、购物等消费分配额度，看看每一类还可以花多少。",
          actionLabel = "添加分类预算",
        ) { openCategoryEditor(null) }
      }
    } else {
      items(state.budgets, key = { it.categoryId }) { budget ->
        CategoryBudgetRow(budget) { openCategoryEditor(budget) }
      }
    }
  }

  if (editingTotal) {
    SextantBottomSheet(onDismissRequest = { editingTotal = false }) {
      BudgetEditor(
        title = if (state.monthlyBudgetCents == null) "设置本月总预算" else "调整本月总预算",
        scope = totalScope,
        onScopeChanged = { totalScope = it },
        onClear = if (state.monthlyBudgetCents == null) null else ({ clearingTotal = true; pendingClear = true }),
        amountText = totalText,
        onAmountChanged = { totalText = it },
        onSave = {
          parseAmountCents(totalText)?.let {
            saveAction.launch {
              repository.setMonthlyBudget(it, totalScope)
            }
          }
        },
      )
    }
  }

  if (editingCategory) {
    SextantBottomSheet(onDismissRequest = { editingCategory = false }) {
      Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          SextantHeading(if (editingBudget != null) "编辑分类预算" else "添加分类预算")
          Text(
            if (editingBudget != null) "修改额度，或清除这项预算" else "为波动较大的消费设置单独额度",
            color = SextantColors.TextSubtle,
          )
        }
        if (editingBudget != null) {
          Text(editingBudget.categoryName, style = MaterialTheme.typography.titleMedium)
        } else {
          Text("消费分类", style = MaterialTheme.typography.labelLarge)
          FlowChips(
            items = expenseCategories,
            selectedId = selectedCategoryId,
            label = { it.displayName(state.categories) },
            color = { argbColor(it.color) },
            icon = { categoryIcon(it.name) },
            onSelect = { selectedCategoryId = it.id },
          )
        }
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
        BudgetScopeSelector(categoryScope) { categoryScope = it }
        SextantButton(onClick = {
            val categoryId = editingCategoryId ?: selectedCategoryId
            val amount = parseAmountCents(categoryAmountText)
            if (categoryId != null && amount != null) {
              saveAction.launch {
                repository.upsertBudget(categoryId, amount, categoryScope)
              }
            }
          },
          enabled = (editingCategoryId != null || selectedCategoryId != null) && parseAmountCents(categoryAmountText) != null && !saveAction.busy,
          modifier = Modifier.fillMaxWidth(),
        ) { Text(if (editingBudget != null) "保存修改" else "保存分类预算") }
        if (editingBudget != null) {
          SextantTextButton(
            onClick = { clearingTotal = false; pendingClear = true },
            enabled = !saveAction.busy,
            contentColor = SextantColors.Danger,
          ) { Text("清除这项预算") }
        }
      }
    }
  }

  if (pendingClear) {
    val categoryId = editingCategoryId
    val scope = if (clearingTotal) totalScope else categoryScope
    SextantAlertDialog(
      onDismissRequest = { pendingClear = false },
      title = { Text(if (clearingTotal) "清除总预算？" else "清除这项预算？") },
      text = { Text(if (scope == BudgetScope.ThisMonth) "仅停用本月预算，下月按原设置执行。历史预算和流水保留。" else "从本月起停用，以后不再沿用。历史预算和流水保留。") },
      confirmButton = {
        SextantTextButton(
          onClick = {
            pendingClear = false
            if (clearingTotal) {
              saveAction.launch { repository.clearMonthlyBudget(scope) }
            } else if (categoryId != null) {
              saveAction.launch { repository.clearBudget(categoryId, scope) }
            }
          },
          contentColor = SextantColors.Danger,
        ) { Text("清除") }
      },
      dismissButton = {
        SextantTextButton(onClick = { pendingClear = false }) { Text("取消") }
      },
    )
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

  SextantSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
    Column(Modifier.fillMaxWidth().padding(16.dp).animateContentSize(animationSpec = SextantMotion.defaultSpatial()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("本月预算", color = SextantColors.TextMuted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
        SextantTextButton(onClick = onEdit) {
          Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (total == null) "设置预算" else "调整预算")
        }
      }
      SextantMetric(
        label = if (total == null) "尚未设置总预算" else if (spent > total) "本月已超支" else "本月剩余",
        value = if (total == null) "—" else formatCurrency(if (spent > total) spent - total else remaining),
        primary = true,
        valueStyle = MaterialTheme.typography.headlineLarge.sextantEmphasized(),
        valueColor = when {
          total == null -> SextantColors.TextSubtle
          spent > total -> SextantColors.Danger
          else -> SextantColors.Text
        },
      )
      if (total != null) {
        BudgetScale(percent, when {
          spent > total -> SextantColors.Danger
          percent >= 0.8f -> SextantColors.Warning
          else -> SextantColors.Accent
        }, notchColor = SextantColors.SurfaceContainerLow)
      }
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SextantMetric("已支出", formatCurrency(spent))
        SextantMetric("总预算", total?.let(::formatCurrency) ?: "未设置")
        SextantMetric("日均可用", if (total == null) "—" else formatCurrency(remaining / daysRemaining))
      }
      Text("本月还剩 $daysRemaining 天（含今天）", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun BudgetEditor(title: String, scope: BudgetScope, onScopeChanged: (BudgetScope) -> Unit, onClear: (() -> Unit)?, amountText: String, onAmountChanged: (String) -> Unit, onSave: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SextantHeading(title)
    Text("额度用于概览进度和本月日均可用金额", color = SextantColors.TextMuted)
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
    BudgetScopeSelector(scope, onScopeChanged)
    SextantButton(onClick = onSave,
      enabled = parseAmountCents(amountText) != null,
      modifier = Modifier.fillMaxWidth(),
    ) { Text("保存总预算") }
    if (onClear != null) {
      SextantTextButton(onClick = onClear, contentColor = SextantColors.Danger) { Text("清除总预算") }
    }
  }
}

@Composable
private fun CategoryBudgetRow(item: BudgetProgress, onClick: () -> Unit) {
  val percent = if (item.amountCents <= 0) 0f else (item.spentCents.toFloat() / item.amountCents).coerceIn(0f, 1f)
  val overspent = item.spentCents > item.amountCents
  val color = if (overspent) SextantColors.Danger else if (percent >= 0.8f) SextantColors.Warning else argbColor(item.color)
  Column(
    Modifier.fillMaxWidth()
      .clickable(onClickLabel = "编辑${item.categoryName}的预算", onClick = onClick)
      .padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryIcon(categoryIcon(item.categoryName), argbColor(item.color), size = 40.dp, iconSize = 22.dp)
        Text(item.categoryName, style = MaterialTheme.typography.titleLarge)
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (overspent) "已超出" else "还可使用", style = MaterialTheme.typography.labelMedium, color = if (overspent) SextantColors.Danger else SextantColors.TextMuted)
        Text(formatCurrency(kotlin.math.abs(item.amountCents - item.spentCents)), style = MaterialTheme.typography.titleLarge.sextantEmphasized(), color = if (overspent) SextantColors.Danger else SextantColors.Text)
      }
    }
    BudgetScale(percent, color)
    Text(
      "已用 ${formatCurrency(item.spentCents)} / 额度 ${formatCurrency(item.amountCents)}",
      color = SextantColors.TextMuted,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}@Composable
private fun BudgetScopeSelector(scope: BudgetScope, onSelect: (BudgetScope) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("生效范围", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      BudgetScope.entries.forEach { item ->
        FilterChip(selected = scope == item, onClick = { onSelect(item) }, label = { Text(item.label) })
      }
    }
    Text(
      if (scope == BudgetScope.FromThisMonth) "以后每月沿用此额度，历史月份不变" else "只调整本月，下月按原设置执行",
      color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall,
    )
  }
}
