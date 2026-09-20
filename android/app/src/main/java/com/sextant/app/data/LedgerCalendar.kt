package com.sextant.app.data

import java.time.LocalDate
import java.time.YearMonth

/** Calendar buckets use local dates and the same accounting rules as the ledger. */
internal class LedgerCalendar(items: List<TransactionListItem>) {
  val days = items.groupBy { it.transaction.transactionDate.toLocalDate() }
  val months = items.groupBy { YearMonth.from(it.transaction.transactionDate.toLocalDate()) }
    .mapValues { LedgerCalculations.summarize(it.value) }
  val years = items.groupBy { it.transaction.transactionDate.toLocalDate().year }
    .mapValues { LedgerCalculations.summarize(it.value) }
  val summaries = days.mapValues { LedgerCalculations.summarize(it.value) }
}

/** Monday first, with blanks only to complete the first and last week. */
internal fun calendarDays(month: YearMonth): List<LocalDate?> {
  val offset = month.atDay(1).dayOfWeek.value - 1
  val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
  return List(count) { index ->
    (index - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
  }
}
