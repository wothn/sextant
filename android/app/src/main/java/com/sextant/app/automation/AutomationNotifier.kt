package com.sextant.app.automation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.sextant.app.MainActivity
import com.sextant.app.SextantApplication
import com.sextant.app.data.PostedTransaction
import com.sextant.app.data.TransactionType
import java.math.BigDecimal

internal class AutomationNotifier(private val context: Context) {
  fun showPosted(transaction: PostedTransaction) {
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(NotificationManager::class.java)
    ensureChannel(manager)
    val editIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_EDIT_TRANSACTION_ID, transaction.id)
    }
    val editPendingIntent = PendingIntent.getActivity(
      context,
      transaction.id.hashCode(),
      editIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val undoIntent = Intent(context, AutomationUndoReceiver::class.java).apply {
      putExtra(UNDO_TRANSACTION_ID, transaction.id)
      putExtra(UNDO_EXPECTED_UPDATED_AT, transaction.updatedAt)
    }
    val undoPendingIntent = PendingIntent.getBroadcast(
      context,
      transaction.id.hashCode(),
      undoIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val direction = if (transaction.type == TransactionType.Expense) "支出" else "收入"
    val notification = Notification.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_save)
      .setContentTitle("已自动记录$direction ${formatAmount(transaction.amountCents)}")
      .setContentText("${transaction.categoryName} · ${transaction.description}")
      .setContentIntent(editPendingIntent)
      .setAutoCancel(true)
      .setGroup(GROUP_KEY)
      .setCategory(Notification.CATEGORY_STATUS)
      .addAction(Notification.Action.Builder(null, "撤销", undoPendingIntent).build())
      .addAction(Notification.Action.Builder(null, "编辑", editPendingIntent).build())
      .build()
    manager.notify(transaction.id.hashCode(), notification)
  }

  private fun ensureChannel(manager: NotificationManager) {
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "自动记账结果", NotificationManager.IMPORTANCE_LOW).apply {
        description = "显示自动记账结果，并提供撤销和编辑操作"
      },
    )
  }

  private fun formatAmount(amountCents: Long): String = "¥" + BigDecimal.valueOf(amountCents, 2).toPlainString()

  companion object {
    const val EXTRA_EDIT_TRANSACTION_ID = "automation_edit_transaction_id"
    private const val CHANNEL_ID = "automation_results"
    private const val GROUP_KEY = "sextant_automation_results"
  }
}

class AutomationUndoReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val transactionId = intent.getStringExtra(UNDO_TRANSACTION_ID) ?: return
    val expectedUpdatedAt = intent.getLongExtra(UNDO_EXPECTED_UPDATED_AT, -1L).takeIf { it >= 0 }
    val application = context.applicationContext as SextantApplication
    application.automationCoordinator.undo(transactionId, expectedUpdatedAt)
    context.getSystemService(NotificationManager::class.java).cancel(transactionId.hashCode())
  }
}

private const val UNDO_TRANSACTION_ID = "automation_transaction_id"
private const val UNDO_EXPECTED_UPDATED_AT = "automation_expected_updated_at"
