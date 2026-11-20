package com.sextant.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(
  val title: String,
  val icon: ImageVector,
  val selectedIcon: ImageVector = icon,
) {
  Home("概览", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
  Analytics("分析", Icons.Outlined.PieChart, Icons.Filled.PieChart),
  Discover("计划", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
  Settings("设置", Icons.Outlined.Settings, Icons.Filled.Settings),
}
