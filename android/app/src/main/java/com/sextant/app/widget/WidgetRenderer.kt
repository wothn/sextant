package com.sextant.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
    val views = shell(context, kind, colors)
    val openApp = WidgetNavigation.pending(context, "home")
    fun text(value: String, size: Float = 13f, color: Int = colors.muted) {
      views.addView(R.id.widget_body, RemoteViews(context.packageName, R.layout.widget_text).apply {
        setTextViewText(R.id.widget_text, value)
        setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(R.id.widget_text, color)
        setOnClickPendingIntent(R.id.widget_text, openApp)
      })
    }
    fun row(title: String, subtitle: String, amount: String) {
      // 金额过长时整行改为上下布局，不挤掉右边金额，也不缩小系统字号。
      val estimatedAmountWidth = amount.length * 9 * fontScale
      val stacked = width - 32 - estimatedAmountWidth < 90 * fontScale
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
        setOnClickPendingIntent(R.id.widget_row, openApp)
      }
      views.addView(R.id.widget_body, row)
    }
    when (kind) {
      WidgetKind.Today -> {
        text(money(data.todaySummary.expenseCents), 28f, colors.text)
        if (height >= 170 * fontScale) text(if (data.todayCount == 0) "今天还没有消费记录" else "今天 · ${data.todayCount} 笔")
      }
      WidgetKind.QuickEntry -> {
        text("打开账本", 24f, colors.accent)
        if (height >= 170 * fontScale) text("查看收支与记录")
      }
      WidgetKind.Budget -> {
        val limit = data.monthlyBudgetCents
        if (limit == null || limit <= 0) {
          text("尚未设置预算", 20f, colors.text)
          text("在应用中设置本月额度")
        } else {
          val spent = data.monthSummary.expenseCents
          val over = spent > limit
          views.setTextViewText(R.id.widget_title, if (over) "本月预算超出" else "本月预算剩余")
          text(money(abs(limit - spent)), 28f, if (over) colors.danger else colors.text)
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
            setOnClickPendingIntent(R.id.widget_month_expense, openApp)
            setOnClickPendingIntent(R.id.widget_month_income, openApp)
          })
        } else {
          text("支出 $expense", 18f, colors.text)
          text("收入 $income", 18f, colors.text)
        }
        if (height >= 190 * fontScale) text("本月结余 ${money(data.monthSummary.netCents)}", 12f)
      }
      WidgetKind.Upcoming -> {
        views.setTextViewText(R.id.widget_title, "近期扣费 · 未来7天")
        val count = ((height - 72) / (58 * fontScale)).toInt().coerceIn(1, 2)
        data.upcoming.take(count).forEach { charge ->
          row(charge.expense.name, futureDayLabel(charge.date.toStartOfDayMillis(), date), money(charge.expense.amountCents))
        }
        if (data.upcoming.isEmpty()) text("未来7天没有预计扣费", 16f, colors.text)
        val more = data.upcoming.size - count
        text(if (more > 0) "预计扣费 · 还有${more}笔" else "预计扣费，以实际账单为准", 11f)
      }
      WidgetKind.Recent -> {
        val count = ((height - 56) / (56 * fontScale)).toInt().coerceIn(1, 3)
        data.recent.take(count).forEach { item ->
          row(item.categoryName, dayGroupLabel(item.transaction.transactionDate, date),
            if (options.hideAmounts) "••••" else "−${formatCurrency(item.transaction.amountCents)}")
        }
        if (data.recent.isEmpty()) text("还没有消费记录", 18f, colors.text)
      }
    }
    return views
  }

  internal fun shell(context: Context, kind: WidgetKind, colors: SextantWidgetPalette): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_shell).apply {
      setInt(R.id.widget_background, "setColorFilter", colors.surface)
      setTextViewText(R.id.widget_title, kind.title)
      setTextColor(R.id.widget_title, colors.muted)
      setTextColor(R.id.widget_status, colors.muted)
      setViewVisibility(R.id.widget_status, View.GONE)
      removeAllViews(R.id.widget_body)
      val openApp = WidgetNavigation.pending(context, "home")
      for (target in listOf(R.id.widget_root, R.id.widget_title, R.id.widget_body, R.id.widget_status)) {
        setOnClickPendingIntent(target, openApp)
      }
    }

  fun showError(context: Context, kind: WidgetKind, id: Int) {
    val prefs = WidgetPreferences(context)
    val updated = prefs.updated(id)
    val manager = AppWidgetManager.getInstance(context)
    if (updated > 0) {
      manager.partiallyUpdateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_shell).apply {
        setViewVisibility(R.id.widget_status, View.VISIBLE)
        setTextViewText(R.id.widget_status, "更新失败，请打开应用")
        setContentDescription(R.id.widget_status, "更新失败，当前显示上次成功读取的数据，点击打开应用")
      })
    } else {
      val views = shell(context, kind, palette(context, prefs.get(id), ThemePreference.System))
      views.setViewVisibility(R.id.widget_status, View.VISIBLE)
      views.setTextViewText(R.id.widget_status, "读取失败，请打开应用")
      manager.updateAppWidget(id, views)
    }
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
