package com.sextant.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.RecurringExpense
import com.sextant.app.data.RecurringExpenseDraft
import com.sextant.app.data.RecurringState
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.countsTowardPlanAt
import com.sextant.app.data.monthlyEquivalentCents
import com.sextant.app.data.nextChargeOnOrAfter
import com.sextant.app.data.occurrencesBetween
import com.sextant.app.data.stateAt
import com.sextant.app.data.toStartOfDayMillis
import com.sextant.app.ui.components.RecurringExpenseEditor
import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.ui.components.SextantTextButton
import com.sextant.app.ui.components.SextantTonalButton
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantFullShape
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.futureDayLabel
import com.sextant.app.ui.util.label
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 时间轴覆盖的天数，也是「近期要扣多少」的口径。 */
private const val UPCOMING_DAYS = 30L

/** 快要扣费的门槛：落在这么多天内的条目用警示色标出来。 */
private const val DUE_SOON_DAYS = 3L

/**
 * 「计划」页的周期支出视图。
 *
 * 页面标题与一级切换由 [PlansScreen] 承担，这里只负责内容，滚动状态由上层持有。所有
 * 「下一次扣费」都是按 [LocalDate.now] 现算的（见 `RecurringSchedule.kt`），不落库，
 * 所以用户几个月不打开 App 也不会看到一堆过期的日期。
 */
@Composable
fun RecurringPane(
  listState: LazyListState,
  state: AppState,
  repository: SextantRepository,
  onSaved: () -> Unit,
) {
  var editing by remember { mutableStateOf<RecurringExpense?>(null) }
  var creating by remember { mutableStateOf(false) }
  var pendingDelete by remember { mutableStateOf<RecurringExpense?>(null) }
  val today = LocalDate.now()

  // 按「下一次扣费」排序：已停止和已暂停的没有下一次，统一沉到列表底部。
  val ordered = state.recurringExpenses
    .map { it to it.nextChargeOnOrAfter(today) }
    .sortedWith(
      compareBy<Pair<RecurringExpense, LocalDate?>> { it.second == null }
        .thenBy { it.second ?: LocalDate.MAX }
        .thenBy { it.first.name },
    )

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item { RecurringOverview(state.recurringExpenses, today, state.monthlyBudgetCents) }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          SextantHeading("周期支出")
          Text(
            "${state.recurringExpenses.size} 笔已记录",
            color = SextantColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        SextantTonalButton(onClick = { creating = true }) {
          Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("添加")
        }
      }
    }
    if (ordered.isEmpty()) {
      item {
        Text("还没有周期支出。房租、宽带、会员这类按固定节奏发生的支出记在这里，就能看到每月到底要花多少。", color = SextantColors.TextMuted)
      }
    } else {
      items(ordered, key = { it.first.id }) { (expense, nextCharge) ->
        RecurringRow(expense, nextCharge, today) { editing = expense }
      }
    }
  }

  if (creating || editing != null) {
    RecurringExpenseEditor(
      state = state,
      editing = editing,
      onDismiss = { creating = false; editing = null },
      onDelete = editing?.let { { pendingDelete = it } },
      onSubmit = { draft: RecurringExpenseDraft ->
        repository.saveRecurringExpense(editing?.id, draft)
        creating = false
        editing = null
        onSaved()
      },
    )
  }

  pendingDelete?.let { target ->
    SextantAlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("删除「${target.name}」？") },
      text = { Text("只会删掉这条计划记录，已经记进账本的流水不受影响。") },
      confirmButton = {
        SextantTextButton(onClick = {
          repository.deleteRecurringExpense(target.id)
          pendingDelete = null
          editing = null
          onSaved()
        }) { Text("删除") }
      },
      dismissButton = { SextantTextButton(onClick = { pendingDelete = null }) { Text("取消") } },
    )
  }
}

/**
 * 顶部汇总：折算成每月的固定支出、占本月预算的比例，以及未来 30 天的扣费刻度。
 *
 * 「每月固定支出」是计划值的折算，和真实流水无关，所以文案里必须写明是折算。
 * 占比的分母用月预算，不用本月已支出：固定支出是按整月承诺的，拿它去比「月初到今天
 * 实际花掉的钱」会算出四位数百分比——那个数字只在月末才成立，平时只会误导。没有设置
 * 月预算时不给比例，而不是拿 0 当分母。
 */
