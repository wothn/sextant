package com.sextant.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sextant.app.data.AppState
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.components.QuickEntryDialog
import com.sextant.app.ui.navigation.LedgerNavigationBar
import com.sextant.app.ui.navigation.Tab
import com.sextant.app.ui.screens.AnalyticsScreen
import com.sextant.app.ui.screens.BudgetScreen
import com.sextant.app.ui.screens.HomeScreen
import com.sextant.app.ui.screens.SettingsScreen
import com.sextant.app.ui.theme.SextantColors

@Composable
fun SextantApp(repository: SextantRepository) {
  var state by remember { mutableStateOf(AppState()) }
  var tab by remember { mutableStateOf(Tab.Home) }
  var showQuickEntry by remember { mutableStateOf(false) }
  var reloadKey by remember { mutableStateOf(0) }

  LaunchedEffect(reloadKey) {
    state = repository.loadState()
  }

  Scaffold(
    bottomBar = {
      LedgerNavigationBar(
        selectedTab = tab,
        onTabSelected = { tab = it },
        onQuickEntry = { showQuickEntry = true },
      )
    },
    containerColor = SextantColors.Background,
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      when (tab) {
        Tab.Home -> HomeScreen(state, onQuickEntry = { showQuickEntry = true })
        Tab.Analytics -> AnalyticsScreen(state)
        Tab.Discover -> BudgetScreen(state, repository) { reloadKey++ }
        Tab.Settings -> SettingsScreen(state, repository) { reloadKey++ }
      }
    }
  }

  if (showQuickEntry) {
    QuickEntryDialog(
      state = state,
      onDismiss = { showQuickEntry = false },
      onSubmit = { type, amount, categoryId, paymentMethodId, description ->
        repository.addTransaction(type, amount, categoryId, paymentMethodId, description)
        showQuickEntry = false
        reloadKey++
      },
    )
  }
}
