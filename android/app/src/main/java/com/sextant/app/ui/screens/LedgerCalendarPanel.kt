package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sextant.app.data.*
import com.sextant.app.ui.components.*
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.*
import java.time.LocalDate
import java.time.YearMonth

internal enum class CalendarLevel(val label: String) { Day("日"), Month("月"), Year("年") }

@Composable
internal fun LedgerCalendarPanel(
  calendar: LedgerCalendar,
  filter: LedgerFilter,
  level: CalendarLevel,
  date: LocalDate,
  onLevel: (CalendarLevel) -> Unit,
  onDate: (LocalDate) -> Unit,
) {
  val month = YearMonth.from(date)
  val firstYear = Math.floorDiv(date.year, 12) * 12
  val summary = when (level) {
    CalendarLevel.Day -> calendar.months[month]
    CalendarLevel.Month -> calendar.years[date.year]
    CalendarLevel.Year -> Summary(
      (firstYear until firstYear + 12).sumOf { calendar.years[it]?.incomeCents ?: 0 },
      (firstYear until firstYear + 12).sumOf { calendar.years[it]?.expenseCents ?: 0 },
    )
  } ?: Summary(0, 0)
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SextantSegmentedButtonGroup(CalendarLevel.entries, level, onLevel, { it.label }, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      SextantIconButton(onClick = { onDate(when (level) {
        CalendarLevel.Day -> date.minusMonths(1)
        CalendarLevel.Month -> date.minusYears(1)
        CalendarLevel.Year -> date.minusYears(12)
      }) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一${if (level == CalendarLevel.Day) "月" else if (level == CalendarLevel.Month) "年" else "组"}") }
      Text(calendarPeriodLabel(date, level == CalendarLevel.Day, if (level == CalendarLevel.Year) firstYear else null),
        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
      SextantIconButton(onClick = { onDate(when (level) {
        CalendarLevel.Day -> date.plusMonths(1)
        CalendarLevel.Month -> date.plusYears(1)
        CalendarLevel.Year -> date.plusYears(12)
      }) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一${if (level == CalendarLevel.Day) "月" else if (level == CalendarLevel.Month) "年" else "组"}") }
    }
    SextantTextButton(onClick = { onDate(LocalDate.now()); onLevel(CalendarLevel.Day) }) { Text("回到今天") }
    if (!filter.isAllTime || filter.hasConditions) {
      Text("仅统计筛选内记录 · ${filter.rangeLabel()}", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
    }
    CalendarTotals(summary)
    Text("金额单位：元 · 收入 + / 支出 − · 格内金额为简写", style = MaterialTheme.typography.labelSmall, color = SextantColors.TextSubtle)
    if (level == CalendarLevel.Day) {
      Row(Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
          Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, color = SextantColors.TextMuted) }
        }
      }
      calendarDays(month).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          week.forEach { day ->
            if (day == null) Spacer(Modifier.weight(1f))
            else CalendarCell(
              label = if (day == LocalDate.now()) "今天" else day.dayOfMonth.toString(),
              description = day.toString(), summary = calendar.summaries[day], selected = day == date,
              modifier = Modifier.weight(1f), onClick = { onDate(day) },
            )
          }
        }
      }
      Text("${calendarDateLabel(date)} · ${calendar.days[date]?.size ?: 0} 笔", style = MaterialTheme.typography.titleMedium)
      CalendarTotals(calendar.summaries[date] ?: Summary(0, 0))
    } else {
      (0 until 12).chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          row.forEach { index ->
            val target = if (level == CalendarLevel.Month) LocalDate.of(date.year, index + 1, 1)
              else LocalDate.of(firstYear + index, 1, 1)
            CalendarCell(
              label = if (level == CalendarLevel.Month) "${index + 1}月" else "${target.year}年",
              description = calendarDateLabel(target),
              summary = if (level == CalendarLevel.Month) calendar.months[YearMonth.from(target)] else calendar.years[target.year],
              selected = false, modifier = Modifier.weight(1f),
              onClick = { onDate(target); onLevel(if (level == CalendarLevel.Month) CalendarLevel.Day else CalendarLevel.Month) },
            )
          }
        }
      }
      Text("点击${if (level == CalendarLevel.Month) "月份查看每日明细" else "年份查看各月收支"}", color = SextantColors.TextSubtle)
    }
  }
}

@Composable
private fun CalendarTotals(summary: Summary) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("收 ${formatCurrency(summary.incomeCents)}", color = SextantColors.Success)
    Text("支 ${formatCurrency(summary.expenseCents)}", color = SextantColors.Text)
    Text("结余 ${formatCurrency(summary.netCents)}", color = SextantColors.TextMuted)
  }
}

@Composable
private fun CalendarCell(label: String, description: String, summary: Summary?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  SextantSelectableSurface(selected, onClick, modifier.heightIn(min = 72.dp).semantics {
    contentDescription = "$description，${if (summary == null) "无收支记录" else "收入 ${formatCurrency(summary.incomeCents)}，支出 ${formatCurrency(summary.expenseCents)}"}"
  }) {
    Column(Modifier.padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(if (selected) "✓ $label" else label, style = MaterialTheme.typography.labelMedium)
      if (summary == null) Text("—", color = SextantColors.TextSubtle)
      else {
        Text("+${calendarAmount(summary.incomeCents)}", style = MaterialTheme.typography.labelSmall, color = SextantColors.Success)
        Text("−${calendarAmount(summary.expenseCents)}", style = MaterialTheme.typography.labelSmall, color = SextantColors.Text)
      }
    }
  }
}
