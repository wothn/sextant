package com.sextant.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantColors
import kotlinx.coroutines.delay

/** 首次读取保留页面标题与中性骨架；骨架本身不进读屏树，只用一条 liveRegion 文案说明状态。 */
@Composable
fun SextantPagePlaceholder(title: String, error: String?, onRetry: () -> Unit) {
  Column(Modifier.fillMaxSize()) {
    SextantFlexibleHeader(title = title, scrollState = rememberLazyListState(),
      modifier = Modifier.padding(horizontal = 20.dp))
    if (error != null) {
      Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(error, color = SextantColors.TextMuted)
        SextantTextButton(onClick = onRetry) { Text("重试") }
      }
    } else {
      Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(16.dp)) {
          SextantSurface(color = SextantColors.SurfaceContainerLow) {
            Spacer(Modifier.fillMaxWidth().height(144.dp))
          }
          repeat(3) {
            SextantSurface(color = SextantColors.SurfaceContainerLow) {
              Spacer(Modifier.fillMaxWidth().height(64.dp))
            }
          }
        }
        Text(
          "正在读取…",
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
          color = SextantColors.TextMuted,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

/** 刷新失败时保留已读到的账本，并把重试放在当前页面。 */
@Composable
fun SextantReadError(message: String, onRetry: () -> Unit) {
  SextantSurface(color = SextantColors.SurfaceContainerLow) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp)
      .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
      Text(message, Modifier.weight(1f), color = SextantColors.Danger, style = MaterialTheme.typography.bodyMedium)
      SextantTextButton(onClick = onRetry) { Text("重试") }
    }
  }
}
