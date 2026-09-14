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
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
  private var requestedEditTransactionId by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestedEditTransactionId = intent.getStringExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID)
    val repository = (application as SextantApplication).repository
    // 主题必须在第一帧之前定下来，否则会先按系统主题画一帧、读完之后再跳成用户选的那套，
    // 深色/浅色之间闪一下。这是一次按主键取单值，没有聚合也没有写入队列，代价可以接受；
    // 其余所有读取都在协程里，不走这条路径。
    var themePreference by mutableStateOf(runBlocking { repository.loadThemePreference() })
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
