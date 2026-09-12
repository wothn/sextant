package com.sextant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.label

/** Shared hierarchy and rows for entry, filtering and category management. */
@Composable
fun CategoryBrowser(
  categories: List<Category>,
  selectedId: String? = null,
  allowAll: Boolean = false,
  onSelect: ((Category?) -> Unit)? = null,
  actions: (@Composable (Category) -> Unit)? = null,
) {
  var parentId by remember { mutableStateOf<String?>(null) }
  val parent = categories.firstOrNull { it.id == parentId }
  val visible = categories.filter { it.parentCategoryId == parent?.id }
  BackHandler(enabled = parent != null) { parentId = null }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (parent != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        SextantIconButton(onClick = { parentId = null }) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回全部分类")
        }
        Text(parent.name, style = MaterialTheme.typography.titleSmall, color = SextantColors.Text)
      }
    }
    if (onSelect != null && (allowAll || parent != null)) {
      CategoryListRow(
        name = parent?.let { if (allowAll) "全部${it.name}" else "使用${it.name}" } ?: "全部分类",
        color = parent?.let { argbColor(it.color) } ?: SextantColors.TextMuted,
        selected = selectedId == parent?.id,
        onClick = { onSelect(parent) },
      )
    }
    if (parent != null && actions != null) {
      CategoryListRow(parent.name, argbColor(parent.color), detail = parent.type.label,
        onClick = {}, trailing = { actions(parent) })
    }
    visible.forEach { category ->
      key(category.id) {
        val children = categories.count { it.parentCategoryId == category.id }
        CategoryListRow(
          name = category.name,
          color = argbColor(category.color),
          detail = listOfNotNull(category.type.label, if (children > 0) "$children 个子分类" else null,
            if (actions != null) { if (category.includeInSpending) "计入支出" else "不计支出" } else null).joinToString(" · "),
          selected = onSelect != null && selectedId == category.id,
          opensChildren = children > 0,
          onClick = { if (children > 0) parentId = category.id else onSelect?.invoke(category) },
          trailing = if (actions == null) null else { { actions(category) } },
        )
      }
    }
    if (visible.isEmpty()) Text("暂无可选分类", color = SextantColors.TextMuted)
  }
}

@Composable
private fun CategoryListRow(
  name: String,
  color: Color,
  detail: String? = null,
  selected: Boolean = false,
  opensChildren: Boolean = false,
  onClick: () -> Unit,
  trailing: (@Composable () -> Unit)? = null,
) {
  SextantSelectableSurface(
    selected = selected,
    onClick = onClick,
    role = Role.RadioButton,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      CategoryIcon(categoryIcon(name), color)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SextantColors.TextSubtle)
      }
      if (selected) Icon(Icons.Outlined.Check, "已选", tint = SextantColors.AccentStrong, modifier = Modifier.size(20.dp))
      if (opensChildren) Icon(Icons.Outlined.ChevronRight, "查看子分类", tint = SextantColors.TextSubtle, modifier = Modifier.size(20.dp))
      trailing?.invoke()
    }
  }
}