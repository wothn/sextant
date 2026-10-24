package com.sextant.app

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.sextant.app.automation.AutomationNotifier
import com.sextant.app.data.ThemePreference
import com.sextant.app.ui.SextantApp
import com.sextant.app.ui.LedgerViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sextant.app.ui.theme.SextantTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val uiPrefs by lazy { getSharedPreferences(UI_PREFS, MODE_PRIVATE) }
  private var requestedEditTransactionId by mutableStateOf<String?>(null)
  private var widgetDestination by mutableStateOf<String?>(null)
  private var widgetRecurringId by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestedEditTransactionId = intent.getStringExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID)
    readWidgetRequest(intent)
    val sextantApplication = application as SextantApplication
    val repository = sextantApplication.repository
    // 主题必须在第一帧之前定下来，否则会先按系统主题画一帧、读完之后再跳成用户选的那套，
    // 深色/浅色之间闪一下。SharedPreferences 是同步内存读取，代价远小于在主线程上跑一次
    // Room 查询；数据库仍是唯一权威，启动后用协程把缓存和数据库对齐。
    var themePreference by mutableStateOf(
      ThemePreference.fromStorage(uiPrefs.getString(THEME_PREFERENCE_KEY, null)),
    )
    lifecycleScope.launch {
      val stored = repository.loadThemePreference()
      uiPrefs.edit().putString(THEME_PREFERENCE_KEY, stored.storageValue).apply()
      if (stored != themePreference) themePreference = stored
    }
    val ledgerViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LedgerViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return LedgerViewModel(repository) as T
      }
    })[LedgerViewModel::class.java]
    setContent {
      SextantTheme(themePreference = themePreference) {
        SextantApp(
          repository = repository,
          viewModel = ledgerViewModel,
          backupCoordinator = sextantApplication.backupCoordinator,
          themePreference = themePreference,
          onThemePreferenceChanged = { preference ->
            if (preference != themePreference) {
              themePreference = preference
              uiPrefs.edit().putString(THEME_PREFERENCE_KEY, preference.storageValue).apply()
            }
          },
          requestedEditTransactionId = requestedEditTransactionId,
          onEditRequestConsumed = ::consumeEditRequest,
          widgetDestination = widgetDestination,
          widgetRecurringId = widgetRecurringId,
          onWidgetRequestConsumed = {
            widgetDestination = null
            widgetRecurringId = null
            intent.removeExtra(com.sextant.app.widget.WidgetNavigation.EXTRA_DESTINATION)
            intent.removeExtra(com.sextant.app.widget.WidgetNavigation.EXTRA_RECURRING_ID)
          },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    requestedEditTransactionId = intent.getStringExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID)
    readWidgetRequest(intent)
  }

  private fun readWidgetRequest(intent: Intent) {
    widgetDestination = intent.getStringExtra(com.sextant.app.widget.WidgetNavigation.EXTRA_DESTINATION)
      ?.takeIf { it in com.sextant.app.widget.WidgetNavigation.destinations }
    widgetRecurringId = intent.getStringExtra(com.sextant.app.widget.WidgetNavigation.EXTRA_RECURRING_ID)
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

  private companion object {
    const val UI_PREFS = "sextant_ui"
    const val THEME_PREFERENCE_KEY = "theme_preference"
  }
}
