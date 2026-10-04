package com.sextant.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sextant.app.data.AppState
import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.StateProjection
import com.sextant.app.data.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LedgerRequest(val filter: LedgerFilter, val projection: StateProjection, val analysisPeriod: AnalysisPeriod = AnalysisPeriod.Month)
data class LedgerUiState(
  val request: LedgerRequest? = null,
  val data: AppState? = null,
  val error: String? = null,
  val theme: ThemePreference = ThemePreference.System,
  val loading: Boolean = true,
  val cachedPages: Map<LedgerRequest, AppState> = emptyMap(),
) {
  fun dataFor(query: LedgerRequest): AppState? = if (query == request) data else cachedPages[query]

  fun loading(query: LedgerRequest): LedgerUiState = copy(
    request = query, data = dataFor(query), error = null, loading = true,
  )

  fun loaded(query: LedgerRequest, value: AppState, preference: ThemePreference): LedgerUiState = copy(
    request = query, data = value, error = null, theme = preference, loading = false,
    // 最多保留四个目的地；旧筛选的数据不能冒充新筛选的结果。
    cachedPages = cachedPages.filterKeys { it.projection != query.projection } + (query to value),
  )

  fun failed(query: LedgerRequest): LedgerUiState = copy(
    request = query, data = dataFor(query), error = "读取失败，请重试。", loading = false,
  )
}

/** 页面只声明需要的投影；数据库版本变化取消旧读取，避免旧结果覆盖新筛选。 */
class LedgerViewModel(private val repository: SextantRepository) : ViewModel() {
  private val request = MutableStateFlow(LedgerRequest(LedgerFilter.allTime(), StateProjection.Ledger))
  private val refresh = MutableStateFlow(0L)
  private val _state = MutableStateFlow(LedgerUiState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      combine(request, repository.changes, refresh) { query, _, _ -> query }
        .collectLatest { query ->
          _state.value = _state.value.loading(query)
          try {
            val data = repository.loadState(query.filter, query.projection, query.analysisPeriod)
            val theme = repository.loadThemePreference()
            _state.value = _state.value.loaded(query, data, theme)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            _state.value = _state.value.failed(query)
          }
        }
    }
  }

  fun show(query: LedgerRequest) { request.value = query }
  fun refresh() { refresh.value++ }
}
