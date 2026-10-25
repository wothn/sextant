package com.sextant.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AnalysisWindow
import com.sextant.app.ui.theme.SextantFullShape
import com.sextant.app.ui.util.analysisDateSelectorLabel
import com.sextant.app.ui.util.analysisPeriodLabel
import com.sextant.app.ui.util.analysisWindowLabel

/** 分析日期入口：箭头移动一个周期，点击直接选择整周、月份或年份。 */
@Composable
fun AnalysisDateSelector(
  window: AnalysisWindow,
  onWindowChanged: (AnalysisWindow) -> Unit,
  modifier: Modifier = Modifier,
) {
  var picking by rememberSaveable(window.period) { mutableStateOf(false) }
  val periodLabel = analysisPeriodLabel(window.period)
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    SextantIconButton(onClick = { onWindowChanged(window.move(-1)) }, modifier = Modifier.size(48.dp)) {
      Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "上一$periodLabel")
    }
    SextantSurface(
      modifier = Modifier.weight(1f, fill = false).clip(SextantFullShape)
        .clickable(role = Role.Button, onClickLabel = "选择日期") { picking = true }
        .semantics { contentDescription = analysisWindowLabel(window) },
      color = Color.Transparent, shape = SextantFullShape,
    ) {
      Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(analysisDateSelectorLabel(window), Modifier.weight(1f, fill = false),
          style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(12.dp))
      }
    }
    SextantIconButton(onClick = { onWindowChanged(window.move(1)) }, modifier = Modifier.size(48.dp)) {
      Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "下一$periodLabel")
    }
  }
  if (picking) {
    AnalysisPeriodPicker(
      window = window,
      onDismiss = { picking = false },
      onSelect = {
        onWindowChanged(it)
        picking = false
      },
    )
  }
}
