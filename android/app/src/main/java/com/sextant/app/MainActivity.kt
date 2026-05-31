package com.sextant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Account
import com.sextant.app.data.AppState
import com.sextant.app.data.Category
import com.sextant.app.data.PaymentMethod
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantTheme
import com.sextant.app.ui.theme.argbColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val repository = (application as SextantApplication).repository
    setContent {
      SextantTheme {
        SextantApp(repository)
      }
    }
  }
}

private enum class Tab(val title: String, val icon: ImageVector) {
  Home("首页", Icons.Outlined.Home),
  Analytics("分析", Icons.Outlined.Analytics),
  Assets("资产", Icons.Outlined.AccountBalanceWallet),
  Budget("预算", Icons.Outlined.PieChart),
  Settings("设置", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SextantApp(repository: SextantRepository) {
  var state by remember { mutableStateOf(AppState()) }
  var tab by remember { mutableStateOf(Tab.Home) }
  var showQuickEntry by remember { mutableStateOf(false) }
  var reloadKey by remember { mutableStateOf(0) }

  LaunchedEffect(reloadKey) {
    state = repository.loadState()
  }

  Scaffold(
    topBar = { CenterAlignedTopAppBar(title = { Text("Sextant") }) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        text = { Text("记一笔") },
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        onClick = { showQuickEntry = true },
      )
    },
    bottomBar = {
      NavigationBar(containerColor = SextantColors.Surface) {
        Tab.entries.forEach { item ->
          NavigationBarItem(
            selected = tab == item,
            onClick = { tab = item },
            icon = { Icon(item.icon, contentDescription = item.title) },
            label = { Text(item.title) },
          )
        }
      }
    },
    containerColor = SextantColors.Background,
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      when (tab) {
        Tab.Home -> HomeScreen(state)
        Tab.Analytics -> AnalyticsScreen(state)
        Tab.Assets -> AssetsScreen(state, repository) { reloadKey++ }
        Tab.Budget -> BudgetScreen(state, repository) { reloadKey++ }
        Tab.Settings -> SettingsScreen(repository)
      }
    }
  }

  if (showQuickEntry) {
    QuickEntryDialog(
      state = state,
      onDismiss = { showQuickEntry = false },
      onSubmit = { type, amount, categoryId, accountId, paymentMethodId, description ->
        repository.addTransaction(type, amount, categoryId, accountId, paymentMethodId, description)
        showQuickEntry = false
        reloadKey++
      },
    )
  }
}

@Composable
private fun HomeScreen(state: AppState) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text("今天", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text("本地优先的个人账本", color = SextantColors.TextMuted)
    }
    item {
      SummaryCard("今日概览", state.todaySummary.income, state.todaySummary.expense)
    }
    item {
      SummaryCard("本月概览", state.monthSummary.income, state.monthSummary.expense)
    }
    if (state.groups.isEmpty()) {
      item { EmptyCard("还没有记录", "记一笔后，这里会按日期排好最近的明细。") }
    }
    state.groups.forEach { group ->
      item {
        Text(group.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      }
      items(group.items, key = { it.transaction.id }) { item ->
        TransactionRow(item)
      }
    }
  }
}

@Composable
private fun AssetsScreen(state: AppState, repository: SextantRepository, onSaved: () -> Unit) {
  var showAddAccount by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("资产全景", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text(formatCurrency(state.netAssets), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
          Text("现金、银行卡和钱包账户统一看余额。", color = SextantColors.TextMuted)
        }
      }
    }
    item {
      Button(onClick = { showAddAccount = true }, modifier = Modifier.fillMaxWidth()) {
        Text("添加账户")
      }
    }
    if (state.accounts.isEmpty()) {
      item { EmptyCard("暂无账户", "添加现金、银行卡或钱包后，可以在记账时同步余额。") }
    } else {
      items(state.accounts, key = { it.id }) { account ->
        AccountRow(account)
      }
    }
  }

  if (showAddAccount) {
    AddAccountDialog(
      onDismiss = { showAddAccount = false },
      onSubmit = { name, type, balance ->
        repository.addAccount(name, type, balance)
        showAddAccount = false
        onSaved()
      },
    )
  }
}

