package com.sextant.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sextant.app.MainActivity
import com.sextant.app.automation.AutomationNotifier

object WidgetNavigation {
  const val EXTRA_DESTINATION = "com.sextant.app.widget.DESTINATION"
  const val EXTRA_RECURRING_ID = "com.sextant.app.widget.RECURRING_ID"
  val destinations = setOf("entry", "today", "budget", "expense", "income", "recurring", "recent")

  fun intent(context: Context, destination: String, itemId: String? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      data = Uri.Builder().scheme("sextant-widget").authority(destination).appendPath(itemId.orEmpty()).build()
      if (destination == "transaction") putExtra(AutomationNotifier.EXTRA_EDIT_TRANSACTION_ID, itemId)
      else {
        putExtra(EXTRA_DESTINATION, destination)
        if (destination == "recurring") putExtra(EXTRA_RECURRING_ID, itemId)
      }
    }

  fun pending(context: Context, destination: String, itemId: String? = null): PendingIntent =
    PendingIntent.getActivity(context, 0, intent(context, destination, itemId),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
