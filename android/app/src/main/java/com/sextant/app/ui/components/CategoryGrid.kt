package com.sextant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor

/**
 * 三列分类方格，记账弹窗和筛选弹窗共用同一套外观。
 *
 * [selectedId] 高亮已选分类，点击整格即触发 [onSelect]。[leading] 会排在
 * 第一行最前面，用来塞“全部分类”这类不属于任何分类的入口。
 */
@Composable
fun CategoryGrid(
  categories: List<Category>,
  selectedId: String?,
  modifier: Modifier = Modifier,
  leading: (@Composable () -> Unit)? = null,
  onSelect: (Category) -> Unit,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    var remaining = categories
    if (leading != null) {
      // 入口格和分类格混排，宽度保持一致。
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { leading() }
        categories.take(2).forEach { category ->
          CategoryTile(category, category.id == selectedId, Modifier.weight(1f)) { onSelect(category) }
        }
        repeat((2 - categories.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
      }
      remaining = categories.drop(2)
    }
    remaining.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { category ->
          CategoryTile(category, category.id == selectedId, Modifier.weight(1f)) { onSelect(category) }
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

/** 单个分类方格，供 [CategoryGrid] 和需要单独摆放的调用方使用。 */
@Composable
fun CategoryTile(category: Category, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  Surface(
    modifier = modifier.clip(RoundedCornerShape(16.dp))
      .selectable(selected, role = Role.RadioButton, onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = if (selected) SextantColors.AccentSoft else SextantColors.Surface,
    border = if (selected) BorderStroke(1.dp, SextantColors.AccentMuted) else null,
  ) {
    Column(
      Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      CategoryIcon(categoryIcon(category.name), argbColor(category.color))
      Text(category.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        color = if (selected) SextantColors.AccentStrong else SextantColors.Text, maxLines = 2,
        overflow = TextOverflow.Ellipsis)
    }
  }
}

/** 圆形底色加图标，分类和来源共用。 */
@Composable
fun CategoryIcon(icon: ImageVector, color: Color) {
  Box(Modifier.size(36.dp).background(color.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
  }
}

/** 按分类名猜一个图标，猜不到时退回到通用分类图标。 */
fun categoryIcon(name: String): ImageVector = when {
  listOf("餐", "食", "饭", "外卖").any(name::contains) -> Icons.Outlined.Restaurant
  listOf("茶", "咖啡").any(name::contains) -> Icons.Outlined.LocalCafe
  listOf("交通", "出行", "地铁").any(name::contains) -> Icons.Outlined.DirectionsBus
  listOf("购物", "超市").any(name::contains) -> Icons.Outlined.ShoppingBag
  listOf("住房", "房租", "家").any(name::contains) -> Icons.Outlined.Home
  listOf("学习", "教育").any(name::contains) -> Icons.Outlined.School
  listOf("娱乐", "游戏").any(name::contains) -> Icons.Outlined.SportsEsports
  listOf("人情", "礼").any(name::contains) -> Icons.Outlined.CardGiftcard
  listOf("工资", "基金", "储蓄", "还款").any(name::contains) -> Icons.Outlined.AccountBalanceWallet
  else -> Icons.Outlined.Category
}
