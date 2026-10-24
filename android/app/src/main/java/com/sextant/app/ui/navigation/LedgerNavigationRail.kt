package com.sextant.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.components.SextantMark
import com.sextant.app.ui.theme.SextantColors

/**
 * 宽屏（>= 600dp）的导航栏：M3 NavigationRail。
 *
 * 手机停在底栏，平板和横屏改用左侧轨道，把横向空间让给内容；条目与底栏共用同一组
 * 目的地、同一套选中图标（Filled / Outlined）。
 */
@Composable
fun LedgerNavigationRail(
  selectedTab: Tab,
  onTabSelected: (Tab) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavigationRail(
    modifier = modifier,
    containerColor = SextantColors.SurfaceContainer,
    header = { SextantMark(Modifier.padding(vertical = 12.dp)) },
  ) {
    Tab.entries.forEach { tab ->
      val selected = tab == selectedTab
      NavigationRailItem(
        selected = selected,
        onClick = { onTabSelected(tab) },
        icon = {
          Icon(
            if (selected) tab.selectedIcon else tab.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
          )
        },
        label = { Text(tab.title, style = MaterialTheme.typography.labelMedium) },
        alwaysShowLabel = true,
        colors = NavigationRailItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
          selectedTextColor = SextantColors.Text,
          indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
          unselectedIconColor = SextantColors.TextSubtle,
          unselectedTextColor = SextantColors.TextSubtle,
        ),
      )
    }
  }
}
