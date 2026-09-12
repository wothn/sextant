package com.sextant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayName

/**
 * 两级分类选择器，外观与记账弹窗的分类面板保持一致。
 *
 * 默认铺开一级分类；选中带下级的一级分类后，在下面追加它自己的入口和二级分类，
 * 这样既能按大类筛选，也能精确到二级分类。选到二级分类后由调用方收起整个分区。
 */
@Composable
fun CategoryPicker(
  parentCategories: List<Category>,
  childCategories: List<Category>,
  selectedParentId: String?,
  selectedCategoryId: String?,
  onSelect: (Category) -> Unit,
  onClear: () -> Unit,
  allCategories: List<Category> = parentCategories + childCategories,
  modifier: Modifier = Modifier,
) {
  if (parentCategories.isEmpty()) {
    Text("暂无可选分类", color = SextantColors.TextMuted)
    return
  }

  val activeParent = parentCategories.firstOrNull { it.id == selectedParentId }
  val children = childCategories.filter { it.parentCategoryId == activeParent?.id && activeParent != null }
  val parentItselfSelected =
    selectedCategoryId != null && selectedCategoryId == activeParent?.id && children.isNotEmpty()

  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (activeParent == null) {
      CategoryGrid(
        categories = parentCategories,
        selectedId = selectedCategoryId,
        leading = { AllCategoriesTile(selected = selectedCategoryId == null, onClick = onClear) },
        onSelect = onSelect,
      )
    } else {
      // 选中的是二级分类时，一级分类格只作为入口，不再高亮，避免出现两个选中态。
      CategoryGrid(
        categories = listOf(activeParent),
        selectedId = selectedCategoryId?.takeIf { it == activeParent.id },
        onSelect = onSelect,
      )
      HorizontalDivider(color = SextantColors.Border)
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          activeParent.name,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = SextantColors.Text,
        )
        TextButton(onClick = onClear) { Text("返回全部") }
      }
      if (parentItselfSelected) {
        UseParentTile(activeParent, allCategories) { onSelect(activeParent) }
      }
      if (children.isNotEmpty()) {
        CategoryGrid(categories = children, selectedId = selectedCategoryId, onSelect = onSelect)
      }
    }
  }
}

/** “全部分类”入口格，外观与普通分类格一致，只是用通用图标。 */
@Composable
private fun AllCategoriesTile(selected: Boolean, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .background(if (selected) SextantColors.AccentSoft else SextantColors.Surface)
      .clickable(onClickLabel = "选择全部分类", onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    CategoryIcon(categoryIcon(""), SextantColors.TextMuted)
    Text(
      "全部分类",
      style = MaterialTheme.typography.labelLarge,
      textAlign = TextAlign.Center,
      color = if (selected) SextantColors.AccentStrong else SextantColors.Text,
      maxLines = 2,
    )
  }
}

/** 按一级分类整体筛选，而不是它的某个二级分类。外观与分类格一致，占一格宽。 */
@Composable
private fun UseParentTile(parent: Category, allCategories: List<Category>, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Column(
      modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(16.dp))
        .background(SextantColors.AccentSoft)
        .clickable(onClickLabel = "按 ${parent.name} 筛选", onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      CategoryIcon(categoryIcon(parent.name), argbColor(parent.color))
      Text(
        parent.displayName(allCategories),
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
        color = SextantColors.AccentStrong,
        maxLines = 2,
      )
    }
    Spacer(Modifier.weight(1f))
    Spacer(Modifier.weight(1f))
  }
}
