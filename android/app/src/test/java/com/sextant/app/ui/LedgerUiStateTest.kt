package com.sextant.app.ui

import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.StateProjection
import com.sextant.app.data.ThemePreference
import org.junit.Assert.*
import org.junit.Test

class LedgerUiStateTest {
  private val ledger = LedgerRequest(LedgerFilter.allTime(), StateProjection.Ledger)
  private val analytics = LedgerRequest(LedgerFilter.allTime(), StateProjection.Analytics)

  @Test
  fun returningToVisitedPageKeepsContentWhileRevalidating() {
    val data = AppState()
    val state = LedgerUiState().loaded(ledger, data, ThemePreference.Dark)
      .loaded(analytics, AppState(), ThemePreference.Dark).loading(ledger)
    assertSame(data, state.data)
    assertTrue(state.loading)
    assertNull(state.error)
    assertEquals(ThemePreference.Dark, state.theme)
  }

  @Test
  fun newFilterNeverShowsPreviousRangesAmounts() {
    val filtered = LedgerRequest(LedgerFilter.forMonth(0L), StateProjection.Ledger)
    val state = LedgerUiState().loaded(ledger, AppState(), ThemePreference.Light).loading(filtered)
    assertNull(state.data)
    assertNull(state.dataFor(filtered))
    assertNotNull(state.dataFor(ledger))
  }

  @Test
  fun refreshFailureRetainsContentAndRetryClearsError() {
    val data = AppState()
    val failed = LedgerUiState().loaded(ledger, data, ThemePreference.System).loading(ledger).failed(ledger)
    assertSame(data, failed.data)
    assertEquals("读取失败，请重试。", failed.error)
    assertFalse(failed.loading)
    val retry = failed.loading(ledger)
    assertSame(data, retry.data)
    assertNull(retry.error)
    assertTrue(retry.loading)
  }

  @Test
  fun firstReadFailureDoesNotInventEmptyLedgerData() {
    val failed = LedgerUiState().loading(ledger).failed(ledger)
    assertNull(failed.data)
    assertNotNull(failed.error)
    assertFalse(failed.loading)
  }

  @Test
  fun loadingNewRangeEvictsOnlyPreviousRangeOfSameDestination() {
    val filtered = LedgerRequest(LedgerFilter.forMonth(0L), StateProjection.Ledger)
    val analysisData = AppState()
    val state = LedgerUiState().loaded(ledger, AppState(), ThemePreference.Light)
      .loaded(analytics, analysisData, ThemePreference.Light)
      .loaded(filtered, AppState(), ThemePreference.Light)
    assertNull(state.dataFor(ledger))
    assertSame(analysisData, state.dataFor(analytics))
    assertEquals(2, state.cachedPages.size)
  }
}
