package com.sextant.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.ui.theme.argbColor

@Composable
fun CategoryPicker(
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