@Composable
private fun AnalyticsScreen(state: AppState) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { SummaryCard("本月收支", state.monthSummary.income, state.monthSummary.expense) }
    item { SectionTitle("支出结构") }
    if (state.categoryBreakdown.isEmpty()) {
      item { EmptyCard("暂无分析", "录入支出后会展示分类占比。") }
    } else {
      items(state.categoryBreakdown) { item ->
        ProgressRow(item.categoryName, item.amount, state.monthSummary.expense, argbColor(item.color))
      }
    }
    item { SectionTitle("预算进度") }
    if (state.budgets.isEmpty()) {
      item { EmptyCard("尚未设置预算", "在预算页为常用分类设置月预算。") }
    } else {
      items(state.budgets) { item ->
        ProgressRow(item.categoryName, item.spent, item.amount, argbColor(item.color))
      }
    }
  }
}

@Composable
private fun BudgetScreen(state: AppState, repository: SextantRepository, onSaved: () -> Unit) {
  var selectedCategoryId by remember { mutableStateOf(state.categories.firstOrNull { it.type == TransactionType.Expense }?.id) }
  var amountText by remember { mutableStateOf("") }
  val expenseCategories = state.categories.filter { it.type == TransactionType.Expense }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { SectionTitle("月度预算") }
    item {
      FlowChips(
        items = expenseCategories,
        selectedId = selectedCategoryId,
        label = { it.name },
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

@Composable
private fun SettingsScreen(repository: SextantRepository) {
  var csv by remember { mutableStateOf<String?>(null) }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { SectionTitle("数据管理") }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("CSV 备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("导出当前本地账本数据。", color = SextantColors.TextMuted)
          Button(onClick = { csv = repository.exportCsv() }) {
            Text("生成 CSV")
          }
        }
      }
    }
  }
  if (csv != null) {
    AlertDialog(
      onDismissRequest = { csv = null },
      confirmButton = { TextButton(onClick = { csv = null }) { Text("完成") } },
      title = { Text("CSV 已生成") },
      text = { Text(csv.orEmpty().lineSequence().take(8).joinToString("\n")) },
    )
  }
}

