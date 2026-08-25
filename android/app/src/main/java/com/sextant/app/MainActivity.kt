package com.sextant.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sextant.app.automation.AutomationNotifier
import com.sextant.app.ui.SextantApp
import com.sextant.app.ui.theme.SextantTheme

class MainActivity : ComponentActivity() {
  private var requestedEditTransactionId by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestedEditTransactionId = intent.getStringExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID)
    val repository = (application as SextantApplication).repository
    var themePreference by mutableStateOf(repository.loadThemePreference())
    setContent {
      SextantTheme(themePreference = themePreference) {
        SextantApp(
          repository = repository,
          themePreference = themePreference,
          onThemePreferenceChanged = { themePreference = it },
          requestedEditTransactionId = requestedEditTransactionId,
          onEditRequestConsumed = { requestedEditTransactionId = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    requestedEditTransactionId = intent.getStringExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID)
  }
}
