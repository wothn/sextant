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

/** Sextant 四目的地底栏：共享组件负责药丸形态和按下、聚焦、选中反馈。 */
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
