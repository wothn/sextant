package com.sextant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Sextant") },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = SextantColors.Background,
          titleContentColor = SextantColors.Text,
        ),
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        text = { Text("记一笔") },
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        onClick = { showQuickEntry = true },
        containerColor = SextantColors.Accent,
        contentColor = SextantColors.OnAccent,
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
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = SextantColors.AccentStrong,
              selectedTextColor = SextantColors.AccentStrong,
              indicatorColor = SextantColors.AccentSoft,
              unselectedIconColor = SextantColors.TabInactive,
              unselectedTextColor = SextantColors.TextSubtle,
            ),
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
        Tab.Budget -> BudgetScreen(state, repository) { reloadKey++ }
        Tab.Settings -> SettingsScreen(state, repository) { reloadKey++ }
      }
    }
  }

  if (showQuickEntry) {
    QuickEntryDialog(
      state = state,
      onDismiss = { showQuickEntry = false },
      onSubmit = { type, amount, categoryId, paymentMethodId, description ->
        repository.addTransaction(type, amount, categoryId, paymentMethodId, description)
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

@Composable
private fun SettingsScreen(state: AppState, repository: SextantRepository, onChanged: () -> Unit) {
  var csv by remember { mutableStateOf<String?>(null) }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      CategoryManagement(
        categories = state.categories,
        repository = repository,
        onChanged = onChanged,
      )
    }
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
private fun CategoryManagement(
  categories: List<Category>,
  repository: SextantRepository,
  onChanged: () -> Unit,
) {
  var editingCategory by remember { mutableStateOf<Category?>(null) }
  var parentForNewChild by remember { mutableStateOf<Category?>(null) }
  var showAddRoot by remember { mutableStateOf(false) }
  val rootCategories = categories.filter { it.parentCategoryId == null }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      SectionTitle("分类管理")
      TextButton(onClick = { showAddRoot = true }) { Text("新增分类") }
    }
    if (categories.isEmpty()) {
      EmptyCard("暂无分类", "新增收入或支出分类后，就可以在记账时选择。")
    } else {
      rootCategories.forEach { category ->
        CategoryRow(
          category = category,
          categories = categories,
          level = 0,
          onAddChild = { parentForNewChild = category },
          onEdit = { editingCategory = category },
          onArchive = {
            repository.archiveCategory(category.id)
            onChanged()
          },
        )
        categories.filter { it.parentCategoryId == category.id }.forEach { child ->
          CategoryRow(
            category = child,
            categories = categories,
            level = 1,
            onAddChild = { parentForNewChild = child },
            onEdit = { editingCategory = child },
            onArchive = {
              repository.archiveCategory(child.id)
              onChanged()
            },
          )
        }
      }
    }
  }

  if (showAddRoot) {
    CategoryDialog(
      categories = categories,
      initial = null,
      fixedParent = null,
      onDismiss = { showAddRoot = false },
      onSubmit = { name, type, color, includeInSpending, parentCategoryId ->
        repository.addCategory(name, type, color, includeInSpending, parentCategoryId)
        showAddRoot = false
        onChanged()
      },
    )
  }

  parentForNewChild?.let { parent ->
    CategoryDialog(
      categories = categories,
      initial = null,
      fixedParent = parent,
      onDismiss = { parentForNewChild = null },
      onSubmit = { name, type, color, includeInSpending, parentCategoryId ->
        repository.addCategory(name, type, color, includeInSpending, parentCategoryId)
        parentForNewChild = null
        onChanged()
      },
    )
  }

  editingCategory?.let { category ->
    CategoryDialog(
      categories = categories,
      initial = category,
      fixedParent = null,
      onDismiss = { editingCategory = null },
      onSubmit = { name, type, color, includeInSpending, parentCategoryId ->
        repository.updateCategory(category.id, name, type, color, includeInSpending, parentCategoryId)
        editingCategory = null
        onChanged()
      },
    )
  }
}

@Composable
private fun CategoryRow(
  category: Category,
  categories: List<Category>,
  level: Int,
  onAddChild: () -> Unit,
  onEdit: () -> Unit,
  onArchive: () -> Unit,
) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(Modifier.width((level * 20).dp))
        ColorDot(argbColor(category.color))
        Spacer(Modifier.width(12.dp))
        Column {
          Text(category.name, fontWeight = FontWeight.SemiBold)
          Text(
            listOf(category.type.label, if (category.includeInSpending) "计入支出" else "不计支出")
              .joinToString(" · "),
            color = SextantColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      Row {
        if (level == 0) {
          TextButton(onClick = onAddChild) { Text("子分类") }
        }
        IconButton(onClick = onEdit) {
          Icon(Icons.Outlined.Edit, contentDescription = "编辑${category.displayName(categories)}")
        }
        IconButton(onClick = onArchive) {
          Icon(Icons.Outlined.Delete, contentDescription = "停用${category.displayName(categories)}")
        }
      }
    }
  }
}

