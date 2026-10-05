package com.sextant.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.sextant.app.R
import com.sextant.app.data.ThemePreference
import com.sextant.app.data.WidgetSnapshot
import com.sextant.app.data.toLocalDate
import com.sextant.app.data.toStartOfDayMillis
import com.sextant.app.ui.theme.SextantWidgetPalette
import com.sextant.app.ui.theme.sextantWidgetPalette
import com.sextant.app.ui.util.*
import kotlin.math.abs

internal object WidgetRenderer {
  fun update(context: Context, kind: WidgetKind, id: Int, snapshot: WidgetSnapshot) {
    val manager = AppWidgetManager.getInstance(context)
    manager.updateAppWidget(id, render(context, kind, id, snapshot))
    WidgetPreferences(context).markUpdated(id, snapshot.updatedAt)
  }

  internal fun render(context: Context, kind: WidgetKind, id: Int, data: WidgetSnapshot): RemoteViews {
    val options = WidgetPreferences(context).get(id)
    val colors = palette(context, options, data.theme)
    val manager = AppWidgetManager.getInstance(context)
    val bounds = manager.getAppWidgetOptions(id)
    val width = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300).coerceAtLeast(120)
    val height = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240).coerceAtLeast(120)
    val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val money: (Long) -> String = { if (options.hideAmounts) "••••" else formatCurrency(it) }
    val date = data.updatedAt.toLocalDate()
    val views = shell(context, kind, id, colors)
    fun text(value: String, size: Float = 13f, color: Int = colors.muted, destination: String? = null) {
      views.addView(R.id.widget_body, RemoteViews(context.packageName, R.layout.widget_text).apply {
        setTextViewText(R.id.widget_text, value)
        setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(R.id.widget_text, color)
        if (destination != null) setOnClickPendingIntent(R.id.widget_text, WidgetNavigation.pending(context, destination))
      })
    }
    fun row(title: String, subtitle: String, amount: String, destination: String, itemId: String? = null) {
      // 金额过长时整行改为上下布局，不挤掉右边金额，也不缩小系统字号。
      val estimatedAmountWidth = amount.length * 9 * fontScale
      val stacked = width - 24 - estimatedAmountWidth < 90 * fontScale
      val row = RemoteViews(context.packageName, R.layout.widget_row).apply {
        setTextViewText(R.id.widget_row_title, title)
        setTextViewText(R.id.widget_row_subtitle, subtitle)
        setTextViewText(R.id.widget_row_amount, amount)
        setTextColor(R.id.widget_row_title, colors.text)
        setTextColor(R.id.widget_row_subtitle, colors.muted)
        setTextColor(R.id.widget_row_amount, colors.text)
        if (stacked) {
          setViewVisibility(R.id.widget_row_amount, View.GONE)
          setTextViewText(R.id.widget_row_subtitle, amount)
          setTextColor(R.id.widget_row_subtitle, colors.text)
          setInt(R.id.widget_row_subtitle, "setMaxLines", 3)
        }
        setContentDescription(R.id.widget_row, "$title，$subtitle，$amount")
        setOnClickPendingIntent(R.id.widget_row, WidgetNavigation.pending(context, destination, itemId))
      }
      views.addView(R.id.widget_body, row)
    }
    when (kind) {
      WidgetKind.Today -> {
        text(money(data.todaySummary.expenseCents), 28f, colors.text, "today")
        if (height >= 170 * fontScale) text(if (data.todayCount == 0) "今天还没有消费记录" else "今天 · ${data.todayCount} 笔")
      }
      WidgetKind.QuickEntry -> {
        text("＋ 记一笔", 28f, colors.accent, "entry")
        if (height >= 170 * fontScale) text("随手记录每一笔")
      }
      WidgetKind.Budget -> {
        val limit = data.monthlyBudgetCents
        if (limit == null || limit <= 0) {
          text("尚未设置预算", 20f, colors.text, "budget")
          text("点击设置本月额度")
        } else {
          val spent = data.monthSummary.expenseCents
          val over = spent > limit
          views.setTextViewText(R.id.widget_title, if (over) "本月预算超出" else "本月预算剩余")
          text(money(abs(limit - spent)), 28f, if (over) colors.danger else colors.text, "budget")
          if (!options.hideAmounts && height >= 170 * fontScale) {
            val ratio = spent.toDouble() / limit
            views.addView(R.id.widget_body, RemoteViews(context.packageName, R.layout.widget_progress).apply {
              setImageViewBitmap(R.id.widget_progress, progress(ratio, colors, over))
              setContentDescription(R.id.widget_progress, "已用 ${percentageLabel(ratio * 100)}")
            })
            text("已用 ${percentageLabel(ratio * 100)} · 共 ${money(limit)}", 12f)
          }
        }
      }
      WidgetKind.Month -> {
        views.setTextViewText(R.id.widget_title, "${date.monthValue}月收支")
        val expense = money(data.monthSummary.expenseCents)
        val income = money(data.monthSummary.incomeCents)
        if (width >= 280 * fontScale && maxOf(expense.length, income.length) <= 12) {
          views.addView(R.id.widget_body, RemoteViews(context.packageName, R.layout.widget_month_metrics).apply {
            setTextViewText(R.id.widget_expense_amount, expense)
            setTextViewText(R.id.widget_income_amount, income)
            for (label in listOf(R.id.widget_expense_label, R.id.widget_income_label)) setTextColor(label, colors.muted)
            for (amount in listOf(R.id.widget_expense_amount, R.id.widget_income_amount)) setTextColor(amount, colors.text)
            setOnClickPendingIntent(R.id.widget_month_expense, WidgetNavigation.pending(context, "expense"))
            setOnClickPendingIntent(R.id.widget_month_income, WidgetNavigation.pending(context, "income"))
          })
        } else {
          text("支出 $expense", 18f, colors.text, "expense")
          text("收入 $income", 18f, colors.text, "income")
        }
        if (height >= 190 * fontScale) text("本月结余 ${money(data.monthSummary.netCents)}", 12f)
      }
      WidgetKind.Upcoming -> {
        views.setTextViewText(R.id.widget_title, "近期扣费 · 未来7天")
        val count = ((height - 120) / (58 * fontScale)).toInt().coerceIn(1, 2)
        data.upcoming.take(count).forEach { charge ->
          row(charge.expense.name, futureDayLabel(charge.date.toStartOfDayMillis(), date), money(charge.expense.amountCents), "recurring", charge.expense.id)
        }
        if (data.upcoming.isEmpty()) text("未来7天没有预计扣费", 16f, colors.text)
        val more = data.upcoming.size - count
        text(if (more > 0) "预计扣费 · 还有${more}笔" else "预计扣费，以实际账单为准", 11f)
      }
      WidgetKind.Recent -> {
        val count = ((height - 104) / (56 * fontScale)).toInt().coerceIn(1, 3)
        data.recent.take(count).forEach { item ->
          row(item.categoryName, dayGroupLabel(item.transaction.transactionDate, date),
            if (options.hideAmounts) "••••" else "−${formatCurrency(item.transaction.amountCents)}", "transaction", item.transaction.id)
        }
        if (data.recent.isEmpty()) text("还没有消费记录", 18f, colors.text)
        views.setTextViewText(R.id.widget_title, "最近消费 · 查看全部 ›")
      }
    }
    views.setTextViewText(R.id.widget_status, widgetUpdatedLabel(data.updatedAt))
    views.setContentDescription(R.id.widget_status, "${timestampLabel(data.updatedAt)}更新，点击刷新")
    return views
  }

  internal fun shell(context: Context, kind: WidgetKind, id: Int, colors: SextantWidgetPalette): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_shell).apply {
      setInt(R.id.widget_background, "setColorFilter", colors.surface)
      setInt(R.id.widget_add_background, "setColorFilter", colors.accent)
      setTextColor(R.id.widget_add_label, colors.onAccent)
      setTextViewText(R.id.widget_title, kind.title)
      setTextColor(R.id.widget_title, colors.muted)
      setTextColor(R.id.widget_status, colors.muted)
      setTextColor(R.id.widget_settings, colors.muted)
      removeAllViews(R.id.widget_body)
      val destination = when (kind) {
        WidgetKind.Today -> "today"
        WidgetKind.QuickEntry -> "entry"
        WidgetKind.Budget -> "budget"
        WidgetKind.Month -> "expense"
        WidgetKind.Upcoming -> "recurring"
        WidgetKind.Recent -> "recent"
      }
      setOnClickPendingIntent(R.id.widget_title, WidgetNavigation.pending(context, destination))
      setOnClickPendingIntent(R.id.widget_body, WidgetNavigation.pending(context, destination))
      setOnClickPendingIntent(R.id.widget_add, WidgetNavigation.pending(context, "entry"))
      setViewVisibility(R.id.widget_add, if (kind in listOf(WidgetKind.Today, WidgetKind.Month, WidgetKind.Recent)) View.VISIBLE else View.GONE)
      val config = Intent(context, WidgetConfigurationActivity::class.java).apply {
        data = Uri.parse("sextant-widget://configure/$id")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
      }
      setOnClickPendingIntent(R.id.widget_settings, PendingIntent.getActivity(context, id, config,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      val refresh = Intent(context, kind.provider).setAction(SextantWidgetProvider.ACTION_REFRESH)
      setOnClickPendingIntent(R.id.widget_status, PendingIntent.getBroadcast(context, 0, refresh,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

  fun showError(context: Context, kind: WidgetKind, id: Int) {
    val prefs = WidgetPreferences(context)
    val updated = prefs.updated(id)
    val manager = AppWidgetManager.getInstance(context)
    if (updated > 0) {
      manager.partiallyUpdateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_shell).apply {
        setTextViewText(R.id.widget_status, "更新失败\n${widgetUpdatedLabel(updated)}")
        setContentDescription(R.id.widget_status, "更新失败，显示${timestampLabel(updated)}的数据，点击重试")
      })
    } else {
      val views = shell(context, kind, id, palette(context, prefs.get(id), ThemePreference.System))
      views.setTextViewText(R.id.widget_status, "读取失败\n点击重试")
      manager.updateAppWidget(id, views)
    }
  }

  /** 配置保存后先清掉原金额；即使随后读取失败，也不会留下已要求隐藏的数据。 */
  fun clearForConfiguration(context: Context, kind: WidgetKind, id: Int) {
    val colors = palette(context, WidgetPreferences(context).get(id), ThemePreference.System)
    AppWidgetManager.getInstance(context).updateAppWidget(id, shell(context, kind, id, colors))
  }

  private fun palette(context: Context, options: WidgetOptions, theme: ThemePreference): SextantWidgetPalette {
    val dark = when (options.appearance) {
      WidgetAppearance.Dark -> true
      WidgetAppearance.Light -> false
      WidgetAppearance.App -> when (theme) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.System -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
      }
    }
    return sextantWidgetPalette(dark)
  }

  private fun progress(ratio: Double, colors: SextantWidgetPalette, over: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(300, 8, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = colors.track
    canvas.drawRoundRect(0f, 0f, 300f, 8f, 4f, 4f, paint)
    paint.color = if (over) colors.danger else colors.accent
    canvas.drawRoundRect(0f, 0f, (300 * ratio.coerceIn(0.0, 1.0)).toFloat(), 8f, 4f, 4f, paint)
    return bitmap
  }
}
