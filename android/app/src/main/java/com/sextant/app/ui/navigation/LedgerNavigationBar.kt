package com.sextant.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantColors

@Composable
fun LedgerNavigationBar(
  selectedTab: Tab,
  onTabSelected: (Tab) -> Unit,
  onQuickEntry: () -> Unit,
) {
  NavigationBar(
    containerColor = SextantColors.Surface,
    tonalElevation = 0.dp,
    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
  ) {
    NavigationBarItem(
      selected = selectedTab == Tab.Home,
      onClick = { onTabSelected(Tab.Home) },
      icon = { Icon(Tab.Home.icon, contentDescription = Tab.Home.title) },
      label = { Text(Tab.Home.title) },
      colors = sextantNavItemColors(),
    )
    NavigationBarItem(
      selected = selectedTab == Tab.Analytics,
      onClick = { onTabSelected(Tab.Analytics) },
      icon = { Icon(Tab.Analytics.icon, contentDescription = Tab.Analytics.title) },
      label = { Text(Tab.Analytics.title) },
      colors = sextantNavItemColors(),
    )
    NavigationBarItem(
      selected = false,
      onClick = onQuickEntry,
      icon = {
        Box(
          Modifier.size(58.dp).clip(CircleShape).background(SextantColors.Accent),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Outlined.Add, contentDescription = "记一笔", tint = SextantColors.OnAccent, modifier = Modifier.size(34.dp))
        }
      },
      label = { Text("记一笔") },
      colors = sextantNavItemColors(),
    )
    NavigationBarItem(
      selected = selectedTab == Tab.Discover,
      onClick = { onTabSelected(Tab.Discover) },
      icon = { Icon(Tab.Discover.icon, contentDescription = Tab.Discover.title) },
      label = { Text(Tab.Discover.title) },
      colors = sextantNavItemColors(),
    )
    NavigationBarItem(
      selected = selectedTab == Tab.Settings,
      onClick = { onTabSelected(Tab.Settings) },
      icon = { Icon(Tab.Settings.icon, contentDescription = Tab.Settings.title) },
      label = { Text(Tab.Settings.title) },
      colors = sextantNavItemColors(),
    )
  }
}

@Composable
private fun sextantNavItemColors() = NavigationBarItemDefaults.colors(
  selectedIconColor = SextantColors.AccentStrong,
  selectedTextColor = SextantColors.AccentStrong,
  indicatorColor = SextantColors.AccentSoft,
  unselectedIconColor = SextantColors.TabInactive,
  unselectedTextColor = SextantColors.TextSubtle,
)