@Composable
private fun QuickEntryDialog(
  state: AppState,
  onDismiss: () -> Unit,
  onSubmit: (TransactionType, Double, String?, String?, String?, String) -> Unit,
) {
  var type by remember { mutableStateOf(TransactionType.Expense) }
  var amountText by remember { mutableStateOf("") }
  var categoryId by remember { mutableStateOf(state.categories.firstOrNull { it.type == TransactionType.Expense }?.id) }
  var accountId by remember { mutableStateOf(state.accounts.firstOrNull()?.id) }
  var paymentMethodId by remember { mutableStateOf(state.paymentMethods.firstOrNull()?.id) }
  var description by remember { mutableStateOf("") }
  val categories = state.categories.filter { it.type == type || type == TransactionType.Transfer }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(onClick = {
        val amount = amountText.toDoubleOrNull()
        if (amount != null && amount > 0) {
          onSubmit(type, amount, categoryId, accountId, paymentMethodId, description)
        }
      }) {
        Text("保存")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    title = { Text("快速记账") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TransactionType.entries.forEach { option ->
            FilterChip(
              selected = type == option,
              onClick = {
                type = option
                categoryId = state.categories.firstOrNull { it.type == option }?.id
              },
              label = { Text(option.label) },
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
        FlowChips(categories, categoryId, { it.name }, { argbColor(it.color) }) { categoryId = it.id }
        FlowChips(state.accounts, accountId, { it.name }, { SextantColors.Accent }) { accountId = it.id }
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

@Composable
private fun AddAccountDialog(
  onDismiss: () -> Unit,
  onSubmit: (String, String, Double) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var type by remember { mutableStateOf("cash") }
  var balanceText by remember { mutableStateOf("") }
  val types = listOf("cash" to "现金", "bank" to "银行卡", "wallet" to "钱包", "credit" to "信用卡")

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(onClick = {
        val balance = balanceText.toDoubleOrNull() ?: 0.0
        if (name.isNotBlank()) {
          onSubmit(name, type, balance)
        }
      }) {
        Text("保存")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    title = { Text("添加账户") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("账户名称") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          types.forEach { option ->
            FilterChip(
              selected = type == option.first,
              onClick = { type = option.first },
              label = { Text(option.second) },
            )
          }
        }
        OutlinedTextField(
          value = balanceText,
          onValueChange = { balanceText = it },
          label = { Text("当前余额") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
      }
    },
  )
}

@Composable
private fun SummaryCard(title: String, income: Double, expense: Double) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("收入", income, SextantColors.Success)
        Metric("支出", expense, SextantColors.Danger)
        Metric("结余", income - expense, SextantColors.Accent)
      }
    }
  }
}

@Composable
private fun Metric(label: String, value: Double, color: Color) {
  Column {
    Text(label, color = SextantColors.TextMuted, style = MaterialTheme.typography.labelMedium)
    Text(formatCurrency(value), color = color, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun AccountRow(account: Account) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(SextantColors.Accent)
        Spacer(Modifier.width(12.dp))
        Column {
          Text(account.name, fontWeight = FontWeight.SemiBold)
          Text(account.type.accountTypeLabel, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
      }
      Text(formatCurrency(account.balance), fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun TransactionRow(item: TransactionListItem) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(argbColor(item.categoryColor))
        Spacer(Modifier.width(12.dp))
        Column {
          Text(item.categoryName, fontWeight = FontWeight.SemiBold)
          Text(
            "${item.accountName} · ${item.paymentMethodName}",
            color = SextantColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      Text(
        text = signedAmount(item),
        color = if (item.transaction.type == TransactionType.Expense) SextantColors.Danger else SextantColors.Success,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun ProgressRow(label: String, value: Double, total: Double, color: Color) {
  val percent = if (total <= 0.0) 0f else (value / total).toFloat().coerceIn(0f, 1f)
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text("${formatCurrency(value)} / ${formatCurrency(total)}", color = SextantColors.TextMuted)
      }
      Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(SextantColors.SurfaceStrong)) {
        Box(Modifier.fillMaxWidth(percent).height(8.dp).clip(CircleShape).background(color))
      }
    }
  }
}

@Composable
private fun EmptyCard(title: String, description: String) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.SurfaceAlt)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(description, color = SextantColors.TextMuted)
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun ColorDot(color: Color) {
  Canvas(Modifier.size(14.dp)) {
    drawCircle(color)
  }
}

@Composable
private fun <T> FlowChips(
  items: List<T>,
  selectedId: String?,
  label: (T) -> String,
  color: (T) -> Color,
  onSelect: (T) -> Unit,
) where T : Any {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    items.take(4).forEach { item ->
      val id = when (item) {
        is Category -> item.id
        is PaymentMethod -> item.id
        is Account -> item.id
        else -> ""
      }
      AssistChip(
        onClick = { onSelect(item) },
        label = { Text(label(item)) },
        leadingIcon = { ColorDot(color(item)) },
        border = if (id == selectedId) null else androidx.compose.material3.AssistChipDefaults.assistChipBorder(enabled = true),
      )
    }
  }
}

private val TransactionType.label: String
  get() = when (this) {
    TransactionType.Expense -> "支出"
    TransactionType.Income -> "收入"
    TransactionType.Transfer -> "转账"
  }

private val String.accountTypeLabel: String
  get() = when (this) {
    "bank" -> "银行卡"
    "wallet" -> "钱包"
    "credit" -> "信用卡"
    else -> "现金"
  }

private fun signedAmount(item: TransactionListItem): String {
  val sign = if (item.transaction.type == TransactionType.Expense) "-" else "+"
  return sign + formatCurrency(item.transaction.amount)
}

private fun formatCurrency(value: Double): String {
  return NumberFormat.getCurrencyInstance(Locale.CHINA).format(value)
}
