package com.sextant.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantColors

@Composable
fun LedgerNavigationBar(
  selectedTab: Tab,
  onTabSelected: (Tab) -> Unit,
  onQuickEntry: () -> Unit,
) {
  Surface(color = SextantColors.Surface, tonalElevation = 0.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        .padding(horizontal = 8.dp, vertical = 6.dp).selectableGroup(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      listOf(Tab.Home, Tab.Analytics, null, Tab.Discover, Tab.Settings).forEach { tab ->
        if (tab == null) {
          Column(
            Modifier.weight(1f).height(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            FilledIconButton(
              onClick = onQuickEntry,
              modifier = Modifier.offset(y = (-6).dp).size(52.dp),
              shape = CircleShape,
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = SextantColors.Accent,
                contentColor = SextantColors.OnAccent,
              ),
            ) {
              Icon(Icons.Outlined.Add, contentDescription = "记一笔", modifier = Modifier.size(28.dp))
            }
          }
        } else {
          val selected = tab == selectedTab
          val color = if (selected) SextantColors.AccentStrong else SextantColors.TextSubtle
          Column(
            modifier = Modifier.weight(1f).height(64.dp)
              .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(tab) }),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
          ) {
            Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Text(tab.title, color = color, style = MaterialTheme.typography.labelSmall,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
          }
        }
      }
    }
  }
}
