package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.data.*
import com.sextant.app.ui.components.*
import com.sextant.app.ui.theme.SextantColors
import java.time.LocalDate

/** A separate destination keeps the overview focused on the ledger. */
@Composable
fun LedgerCalendarScreen(
  state: AppState,
  filter: LedgerFilter,
  onFilterChanged: (LedgerFilter) -> Unit,
  onBack: () -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
) {
  var level by rememberSaveable { mutableStateOf(CalendarLevel.Day) }
  var selectedDay by rememberSaveable(filter.rangeStart, filter.rangeEnd) {
    mutableStateOf((if (filter.isAllTime) LocalDate.now() else filter.anchorMonthStart.toLocalDate()).toEpochDay())
  }
  var showFilters by rememberSaveable { mutableStateOf(false) }
  val date = LocalDate.ofEpochDay(selectedDay)
  val calendar = remember(state.transactions) { LedgerCalendar(state.transactions) }
  val categories = remember(state.categories) { state.categories.associateBy { it.id } }
  val listState = rememberLazyListState()
  val entries = if (level == CalendarLevel.Day) calendar.days[date].orEmpty() else emptyList()

  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SextantIconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回概览")
      }
      Text("收支日历", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = SextantColors.Text)
      SextantIconButton(onClick = { showFilters = true }) {
        Icon(Icons.Outlined.Tune, contentDescription = "筛选")
      }
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    ) {
      item {
        LedgerCalendarPanel(calendar, filter, level, date,
          onLevel = { level = it }, onDate = { selectedDay = it.toEpochDay() })
      }
      if (level == CalendarLevel.Day && entries.isEmpty()) {
        item { EmptyCard("这一天没有记录", "可选择其他日期，或调整筛选条件。") }
      }
      items(entries, key = { it.transaction.id }) { item ->
        val category = categories[item.transaction.categoryId]
        LedgerTransactionRow(
          item = item,
          categoryName = category?.name ?: item.categoryName,
          parentName = categories[category?.parentCategoryId]?.name,
          onClick = onTransactionClick,
        )
      }
    }
  }
  if (showFilters) {
    LedgerFilterSheet(state, filter,
      onApply = { onFilterChanged(it); showFilters = false },
      onDismiss = { showFilters = false })
  }
}
