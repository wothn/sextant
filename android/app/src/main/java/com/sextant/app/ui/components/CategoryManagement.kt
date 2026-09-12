package com.sextant.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.categoryColorOptions
import com.sextant.app.ui.util.displayName
import com.sextant.app.ui.util.label

@Composable
fun CategoryManagement(
  categories: List<Category>,
  repository: SextantRepository,
  onChanged: () -> Unit,
  onNavigateBack: (() -> Unit)? = null,
) {
  var editingCategory by remember { mutableStateOf<Category?>(null) }
  var parentForNewChild by remember { mutableStateOf<Category?>(null) }
  var showAddRoot by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onNavigateBack != null) {
          SextantIconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
          }
        }
        SectionTitle("分类管理")
      }
      SextantTextButton(onClick = { showAddRoot = true }) { Text("新增分类") }
    }
    if (categories.isEmpty()) {
      EmptyCard("暂无分类", "新增收入或支出分类后，就可以在记账时选择。")
    } else {
      CategoryBrowser(categories = categories, actions = { category ->
        CategoryActions(
          category = category,
          categories = categories,
          onAddChild = { parentForNewChild = category },
          onEdit = { editingCategory = category },
          onArchive = { repository.archiveCategory(category.id); onChanged() },
        )
      })
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
private fun CategoryActions(
  category: Category,
  categories: List<Category>,
  onAddChild: () -> Unit,
  onEdit: () -> Unit,
  onArchive: () -> Unit,
) {
  var showActions by remember { mutableStateOf(false) }
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

  SextantAlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      SextantButton(onClick = {
        if (name.isNotBlank()) {
          onSubmit(name, type, color, includeInSpending, fixedParent?.id ?: parentCategoryId)
        }
      }) {
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
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("计入支出统计")
          SextantSwitch(
            checked = includeInSpending,
            onCheckedChange = { includeInSpending = it },
          )
        }
      }
    },
  )
}

