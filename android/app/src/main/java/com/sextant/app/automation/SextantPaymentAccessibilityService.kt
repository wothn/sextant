package com.sextant.app.automation

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sextant.app.SextantApplication
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** Read-only and restricted to the two payment apps. No gestures, screenshots or network calls. */
class SextantPaymentAccessibilityService : AccessibilityService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val scanLock = Mutex()
  private var session = UUID.randomUUID().toString()
  private var lastPage: String? = null
  private var lastWindowId: Int? = null

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val packageName = event?.packageName?.toString() ?: return
    if (packageName != WECHAT_PACKAGE && packageName != ALIPAY_PACKAGE) return
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
      event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
    // A single delayed scan lets the page settle without starvation from continuous content events.
    if (!scanLock.tryLock()) return
    val windowId = event.windowId
    serviceScope.launch {
      try {
        if (lastWindowId != windowId) {
          lastPage = null
          session = UUID.randomUUID().toString()
          lastWindowId = windowId
        }
        delay(350)
        val app = application as SextantApplication
        val settings = app.repository.loadAutomationSettings()
        if (!settings.enabled || !settings.paymentPageEnabled) return@launch
        if (packageName == WECHAT_PACKAGE && !settings.wechatEnabled) return@launch
        if (packageName == ALIPAY_PACKAGE && !settings.alipayEnabled) return@launch
        val root = rootInActiveWindow ?: return@launch
        val texts = try {
          if (root.packageName?.toString() != packageName) return@launch
          collectPaymentPageText(root)
        } finally {
          @Suppress("DEPRECATION")
          root.recycle()
        }
        val candidate = PaymentPageParser.parse(packageName, texts, System.currentTimeMillis())
        if (candidate == null) {
          lastPage = null
          session = UUID.randomUUID().toString()
          return@launch
        }
        val identity = "$packageName|${candidate.providerReference}|${candidate.amountCents}|${candidate.description}|${candidate.occurredAt.takeIf { candidate.hasExactTime }}"
        if (identity == lastPage) return@launch
        app.automationCoordinator.receivePaymentPage(packageName, session, candidate)
        lastPage = identity
      } catch (_: SecurityException) {
        // Permission can be revoked while a scan is in progress.
      } finally {
        scanLock.unlock()
      }
    }
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }
}

/** Bound work and ignore editable/password nodes; retain node boundaries for label/value parsing. */
internal fun collectPaymentPageText(root: AccessibilityNodeInfo): List<String> {
  val result = mutableListOf<String>()
  var visited = 0
  fun visit(node: AccessibilityNodeInfo, depth: Int) {
    if (++visited > 500 || depth > 30 || result.size >= 200) return
    if (node.isPassword || node.isEditable) return
    if (node.isVisibleToUser) {
      val text = node.text?.toString()?.takeIf(String::isNotBlank)
        ?: node.contentDescription?.toString()?.takeIf(String::isNotBlank)
      if (text != null) result.add(text.take(200))
    }
    for (index in 0 until node.childCount.coerceAtMost(100)) {
      val child = node.getChild(index) ?: continue
      try { visit(child, depth + 1) } finally {
        @Suppress("DEPRECATION")
        child.recycle()
      }
    }
  }
  visit(root, 0)
  return result
}

fun isPaymentPageServiceEnabled(context: Context): Boolean {
  val expected = ComponentName(context, SextantPaymentAccessibilityService::class.java)
  return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
}
