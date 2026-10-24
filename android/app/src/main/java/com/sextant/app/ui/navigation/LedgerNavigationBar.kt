package com.sextant.app.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.components.SextantNavigationBar
import com.sextant.app.ui.components.SextantNavigationItem
import com.sextant.app.ui.theme.SextantMotion

/** 四目的地底栏：共享组件负责整项胶囊和按下、聚焦、选中反馈。 */
@Composable
fun LedgerNavigationBar(
  selectedTab: Tab,
  onTabSelected: (Tab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val effects = SextantMotion.fastEffects<Float>()
  val spatial = SextantMotion.fastSpatial<Float>()
  SextantNavigationBar(selectedIndex = selectedTab.ordinal, itemCount = Tab.entries.size, modifier = modifier) {
    Tab.entries.forEach { tab ->
      SextantNavigationItem(
        selected = tab == selectedTab,
        onClick = { onTabSelected(tab) },
        icon = {
          // Outlined → Filled 不硬切：淡出叠一次轻微缩放，和指示器滑动同一套弹簧。
          AnimatedContent(
            targetState = tab == selectedTab,
            transitionSpec = {
              (fadeIn(animationSpec = effects) +
                scaleIn(initialScale = 0.9f, animationSpec = spatial)) togetherWith
                fadeOut(animationSpec = effects)
            },
            label = "navIcon",
          ) { selected ->
            Icon(
              if (selected) tab.selectedIcon else tab.icon,
              contentDescription = null,
              modifier = Modifier.size(24.dp),
            )
          }
        },
        label = { Text(tab.title, maxLines = 1) },
      )
    }
  }
}
