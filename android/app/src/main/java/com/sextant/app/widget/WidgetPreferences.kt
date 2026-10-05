package com.sextant.app.widget

import android.content.Context

enum class WidgetAppearance(val label: String) { App("跟随应用"), Light("浅色"), Dark("深色") }
data class WidgetOptions(val appearance: WidgetAppearance = WidgetAppearance.App, val hideAmounts: Boolean = false)

internal class WidgetPreferences(context: Context) {
  private val prefs = context.getSharedPreferences("desktop_widgets", Context.MODE_PRIVATE)
  fun get(id: Int) = WidgetOptions(
    WidgetAppearance.entries.firstOrNull { it.name == prefs.getString("$id.theme", null) } ?: WidgetAppearance.App,
    prefs.getBoolean("$id.hide", false),
  )
  fun save(id: Int, options: WidgetOptions) {
    check(prefs.edit().putString("$id.theme", options.appearance.name)
      .putBoolean("$id.hide", options.hideAmounts).remove("$id.updated").commit())
  }
  fun updated(id: Int) = prefs.getLong("$id.updated", 0)
  fun markUpdated(id: Int, time: Long) { prefs.edit().putLong("$id.updated", time).apply() }
  fun delete(id: Int) { prefs.edit().remove("$id.theme").remove("$id.hide").remove("$id.updated").apply() }
}