@Composable
private fun CategoryDialog(
  categories: List<Category>,
  initial: Category?,
  fixedParent: Category?,
  onDismiss: () -> Unit,
  onSubmit: (String, TransactionType, Long, Boolean, String?) -> Unit,
) {
  var name by remember(initial, fixedParent) { mutableStateOf(initial?.name.orEmpty()) }
  var type by remember(initial, fixedParent) { mutableStateOf(fixedParent?.type ?: initial?.type ?: TransactionType.Expense) }
  var color by remember(initial, fixedParent) { mutableStateOf(initial?.color ?: fixedParent?.color ?: categoryColorOptions.first().first) }
  var includeInSpending by remember(initial, fixedParent) {
    mutableStateOf(initial?.includeInSpending ?: fixedParent?.includeInSpending ?: true)
  }
  var parentCategoryId by remember(initial, fixedParent) { mutableStateOf(fixedParent?.id ?: initial?.parentCategoryId) }
  val parentOptions = categories.filter {
    it.parentCategoryId == null && it.type == type && it.id != initial?.id
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(onClick = {
        if (name.isNotBlank()) {
          onSubmit(name, type, color, includeInSpending, fixedParent?.id ?: parentCategoryId)
        }
      }) {
        Text("保存")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    title = { Text(if (initial == null) "添加分类" else "编辑分类") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(TransactionType.Expense, TransactionType.Income).forEach { option ->
            FilterChip(
              selected = type == option,
              enabled = fixedParent == null,
              onClick = {
                type = option
                parentCategoryId = null
              },
              label = { Text(option.label) },
              colors = sextantFilterChipColors(),
            )
          }
        }
        FlowChips(
          items = categoryColorOptions,
          selectedId = color.toString(),
          label = { it.second },
          color = { argbColor(it.first) },
          onSelect = { color = it.first },
        )
        if (fixedParent == null) {
          Text("上级分类", style = MaterialTheme.typography.labelLarge)
          FlowChips(
            items = listOf<Category?>(null) + parentOptions,
            selectedId = parentCategoryId,
            label = { it?.name ?: "无" },
            color = { it?.let { item -> argbColor(item.color) } ?: SextantColors.TextMuted },
            onSelect = { parentCategoryId = it?.id },
          )
        } else {
          Text("上级分类：${fixedParent.name}", color = SextantColors.TextMuted)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("计入支出统计")
          Switch(
            checked = includeInSpending,
            onCheckedChange = { includeInSpending = it },
            colors = sextantSwitchColors(),
          )
        }
      }
    },
  )
}

