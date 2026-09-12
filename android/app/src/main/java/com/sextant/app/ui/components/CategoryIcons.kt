package com.sextant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 小圆角淡色底加图标，分类和来源共用。
 *
 * 分类身份的唯一写法就是「淡色底块 + 分类色的图标」。列表用 36dp 块，chip 等紧凑位置
 * 用较小尺寸，但底块形状、透明度必须一致，否则同一分类在两处看起来像两个东西。
 */
@Composable
fun CategoryIcon(icon: ImageVector, color: Color, size: Dp = 36.dp, iconSize: Dp = 20.dp) {
  Box(Modifier.size(size).background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
  }
}

/** 按分类名猜一个图标，猜不到时退回到通用分类图标。 */
fun categoryIcon(name: String): ImageVector = when {
  listOf("餐", "食", "饭", "外卖").any(name::contains) -> Icons.Outlined.Restaurant
  listOf("茶", "咖啡").any(name::contains) -> Icons.Outlined.LocalCafe
  listOf("交通", "出行", "地铁").any(name::contains) -> Icons.Outlined.DirectionsBus
  listOf("购物", "超市", "服饰").any(name::contains) -> Icons.Outlined.ShoppingBag
  listOf("住房", "房租", "家").any(name::contains) -> Icons.Outlined.Home
  listOf("医疗", "健康").any(name::contains) -> Icons.Outlined.LocalHospital
  listOf("学习", "教育").any(name::contains) -> Icons.Outlined.School
  listOf("娱乐", "游戏").any(name::contains) -> Icons.Outlined.SportsEsports
  listOf("人情", "礼").any(name::contains) -> Icons.Outlined.CardGiftcard
  listOf("工资", "基金", "储蓄", "还款").any(name::contains) -> Icons.Outlined.AccountBalanceWallet
  else -> Icons.Outlined.Category
}
