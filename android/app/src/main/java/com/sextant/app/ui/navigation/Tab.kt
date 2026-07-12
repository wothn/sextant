package com.sextant.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val title: String, val icon: ImageVector) {
  Home("概览", Icons.Outlined.AccountBalanceWallet),
  Analytics("分析", Icons.Outlined.PieChart),
  Discover("预算", Icons.AutoMirrored.Outlined.ReceiptLong),
  Settings("设置", Icons.Outlined.Settings),
}
