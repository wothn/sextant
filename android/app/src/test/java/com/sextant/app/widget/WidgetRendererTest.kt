package com.sextant.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.sextant.app.R
import com.sextant.app.data.*
import com.sextant.app.ui.util.formatCurrency
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetRendererTest {
  private val context: Context get() = RuntimeEnvironment.getApplication()
  private val now = System.currentTimeMillis()
  private val item = TransactionListItem(Transaction("purchase", null, null, 8650, TransactionType.Expense,
    "午餐", now, now, now), "餐饮", 0L, "未设置", true)
  private val data get() = WidgetSnapshot(now, Summary(0, 8650), 1, Summary(1200000, 384000), 600000,
    listOf(item), emptyList(), ThemePreference.Light)

  @Before fun clear() { context.getSharedPreferences("desktop_widgets", Context.MODE_PRIVATE).edit().clear().commit() }
  private fun render(kind: WidgetKind, snapshot: WidgetSnapshot = data): View =
    WidgetRenderer.render(context, kind, 42, snapshot).apply(context, FrameLayout(context))
  private fun texts(view: View): List<String> = buildList {
    if (view.visibility != View.VISIBLE) return@buildList
    if (view is TextView) add(view.text.toString())
    view.contentDescription?.let { add(it.toString()) }
    if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
  }

  @Test fun allSixInflateInBothThemesAndPrivacyHidesValuesAndProgress() {
    for (appearance in listOf(WidgetAppearance.Light, WidgetAppearance.Dark)) {
      WidgetPreferences(context).save(42, WidgetOptions(appearance, false))
      for (kind in WidgetKind.entries) assertNotNull(render(kind).findViewById<View>(R.id.widget_settings))
      WidgetPreferences(context).save(42, WidgetOptions(appearance, true))
      for (kind in WidgetKind.entries) {
        val view = render(kind)
        val visible = texts(view).joinToString(" ")
        assertFalse(visible, visible.contains("86.50"))
        assertFalse(visible, visible.contains("3,840"))
        assertFalse(visible, visible.contains("12,000"))
        assertFalse(visible, visible.contains("%"))
        assertNull(view.findViewById<View>(R.id.widget_progress))
      }
    }
  }

  @Test fun overBudgetEmptyAndLongAmountsHaveExplicitText() {
    assertTrue(texts(render(WidgetKind.Budget, data.copy(monthSummary = Summary(0, 648000)))).contains("本月预算超出"))
    assertTrue(texts(render(WidgetKind.Budget, data.copy(monthlyBudgetCents = null))).contains("尚未设置预算"))
    assertTrue(texts(render(WidgetKind.Recent, data.copy(recent = emptyList()))).contains("还没有消费记录"))
    val amount = 123456789L
    assertTrue(texts(render(WidgetKind.Today, data.copy(todaySummary = Summary(0, amount)))).contains(formatCurrency(amount)))
  }

  @Test fun recentRowOpensExactTransactionAndHeaderOpensExpenseList() {
    val root = render(WidgetKind.Recent)
    root.findViewById<View>(R.id.widget_row).performClick()
    val transaction = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
    assertEquals("purchase", transaction.getStringExtra(com.sextant.app.automation.AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID))
    root.findViewById<View>(R.id.widget_title).performClick()
    val list = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
    assertEquals("recent", list.getStringExtra(WidgetNavigation.EXTRA_DESTINATION))
  }

  @Test fun pendingIntentsForDifferentTransactionsDoNotOverwriteEachOther() {
    val first = WidgetNavigation.pending(context, "transaction", "one")
    WidgetNavigation.pending(context, "transaction", "two")
    first.send()
    assertEquals("one", shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
      .getStringExtra(com.sextant.app.automation.AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID))
  }

  @Test @Config(sdk = [26]) fun remoteViewsAlsoInflateOnMinimumAndroidVersion() {
    for (kind in WidgetKind.entries) assertNotNull(render(kind))
  }
}
