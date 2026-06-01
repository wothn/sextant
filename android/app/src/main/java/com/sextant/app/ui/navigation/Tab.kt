package com.sextant.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val title: String, val icon: ImageVector) {
  Home("记账", Icons.Outlined.Home),
  Analytics("报表", Icons.Outlined.PieChart),
  Discover("发现", Icons.Outlined.Explore),
  Settings("设置", Icons.Outlined.Settings),
}
