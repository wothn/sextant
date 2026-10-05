package com.sextant.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.SextantApplication
import com.sextant.app.data.ThemePreference
import com.sextant.app.ui.components.*
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantTheme
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(RESULT_CANCELED)
    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
    val kind = WidgetKind.entries.firstOrNull { it.provider.name == info?.provider?.className }
    if (id == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null || info?.provider?.packageName != packageName) {
      finish()
      return
    }
    val current = WidgetPreferences(this).get(id)
    setContent {
      var appearance by rememberSaveable { mutableStateOf(current.appearance) }
      var hidden by rememberSaveable { mutableStateOf(current.hideAmounts) }
      var appTheme by remember { mutableStateOf(ThemePreference.System) }
      var saving by remember { mutableStateOf(false) }
      var error by remember { mutableStateOf<String?>(null) }
      val scope = rememberCoroutineScope()
      LaunchedEffect(Unit) {
        appTheme = (application as SextantApplication).repository.loadThemePreference()
      }
      SextantTheme(themePreference = when (appearance) {
        WidgetAppearance.App -> appTheme
        WidgetAppearance.Light -> ThemePreference.Light
        WidgetAppearance.Dark -> ThemePreference.Dark
      }) {
        SextantSurface(modifier = Modifier.fillMaxSize()) {
          Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(kind.title, style = MaterialTheme.typography.headlineMedium, color = SextantColors.Text)
            Text("桌面小组件", color = SextantColors.TextMuted)
            Text("外观", style = MaterialTheme.typography.titleMedium, color = SextantColors.Text)
            SextantSegmentedButtonGroup(WidgetAppearance.entries, appearance, { appearance = it }, { it.label }, enabled = !saving)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("隐藏金额", color = SextantColors.Text, modifier = Modifier.weight(1f).padding(top = 12.dp))
              SextantSwitch(hidden, { hidden = it }, enabled = !saving)
            }
            Text("隐藏后，金额和预算比例不会显示在桌面上。点击记录仍会打开应用查看。", color = SextantColors.TextMuted)
            Text("点击组件底部的更新时间可刷新；后台更新可能受系统省电限制。", color = SextantColors.TextMuted)
            error?.let { Text(it, color = SextantColors.Danger) }
            SextantButton(onClick = {
              saving = true
              error = null
              scope.launch {
                try {
                  WidgetUpdates.configure(this@WidgetConfigurationActivity, kind, id, WidgetOptions(appearance, hidden))
                  setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                  finish()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                  throw cancelled
                } catch (_: Exception) {
                  error = "设置未完成，请重试"
                  saving = false
                }
              }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "正在保存…" else "完成") }
            SextantTextButton(onClick = { finish() }, enabled = !saving) { Text("取消") }
          }
        }
      }
    }
  }
}
