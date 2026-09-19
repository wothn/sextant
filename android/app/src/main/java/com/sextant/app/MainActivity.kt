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
    val sextantApplication = application as SextantApplication
    val repository = sextantApplication.repository
    // 主题必须在第一帧之前定下来，否则会先按系统主题画一帧、读完之后再跳成用户选的那套，
    // 深色/浅色之间闪一下。这是一次按主键取单值，没有聚合也没有写入队列，代价可以接受；
    // 其余所有读取都在协程里，不走这条路径。
    var themePreference by mutableStateOf(runBlocking { repository.loadThemePreference() })
    setContent {
      SextantTheme(themePreference = themePreference) {
        SextantApp(
          repository = repository,
          backupCoordinator = sextantApplication.backupCoordinator,
          themePreference = themePreference,
          onThemePreferenceChanged = { themePreference = it },
          requestedEditTransactionId = requestedEditTransactionId,
          onEditRequestConsumed = ::consumeEditRequest,
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    requestedEditTransactionId = intent.getStringExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID)
  }

  /**
   * 兑现一次「打开这笔流水」的请求。
   *
   * 请求只该生效一次，所以顺带把附加信息从 Intent 里摘掉：否则旋转、分屏改变窗口大小都会
   * 重建 Activity，[onCreate] 会从同一个 Intent 里把它再读出来，把用户已经关掉的记账面板
   * 重新弹一次，还会把筛选区间改回那笔流水所在的月份。
   */
  private fun consumeEditRequest() {
    requestedEditTransactionId = null
    intent.removeExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID)
  }
}