@Composable
private fun RecurringOverview(expenses: List<RecurringExpense>, today: LocalDate, monthlyBudgetCents: Long?) {
  val counting = expenses.filter { it.countsTowardPlanAt(today) }
  val monthlyTotal = counting.sumOf { it.monthlyEquivalentCents() }
  val upcoming = counting
    .flatMap { expense -> expense.occurrencesBetween(today, today.plusDays(UPCOMING_DAYS)).map { it to expense } }
  val upcomingTotal = upcoming.sumOf { (_, expense) -> expense.amountCents }
  val amountByDay = upcoming.groupBy({ it.first }, { it.second.amountCents }).mapValues { (_, amounts) -> amounts.sum() }
  val peak = amountByDay.values.maxOrNull() ?: 0L
  val nextCharge = upcoming.minByOrNull { it.first }
  val budgetShare = monthlyBudgetCents?.takeIf { it > 0L }?.let { monthlyTotal * 100 / it }

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("折算每月固定支出", color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
      Text(
        if (counting.isEmpty()) "—" else formatCurrency(monthlyTotal),
        style = MaterialTheme.typography.headlineLarge,
        // 占位符不是数据，用次级色，免得一条粗横杠被读成一个数字。
        color = if (counting.isEmpty()) SextantColors.TextSubtle else SextantColors.Text,
      )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      SummaryMetric("在扣费", if (counting.isEmpty()) "—" else "${counting.size} 笔")
      SummaryMetric("未来 30 天", if (upcoming.isEmpty()) "—" else formatCurrency(upcomingTotal))
      SummaryMetric(
        "占本月预算",
        if (counting.isEmpty() || budgetShare == null) "—" else "$budgetShare%",
        // 固定支出已经吃掉整个预算时用警示色，和预算进度的超支表达保持一致。
        color = if ((budgetShare ?: 0L) > 100L) SextantColors.Danger else SextantColors.Text,
      )
    }
    if (amountByDay.isNotEmpty()) {
      UpcomingScale(amountByDay, peak)
      Text(
        "未来 30 天有 ${upcoming.size} 笔扣费" +
          (nextCharge?.let { (day, expense) -> "，最近一笔：${futureDayLabel(day.toStartOfDayMillis(), today)} ${expense.name}" } ?: ""),
        color = SextantColors.TextSubtle,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun SummaryMetric(label: String, value: String, color: Color = SextantColors.Text) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = SextantColors.TextSubtle)
    Text(value, fontWeight = FontWeight.Bold, color = color)
  }
}

/**
 * 未来 30 天的扣费刻度：横向 30 格，一格一天，柱高按当天扣费金额与最高一天的比值。
 *
 * 横轴是真实时间、纵轴是真实金额，两轴都不做美化；没有扣费的日子留一条凹槽色基线，
 * 而不是不画——空白和不画在这一格里看不出区别。柱高设了一个最小可见高度，金额极小的
 * 一天不会矮到看不见，具体数值由上方文字给全，所以这点视觉夸张不会误导读数。
 */
@Composable
private fun UpcomingScale(amountByDay: Map<LocalDate, Long>, peakCents: Long) {
  val today = LocalDate.now()
  Row(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    repeat(UPCOMING_DAYS.toInt()) { offset ->
      val day = today.plusDays(offset.toLong())
      val amount = amountByDay[day] ?: 0L
      val fraction = if (amount > 0L && peakCents > 0L) {
        (amount.toFloat() / peakCents).coerceIn(0.16f, 1f)
      } else {
        0.06f
      }
      val isDueSoon = amount > 0L && ChronoUnit.DAYS.between(today, day) <= DUE_SOON_DAYS
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(top = 56.dp * (1f - fraction))
          .clip(SextantFullShape)
          .background(
            when {
              amount <= 0L -> SextantColors.SurfaceContainerHighest
              isDueSoon -> SextantColors.Warning
              else -> SextantColors.Accent
            },
          ),
      )
    }
  }
}

@Composable
private fun RecurringRow(
  expense: RecurringExpense,
  nextCharge: LocalDate?,
  today: LocalDate,
  onClick: () -> Unit,
) {
  val dueSoon = nextCharge != null && ChronoUnit.DAYS.between(today, nextCharge) <= DUE_SOON_DAYS
  SextantSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = SextantColors.SurfaceContainerLow) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(expense.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          listOf(expense.cycle.label(expense.customDays), cycleStatus(expense, today))
            .filter { it.isNotBlank() }
            .joinToString(" · "),
          color = SextantColors.TextSubtle,
          style = MaterialTheme.typography.bodySmall,
        )
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(formatCurrency(expense.amountCents), fontWeight = FontWeight.SemiBold)
        Text(
          chargeLabel(expense, nextCharge, today),
          color = if (dueSoon) SextantColors.Warning else SextantColors.TextSubtle,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

/** 左列副标题里额外的那一句。正常重复时返回空串，周期本身已经说明了节奏。 */
private fun cycleStatus(expense: RecurringExpense, today: LocalDate): String =
  if (expense.stateAt(today) == RecurringState.Ending) "已取消" else ""

/**
 * 右列金额下方那行：下一次扣费，或为什么没有下一次。
 *
 * 「已取消但还有有效期」的条目仍然会有最后一笔，所以它显示「最后一笔」而不是「下次」——
 * 这两个词在用户眼里是「还会一直扣」和「扣完就没了」的区别。
 */
private fun chargeLabel(expense: RecurringExpense, nextCharge: LocalDate?, today: LocalDate): String = when {
  nextCharge == null && expense.stateAt(today) == RecurringState.Paused -> "已暂停"
  nextCharge == null -> "已结束"
  expense.stateAt(today) == RecurringState.Ending -> "最后一笔 ${futureDayLabel(nextCharge.toStartOfDayMillis(), today)}"
  else -> "下次 ${futureDayLabel(nextCharge.toStartOfDayMillis(), today)}"
}
