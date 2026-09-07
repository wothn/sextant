package com.sextant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor

@Composable
fun CategoryPicker(
  parentCategories: List<Category>,
  typeCategories: List<Category>,
  selectedParentId: String?,
  selectedCategoryId: String?,
  onSelect: (Category) -> Unit,
) {
  val children = typeCategories.filter { it.parentCategoryId == selectedParentId && selectedParentId != null }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      parentCategories.forEach { category ->
        CategoryChip(category, category.id == selectedParentId) { onSelect(category) }
      }
    }
    if (children.isNotEmpty()) {
      HorizontalDivider(color = SextantColors.Border)
      Text(
        "${parentCategories.firstOrNull { it.id == selectedParentId }?.name.orEmpty()} · 子分类",
        style = MaterialTheme.typography.labelMedium,
        color = SextantColors.TextMuted,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        children.forEach { category ->
          CategoryChip(category, category.id == selectedCategoryId) { onSelect(category) }
        }
      }
    }
    if (parentCategories.isEmpty()) {
      Text("暂无可选分类", color = SextantColors.TextMuted)
    }
  }
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onSelect: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onSelect,
    label = { Text(category.name) },
    leadingIcon = { ColorDot(argbColor(category.color)) },
    colors = sextantFilterChipColors(),
  )
}
