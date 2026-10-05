package com.sextant.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sextant.app.SextantApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WidgetKind(val title: String, val provider: Class<out SextantWidgetProvider>) {
  Today("今日支出", TodayWidget::class.java),
  QuickEntry("快捷记账", QuickEntryWidget::class.java),
  Budget("本月预算", BudgetWidget::class.java),
  Month("月度收支", MonthWidget::class.java),
  Upcoming("近期扣费", UpcomingWidget::class.java),
  Recent("最近消费", RecentWidget::class.java),
}

open class SextantWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { WidgetUpdates.enqueue(context) }
  override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
    WidgetUpdates.enqueue(context)
  }
  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action in setOf(ACTION_REFRESH, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_CONFIGURATION_CHANGED)) {
      WidgetUpdates.enqueue(context)
    }
  }
  override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetPreferences(context).delete(it) } }
  override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
    val prefs = WidgetPreferences(context)
    val restored = oldWidgetIds.zip(newWidgetIds).map { (old, new) -> new to prefs.get(old) }
    oldWidgetIds.forEach(prefs::delete)
    restored.forEach { (id, options) -> prefs.save(id, options) }
    WidgetUpdates.enqueue(context)
  }
  companion object { const val ACTION_REFRESH = "com.sextant.app.widget.REFRESH" }
}

class TodayWidget : SextantWidgetProvider()
class QuickEntryWidget : SextantWidgetProvider()
class BudgetWidget : SextantWidgetProvider()
class MonthWidget : SextantWidgetProvider()
class UpcomingWidget : SextantWidgetProvider()
class RecentWidget : SextantWidgetProvider()

object WidgetUpdates {
  private val mutex = Mutex()
  suspend fun configure(context: Context, kind: WidgetKind, id: Int, options: WidgetOptions) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
      mutex.withLock {
        WidgetPreferences(context).save(id, options)
        WidgetRenderer.clearForConfiguration(context, kind, id)
      }
      enqueue(context)
    }
  }
  fun installed(context: Context): List<Pair<WidgetKind, Int>> {
    val manager = AppWidgetManager.getInstance(context)
    return WidgetKind.entries.flatMap { kind ->
      manager.getAppWidgetIds(ComponentName(context, kind.provider)).map { kind to it }
    }
  }
  fun enqueue(context: Context) {
    if (installed(context).isEmpty()) return
    // APPEND_OR_REPLACE 不取消正在读取的快照，尾随任务不会漏掉期间发生的最后一次写入。
    WorkManager.getInstance(context).enqueueUniqueWork("desktop-widget-refresh", ExistingWorkPolicy.APPEND_OR_REPLACE,
      OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
  }
  suspend fun refresh(context: Context): Boolean = mutex.withLock {
    val widgets = installed(context)
    if (widgets.isEmpty()) return@withLock true
    try {
      val snapshot = (context.applicationContext as SextantApplication).repository.loadWidgetSnapshot()
      widgets.forEach { (kind, id) ->
        // 配置和尺寸在快照读取后获取，避免显示已取消的隐私设置。
        WidgetRenderer.update(context, kind, id, snapshot)
      }
      true
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      widgets.forEach { (kind, id) -> WidgetRenderer.showError(context, kind, id) }
      false
    }
  }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result = if (WidgetUpdates.refresh(applicationContext)) Result.success()
    else if (runAttemptCount < 3) Result.retry() else Result.failure()
}
