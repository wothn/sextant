package com.sextant.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.categoryColorOptions
import com.sextant.app.ui.util.displayName
import com.sextant.app.ui.util.label
import kotlinx.coroutines.launch

@Composable
fun CategoryManagement(
  categories: List<Category>,
  repository: SextantRepository,
  onNavigateBack: () -> Unit,
) {
  var editingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
  val editingCategory = categories.firstOrNull { it.id == editingCategoryId }
  var pendingArchiveId by rememberSaveable { mutableStateOf<String?>(null) }
  val pendingArchive = categories.firstOrNull { it.id == pendingArchiveId }
  val snackbar = LocalSnackbarHostState.current
  val scope = rememberCoroutineScope()
  val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
  var parentForNewChildId by rememberSaveable { mutableStateOf<String?>(null) }
  val parentForNewChild = categories.firstOrNull { it.id == parentForNewChildId }
  var showAddRoot by rememberSaveable { mutableStateOf(false) }
  // 当前浏览到的层级由这里唯一持有，标题栏和列表都读它：页面标题显示父分类名时，浏览器
  // 就不再画自己的返回行，屏幕上始终只有一个返回箭头、一个「你在哪」。
  var browsingParentId by rememberSaveable { mutableStateOf<String?>(null) }
  val browsingParent = categories.firstOrNull { it.id == browsingParentId }
  // 浏览到的父分类被停用后，标题栏不能继续停在一个已经不存在的层级上。
  LaunchedEffect(categories, browsingParentId) {
    if (browsingParentId != null && browsingParent == null) browsingParentId = null
  }
  // 写分类是挂起调用（Room 不允许主线程访问数据库），而对话框的 onSave 不是协程。
  val saveAction = rememberSaveAction("categories") {
    showAddRoot = false
    parentForNewChildId = null
    editingCategoryId = null
  }

  BackHandler(enabled = browsingParent != null) { browsingParentId = null }
  val pages = rememberSaveableStateHolder()
  Column(Modifier.fillMaxSize()) {
    SextantManagementHeader(
      title = browsingParent?.name ?: "分类管理",
      backDescription = if (browsingParent != null) "返回全部分类" else "返回设置",
      onNavigateBack = { if (browsingParent != null) browsingParentId = null else onNavigateBack() },
      actionLabel = if (browsingParent != null) "新增子分类" else "新增分类",
      onAction = { if (browsingParent != null) parentForNewChildId = browsingParent.id else showAddRoot = true },
    )
    pages.SaveableStateProvider(browsingParentId ?: "root") {
      val listState = rememberLazyListState()
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp + LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        if (categories.isEmpty()) {
          item { EmptyCard("暂无分类", "新增收入或支出分类后，就可以在记账时选择。") }
        } else {
          val groups = if (browsingParent != null) listOf(browsingParent.type) else listOf(TransactionType.Expense, TransactionType.Income)
          groups.forEach { type ->
            val group = categories.filter { it.type == type }
            val count = group.count { it.parentCategoryId == browsingParent?.id }
            if (count > 0) {
              item(key = "categories:${browsingParent?.id}:$type") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("${if (browsingParent != null) "子分类" else type.label + "分类"} · $count 个",
                    color = SextantColors.TextMuted, style = MaterialTheme.typography.labelLarge)
                  SextantSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    CategoryBrowser(
                      categories = group, parentId = browsingParentId,
                      onParentChange = { browsingParentId = it },
                      actions = { category ->
                        CategoryActions(category, categories,
                          onAddChild = { parentForNewChildId = category.id },
                          onEdit = { editingCategoryId = category.id },
                          onArchive = { pendingArchiveId = category.id })
                      },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showAddRoot) {
    CategoryDialog(
      categories = categories,
      initial = null,
      fixedParent = null,
      saving = saveAction.busy,
      onDismiss = { showAddRoot = false },
      onSubmit = { name, type, color, includeInSpending, parentCategoryId ->
        saveAction.launch {
          repository.addCategory(name, type, color, includeInSpending, parentCategoryId)
        }
      },
    )
  }

  parentForNewChild?.let { parent ->
    CategoryDialog(
      categories = categories,
      initial = null,
      fixedParent = parent,
      saving = saveAction.busy,
      onDismiss = { parentForNewChildId = null },
      onSubmit = { name, type, color, includeInSpending, parentCategoryId ->
        saveAction.launch {
          repository.addCategory(name, type, color, includeInSpending, parentCategoryId)
        }
      },
    )
  }

  editingCategory?.let { category ->
    CategoryDialog(
      categories = categories,
      initial = category,
      fixedParent = null,
      saving = saveAction.busy,
      onDismiss = { editingCategoryId = null },
      onSubmit = { name, type, color, includeInSpending, parentCategoryId ->
        saveAction.launch {
          repository.updateCategory(category.id, name, type, color, includeInSpending, parentCategoryId)
        }
      },
    )
  }

  pendingArchive?.let { category ->
    SextantAlertDialog(
      onDismissRequest = { pendingArchiveId = null },
      title = { Text("停用「${category.name}」？") },
      text = { Text("停用后它和它的子分类不再出现在记账与预算里。可以立刻撤销，也可以之后通过备份恢复。") },
      confirmButton = {
        SextantTextButton(
          onClick = {
            pendingArchiveId = null
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
            saveAction.launch { repository.archiveCategory(category.id) }
            scope.launch {
              val result = snackbar.showSnackbar(
                message = "已停用「${category.name}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long,
              )
              if (result == SnackbarResult.ActionPerformed) {
                repository.restoreCategory(category.id)
              }
            }
          },
          contentColor = SextantColors.Danger,
        ) { Text("停用") }
      },
      dismissButton = {
        SextantTextButton(onClick = { pendingArchiveId = null }) { Text("取消") }
      },
    )
  }
}

@Composable
private fun CategoryActions(
  category: Category,
  categories: List<Category>,
  onAddChild: () -> Unit,
  onEdit: () -> Unit,
  onArchive: () -> Unit,
) {
  var showActions by rememberSaveable { mutableStateOf(false) }
  Box {
    SextantIconButton(onClick = { showActions = true }) {
      Icon(Icons.Outlined.MoreVert, contentDescription = "管理${category.displayName(categories)}")
    }
    SextantDropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
      if (category.parentCategoryId == null) {
        SextantDropdownMenuItem(text = { Text("添加子分类") }, onClick = { showActions = false; onAddChild() })
      }
      SextantDropdownMenuItem(text = { Text("编辑") }, onClick = { showActions = false; onEdit() })
      SextantDropdownMenuItem(text = { Text("停用", color = SextantColors.Danger) },
        onClick = { showActions = false; onArchive() })
    }
  }
}
@Composable
private fun CategoryDialog(
  categories: List<Category>,
  initial: Category?,
  fixedParent: Category?,
  saving: Boolean = false,
  onDismiss: () -> Unit,
  onSubmit: (String, TransactionType, Long, Boolean, String?) -> Unit,
) {
  var name by rememberSaveable(initial?.id, fixedParent?.id) { mutableStateOf(initial?.name.orEmpty()) }
  var type by rememberSaveable(initial?.id, fixedParent?.id) { mutableStateOf(fixedParent?.type ?: initial?.type ?: TransactionType.Expense) }
  var color by rememberSaveable(initial?.id, fixedParent?.id) { mutableStateOf(initial?.color ?: fixedParent?.color ?: categoryColorOptions.first().first) }
  var includeInSpending by rememberSaveable(initial?.id, fixedParent?.id) {
    mutableStateOf(initial?.includeInSpending ?: fixedParent?.includeInSpending ?: true)
  }
  var parentCategoryId by rememberSaveable(initial?.id, fixedParent?.id) { mutableStateOf(fixedParent?.id ?: initial?.parentCategoryId) }
  val parentOptions = categories.filter {
    it.parentCategoryId == null && it.type == type && it.id != initial?.id
  }

  SextantAlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      SextantButton(enabled = name.isNotBlank() && !saving, onClick = {
        if (name.isNotBlank()) {
          onSubmit(name, type, color, includeInSpending, fixedParent?.id ?: parentCategoryId)
        }
      }) {
        if (saving) {
          SextantLoadingIndicator(size = 18.dp, color = SextantColors.TextMuted,
            containerColor = SextantColors.SurfaceContainerHighest)
        }
        Text("保存")
      }
    },
    dismissButton = { SextantTextButton(onClick = onDismiss) { Text("取消") } },
    title = { Text(if (initial == null) "添加分类" else "编辑分类") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SextantTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(TransactionType.Expense, TransactionType.Income).forEach { option ->
            SextantFilterChip(
              selected = type == option,
              enabled = fixedParent == null,
              onClick = {
                type = option
                parentCategoryId = null
              },
              label = { Text(option.label) },
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
            icon = { it?.let { item -> categoryIcon(item.name) } ?: Icons.Outlined.Remove },
            onSelect = { parentCategoryId = it?.id },
          )
        } else {
          Text("上级分类：${fixedParent.name}", color = SextantColors.TextMuted)
        }
        Row(
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = includeInSpending,
            enabled = !saving,
            role = Role.Switch,
            onValueChange = { includeInSpending = it },
          ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("计入支出统计")
          SextantSwitch(
            checked = includeInSpending,
            onCheckedChange = null,
            enabled = !saving,
          )
        }
      }
    },
  )
}
