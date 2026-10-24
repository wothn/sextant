@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sextant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.AnalysisWindow
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.sextantEmphasized
import com.sextant.app.ui.util.analysisDateSelectorLabel
import com.sextant.app.ui.util.analysisPeriodLabel
import com.sextant.app.ui.util.analysisWeeksInMonth
import com.sextant.app.ui.util.analysisWindowLabel
import com.sextant.app.ui.util.analysisWeekLabel
import java.time.LocalDate
import java.time.YearMonth

/** 周选整周，月选月份，年选年份；浏览更高层级不会提交日期。 */
@Composable
fun AnalysisPeriodPicker(
  window: AnalysisWindow,
  onDismiss: () -> Unit,
  onSelect: (AnalysisWindow) -> Unit,
) {
  val initialLevel = window.period
  var level by rememberSaveable(window.period) { mutableStateOf(initialLevel) }
  var browsingDay by rememberSaveable(window.period, window.start) { mutableStateOf(window.anchor.withDayOfMonth(1).toEpochDay()) }
  var selectedDay by rememberSaveable(window.period, window.start) { mutableStateOf(window.start.toEpochDay()) }
  val browsing = LocalDate.ofEpochDay(browsingDay)
  val selected = AnalysisWindow(window.period, LocalDate.ofEpochDay(selectedDay))
  val firstYear = Math.floorDiv(browsing.year - 1, 12) * 12 + 1
  val periodLabel = analysisPeriodLabel(window.period)
  val current = AnalysisWindow(window.period, LocalDate.now())
  val returnToSelection = {
    level = if (level == AnalysisPeriod.Year && initialLevel == AnalysisPeriod.Week) AnalysisPeriod.Month else initialLevel
  }

  SextantBottomSheet(onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    BackHandler(enabled = level != initialLevel, onBack = returnToSelection)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (level != initialLevel) {
          SextantIconButton(onClick = returnToSelection) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回选择" + analysisPeriodLabel(initialLevel))
          }
        }
        Text(when (level) {
          AnalysisPeriod.Week -> "选择周"
          AnalysisPeriod.Month -> "选择月份"
          AnalysisPeriod.Year -> "选择年份"
        }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        SextantTextButton(onClick = {
          selectedDay = current.start.toEpochDay()
          browsingDay = LocalDate.now().withDayOfMonth(1).toEpochDay()
          level = initialLevel
        }) { Text("本$periodLabel") }
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val previous = when (level) {
          AnalysisPeriod.Week -> browsing.minusMonths(1)
          AnalysisPeriod.Month -> browsing.minusYears(1)
          AnalysisPeriod.Year -> browsing.minusYears(12)
        }
        val next = when (level) {
          AnalysisPeriod.Week -> browsing.plusMonths(1)
          AnalysisPeriod.Month -> browsing.plusYears(1)
          AnalysisPeriod.Year -> browsing.plusYears(12)
        }
        val navigationUnit = when (level) {
          AnalysisPeriod.Week -> "月"
          AnalysisPeriod.Month -> "年"
          AnalysisPeriod.Year -> "组年份"
        }
        SextantIconButton(onClick = { browsingDay = previous.toEpochDay() }, enabled = previous.year >= 1) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "上一$navigationUnit")
        }
        SextantTextButton(modifier = Modifier.weight(1f), enabled = level != AnalysisPeriod.Year,
          onClick = { level = if (level == AnalysisPeriod.Week) AnalysisPeriod.Month else AnalysisPeriod.Year }) {
          Text(when (level) {
            AnalysisPeriod.Week -> "${browsing.year}年${browsing.monthValue}月"
            AnalysisPeriod.Month -> "${browsing.year}年"
            AnalysisPeriod.Year -> "$firstYear–${minOf(firstYear + 11, 9999)}年"
          }, style = MaterialTheme.typography.titleMedium)
          if (level != AnalysisPeriod.Year) Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
        SextantIconButton(onClick = { browsingDay = next.toEpochDay() }, enabled = next.year <= 9999) {
          Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "下一$navigationUnit")
        }
      }
      if (level == AnalysisPeriod.Week) {
        Text("周一至周日", style = MaterialTheme.typography.bodySmall, color = SextantColors.TextSubtle)
      }
      LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (level) {
          AnalysisPeriod.Week -> items(analysisWeeksInMonth(YearMonth.from(browsing)), key = { it.start.toEpochDay() }) { week ->
            val checked = selected.start == week.start
            SextantSelectableSurface(selected = checked, onClick = { selectedDay = week.start.toEpochDay() },
              role = Role.RadioButton,
              modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = analysisWeekLabel(week) + "，" + analysisWindowLabel(week)
              }) {
              Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(analysisWeekLabel(week), style = if (checked) MaterialTheme.typography.titleMedium.sextantEmphasized()
                  else MaterialTheme.typography.titleMedium)
                Text(analysisDateSelectorLabel(week), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (week.start == AnalysisWindow(AnalysisPeriod.Week, LocalDate.now()).start) {
                  Text("本周", style = MaterialTheme.typography.labelMedium)
                }
              }
            }
          }
          else -> {
            val dates = if (level == AnalysisPeriod.Month) (1..12).map { LocalDate.of(browsing.year, it, 1) }
              else (firstYear..minOf(firstYear + 11, 9999)).map { LocalDate.of(it, 1, 1) }
            items(dates.chunked(3), key = { it.first().toEpochDay() }) { row ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { date ->
                  val checked = if (level == AnalysisPeriod.Month) YearMonth.from(selected.start) == YearMonth.from(date)
                    else selected.start.year == date.year
                  SextantSelectableSurface(selected = checked, modifier = Modifier.weight(1f), role = Role.RadioButton, onClick = {
                    if (level == initialLevel) selectedDay = date.toEpochDay()
                    else {
                      browsingDay = date.toEpochDay()
                      level = if (level == AnalysisPeriod.Year) AnalysisPeriod.Month else AnalysisPeriod.Week
                    }
                  }) {
                    Box(Modifier.heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                      contentAlignment = Alignment.Center) {
                      Text(if (level == AnalysisPeriod.Month) "${date.monthValue}月" else "${date.year}",
                        style = if (checked) MaterialTheme.typography.titleMedium.sextantEmphasized()
                          else MaterialTheme.typography.titleMedium)
                    }
                  }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
              }
            }
          }
        }
      }
      Text("已选：" + analysisWindowLabel(selected), style = MaterialTheme.typography.bodyMedium,
        color = SextantColors.TextMuted)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SextantTextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
        SextantButton(onClick = { onSelect(selected) }, modifier = Modifier.weight(2f)) { Text("查看所选$periodLabel") }
      }
    }
  }
}
