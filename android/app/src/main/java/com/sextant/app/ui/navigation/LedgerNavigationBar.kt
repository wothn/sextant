package com.sextant.app.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.components.SextantNavigationBar
import com.sextant.app.ui.components.SextantNavigationItem

/**
 * 底部导航：M3 Expressive 风格的短导航栏，选中项带药丸形指示条。
 *
 * 为什么不用 material3 的 `ShortNavigationBar`：它只有在作为 `bottomBar` 的直接内容时才按
 * 条目数布局；一旦被包进外层 Layout（哪怕给足整屏宽度），就只渲染一个居中的条目——
 * 4 个目的地只剩 1 个可见。在 1.4.0 stable 与 1.5.0-alpha18 上都能复现，所以导航栏按 M3
 * 短导航栏的规格（64dp 高、药丸指示条、图标 24dp、标签 labelMedium）自绘。
 *
 * 导航层级与目的地没有变化，仍然是概览 / 分析 / 计划 / 设置。「计划」里再分预算与周期支出
 * 两个一级视图（见 `ui/screens/PlansScreen.kt`），底栏本身不跟着拆。
 */
@Composable
fun LedgerNavigationBar(
  selectedTab: Tab,
  onTabSelected: (Tab) -> Unit,
  modifier: Modifier = Modifier,
) {
  SextantNavigationBar(modifier = modifier) {
    Tab.entries.forEach { tab ->
      SextantNavigationItem(
        selected = tab == selectedTab,
        onClick = { onTabSelected(tab) },
        icon = {
          Icon(tab.icon, contentDescription = null, modifier = Modifier.size(24.dp))
        },
        label = {
          Text(tab.title, style = MaterialTheme.typography.labelMedium)
        },
      )
    }
  }
}
