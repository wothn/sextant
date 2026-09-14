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
import com.sextant.app.R
import com.sextant.app.SextantApplication
import com.sextant.app.data.PostedTransaction
import com.sextant.app.data.TransactionType
import java.math.BigDecimal
import kotlinx.coroutines.launch

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
      // 必须是应用自己的资源：setSmallIcon(int) 的契约是「本应用包内的 drawable」，
      // 它会把这个 ID 连同应用包名一起交给系统（Icon.createWithResource），系统再按
      // 应用包去解析。以前这里借的是 android.R.drawable.ic_menu_save——一个属于 android
      // 包、且早已废弃的软盘图标，解析失败时系统只记一行日志然后画一个空白图标。
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("已自动记录$direction ${formatAmount(transaction.amountCents)}")
      .setContentText(listOf(transaction.categoryName, transaction.description).filter(String::isNotBlank).joinToString(" · "))
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
  /**
   * 通知上的「撤销」。
   *
   * 撤销要读一笔流水、删掉它、再把事件状态改回去，全是数据库工作，而 `onReceive` 跑在
   * 主线程上——所以真正的动作交给应用级作用域，通知上的这条记录则立刻收掉：用户已经
   * 明确表达了意图，留着一个可能已经失效的「撤销」按钮只会让人再点一次。
   */
  override fun onReceive(context: Context, intent: Intent) {
    val transactionId = intent.getStringExtra(UNDO_TRANSACTION_ID) ?: return
    val expectedUpdatedAt = intent.getLongExtra(UNDO_EXPECTED_UPDATED_AT, -1L).takeIf { it >= 0 }
    val application = context.applicationContext as SextantApplication
    application.applicationScope.launch {
      application.automationCoordinator.undo(transactionId, expectedUpdatedAt)
    }
    context.getSystemService(NotificationManager::class.java).cancel(transactionId.hashCode())
  }
}

private const val UNDO_TRANSACTION_ID = "automation_transaction_id"
private const val UNDO_EXPECTED_UPDATED_AT = "automation_expected_updated_at"
