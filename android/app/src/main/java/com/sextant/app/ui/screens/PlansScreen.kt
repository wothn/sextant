package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.components.SextantFlexibleHeader
import com.sextant.app.ui.components.SextantTabRow

/** 「计划」页里的两个一级视图。 */
enum class PlanSection(val title: String, val subtitle: String) {
  Budget("预算", "控制总支出，再为重要分类分配额度。"),
  Recurring("周期支出", "记录房租、宽带、会员这类按固定节奏发生的支出。"),
}

/**
 * 「计划」页：预算与周期支出两个一级视图，顶部一级切换常驻在头部下方。
 *
 * 两个视图各自持有一个 `LazyListState`，不能共用：头部的收起程度是直接读当前列表的滚动
 * 位置算出来的（见 [SextantFlexibleHeader]），共用的话切过去会继承上一块的偏移，内容停在
 * 中间而头部还收着。分开之后还顺带得到了「每个视图记住自己的位置」——切来切去不会丢进度。
 */
@Composable
fun PlansScreen(state: AppState, repository: SextantRepository, onSaved: () -> Unit) {
  val budgetListState = rememberLazyListState()
  val recurringListState = rememberLazyListState()
  var section by rememberSaveable { mutableStateOf(PlanSection.Budget) }
  val activeListState = when (section) {
    PlanSection.Budget -> budgetListState
    PlanSection.Recurring -> recurringListState
  }

  Column(Modifier.fillMaxSize()) {
    SextantFlexibleHeader(
      title = "计划",
      scrollState = activeListState,
      subtitle = section.subtitle,
      modifier = Modifier.padding(horizontal = 20.dp),
    )
    SextantTabRow(
      options = PlanSection.entries,
      selected = section,
      onSelect = { section = it },
      label = { it.title },
      modifier = Modifier.padding(horizontal = 20.dp),
    )
    when (section) {
      PlanSection.Budget -> BudgetPane(budgetListState, state, repository, onSaved)
      PlanSection.Recurring -> RecurringPane(recurringListState, state, repository, onSaved)
    }
  }
}
