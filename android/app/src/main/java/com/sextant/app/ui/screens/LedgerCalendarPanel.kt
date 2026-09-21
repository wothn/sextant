package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
      CalendarCellText(if (selected) "✓ $label" else label, MaterialTheme.typography.labelMedium)
      if (summary == null) CalendarCellText("—", MaterialTheme.typography.labelSmall, SextantColors.TextSubtle)
      else {
        CalendarCellText("+${calendarAmount(summary.incomeCents)}", MaterialTheme.typography.labelSmall, SextantColors.Success)
        CalendarCellText("−${calendarAmount(summary.expenseCents)}", MaterialTheme.typography.labelSmall, SextantColors.Text)
      }
    }
  }
}

/**
 * 格内文字一律单行。
 *
 * 日视图一格只有屏宽/7——窄机器上不到 40dp，`+999.99` 这种长度按 labelSmall 排下来就会折行，
 * 既把金额劈成两行，又撑高整行、让七列对不齐。所以这里按宽度分步缩字号，宁可小一号也不换行；
 * 缩到 [MIN_CELL_FONT_SIZE] 还放不下时按格边裁掉，不再溢出到相邻日期。
 *
 * 字号上限跟着传入的 style 走，主题调大排版时这里不会变成写死的常数。
 */
@Composable
private fun CalendarCellText(text: String, style: TextStyle, color: Color = Color.Unspecified) {
  val maxFontSize = style.fontSize.takeIf { it.isSp } ?: MIN_CELL_FONT_SIZE
  Text(
    text = text,
    style = style,
    color = color,
    maxLines = 1,
    softWrap = false,
    overflow = TextOverflow.Clip,
    autoSize = TextAutoSize.StepBased(
      minFontSize = MIN_CELL_FONT_SIZE,
      maxFontSize = maxFontSize,
      stepSize = 0.5.sp,
    ),
  )
}

private val MIN_CELL_FONT_SIZE = 7.sp