@Composable
private fun QuickEntryDialog(
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

@Composable
private fun CategoryPicker(
  parentCategories: List<Category>,
  typeCategories: List<Category>,
  selectedParentId: String?,
  selectedCategoryId: String?,
  onSelect: (Category) -> Unit,
) {
  val selectedChildren = selectedParentId?.let { parentId ->
    typeCategories.filter { it.parentCategoryId == parentId }
  }.orEmpty()

  StableCategoryPickerLayout(
    parentCount = parentCategories.size,
    selectedParentIndex = parentCategories.indexOfFirst { it.id == selectedParentId },
    hasChildren = selectedChildren.isNotEmpty(),
  ) {
    parentCategories.forEach { parent ->
      CategoryChip(
        category = parent,
        selected = parent.id == selectedParentId,
        onSelect = { onSelect(parent) },
      )
    }
    if (selectedChildren.isNotEmpty()) {
      FlowChips(
        items = selectedChildren,
        selectedId = selectedCategoryId,
        label = { it.name },
        color = { argbColor(it.color) },
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        useColorContainer = true,
        onSelect = onSelect,
      )
    }
  }
}

@Composable
private fun StableCategoryPickerLayout(
  parentCount: Int,
  selectedParentIndex: Int,
  hasChildren: Boolean,
  content: @Composable () -> Unit,
) {
  val horizontalGap = 8.dp
  val verticalGap = 8.dp

  Layout(content = content) { measurables, constraints ->
    val horizontalGapPx = horizontalGap.roundToPx()
    val verticalGapPx = verticalGap.roundToPx()
    val parentPlaceables = measurables.take(parentCount).map { measurable ->
      measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    }
    val childPlaceable = if (hasChildren) {
      measurables.getOrNull(parentCount)?.measure(constraints.copy(minWidth = 0, minHeight = 0))
    } else {
      null
    }

    val placements = mutableListOf<CategoryPlacement>()
    val rowHeights = mutableListOf<Int>()
    val rowTops = mutableListOf<Int>()
    var x = 0
    var y = 0
    var row = 0
    var rowHeight = 0
    parentPlaceables.forEachIndexed { index, placeable ->
      if (x > 0 && x + placeable.width > constraints.maxWidth) {
        rowHeights += rowHeight
        y += rowHeight + verticalGapPx
        x = 0
        row += 1
        rowHeight = 0
      }
      if (rowTops.size <= row) {
        rowTops += y
      }
      placements += CategoryPlacement(index, x, y, row)
      x += placeable.width + horizontalGapPx
      rowHeight = maxOf(rowHeight, placeable.height)
    }
    if (parentPlaceables.isNotEmpty()) {
      rowHeights += rowHeight
    }

    val selectedRow = if (hasChildren) {
      placements.firstOrNull { it.index == selectedParentIndex }?.row ?: -1
    } else {
      -1
    }

    val parentHeight = if (rowHeights.isEmpty()) 0 else rowTops.last() + rowHeights.last()
    val childExtraHeight = if (childPlaceable != null && selectedRow >= 0) {
      verticalGapPx + childPlaceable.height
    } else {
      0
    }

    layout(constraints.maxWidth, parentHeight + childExtraHeight) {
      val childTop = if (childPlaceable != null && selectedRow >= 0) {
        rowTops[selectedRow] + rowHeights[selectedRow] + verticalGapPx
      } else {
        -1
      }
      placements.forEach { placement ->
        val rowOffset = if (childTop >= 0 && placement.row > selectedRow) childExtraHeight else 0
        parentPlaceables[placement.index].placeRelative(placement.x, placement.y + rowOffset)
      }
      childPlaceable?.placeRelative(0, childTop)
    }
  }
}

private data class CategoryPlacement(
  val index: Int,
  val x: Int,
  val y: Int,
  val row: Int,
)

@Composable
private fun CategoryChip(
  category: Category,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  val itemColor = argbColor(category.color)
  AssistChip(
    onClick = onSelect,
    label = { Text(category.name) },
    colors = AssistChipDefaults.assistChipColors(
      containerColor = if (selected) itemColor else itemColor.copy(alpha = 0.14f),
      labelColor = if (selected) Color.White else itemColor,
    ),
    border = null,
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
            item.paymentMethodName,
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
  modifier: Modifier = Modifier,
  useColorContainer: Boolean = false,
  onSelect: (T) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    items.forEach { item ->
      val id = when (item) {
        is Category -> item.id
        is PaymentMethod -> item.id
        is Pair<*, *> -> item.first.toString()
        null -> null
        else -> ""
      }
      val isSelected = id == selectedId
      val itemColor = color(item)
      AssistChip(
        onClick = { onSelect(item) },
        label = { Text(label(item)) },
        leadingIcon = if (useColorContainer) null else {
          { ColorDot(itemColor) }
        },
        colors = AssistChipDefaults.assistChipColors(
          containerColor = when {
            useColorContainer && isSelected -> itemColor
            useColorContainer -> itemColor.copy(alpha = 0.14f)
            isSelected -> SextantColors.AccentSoft
            else -> SextantColors.Surface
          },
          labelColor = when {
            useColorContainer && isSelected -> Color.White
            useColorContainer -> itemColor
            isSelected -> SextantColors.AccentStrong
            else -> SextantColors.Text
          },
          leadingIconContentColor = SextantColors.TextMuted,
        ),
        border = if (useColorContainer || isSelected) null else AssistChipDefaults.assistChipBorder(enabled = true),
      )
    }
  }
}

@Composable
private fun sextantFilterChipColors() = FilterChipDefaults.filterChipColors(
  selectedContainerColor = SextantColors.AccentSoft,
  selectedLabelColor = SextantColors.AccentStrong,
  selectedLeadingIconColor = SextantColors.AccentStrong,
  containerColor = SextantColors.Surface,
  labelColor = SextantColors.Text,
)

@Composable
private fun sextantSwitchColors() = SwitchDefaults.colors(
  checkedThumbColor = SextantColors.OnAccent,
  checkedTrackColor = SextantColors.Accent,
  checkedBorderColor = SextantColors.Accent,
  uncheckedThumbColor = SextantColors.Surface,
  uncheckedTrackColor = SextantColors.SurfaceStrong,
  uncheckedBorderColor = SextantColors.Border,
)

private val TransactionType.label: String
  get() = when (this) {
    TransactionType.Expense -> "支出"
    TransactionType.Income -> "收入"
    TransactionType.Transfer -> "转账"
  }

private fun Category.displayName(categories: List<Category>): String {
  val parentName = parentCategoryId?.let { parentId -> categories.firstOrNull { it.id == parentId }?.name }
  return if (parentName == null) name else "$parentName / $name"
}

private val categoryColorOptions = listOf(
  0xFFFF7043L to "橙",
  0xFF42A5F5L to "蓝",
  0xFFF97316L to "橘",
  0xFF66BB6AL to "绿",
  0xFF26A69AL to "青",
  0xFFD97706L to "金",
)

private fun signedAmount(item: TransactionListItem): String {
  val sign = if (item.transaction.type == TransactionType.Expense) "-" else "+"
  return sign + formatCurrency(item.transaction.amount)
}

private fun formatCurrency(value: Double): String {
  return NumberFormat.getCurrencyInstance(Locale.CHINA).format(value)
}
