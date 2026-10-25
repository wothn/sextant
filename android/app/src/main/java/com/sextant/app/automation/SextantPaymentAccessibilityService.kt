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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read-only and restricted to the two payment apps. No gestures, screenshots or network calls. */
class SextantPaymentAccessibilityService : AccessibilityService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var scanJob: Job? = null
  private var session = UUID.randomUUID().toString()
  private var lastPage: String? = null
  private var lastWindow: Pair<String, Int>? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    (application as SextantApplication).automationCoordinator.noteServiceEvent("page", "page_service_connected")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val packageName = event?.packageName?.toString() ?: return
    if (packageName != WECHAT_PACKAGE && packageName != ALIPAY_PACKAGE) return
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
      event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
    val window = packageName to event.windowId
    if (lastWindow != window) {
      scanJob?.cancel()
      lastPage = null
      session = UUID.randomUUID().toString()
      lastWindow = window
    } else if (scanJob?.isActive == true) {
      // Content events must not keep restarting the delay while the page is loading.
      return
    }
    val scanSession = session
    val eventDetails = mapOf("windowId" to event.windowId.toString(), "eventType" to event.eventType.toString(),
      "windowClass" to event.className?.toString().orEmpty())
    scanJob = serviceScope.launch {
      val app = application as SextantApplication
      val diagnostics = app.repository.automationDiagnostics
      val reasons = mutableListOf<String>()
      val scanDetails = mutableMapOf<String, String>()
      val scanContent = mutableListOf<String>()
      var retainContent = false
      var result = "scan_cancelled"
      var errorType: String? = null
      try {
        val settings = app.repository.loadAutomationSettings()
        retainContent = settings.diagnosticsEnabled && settings.diagnosticContentEnabled
        if (!settings.enabled || !settings.paymentPageEnabled ||
          (packageName == WECHAT_PACKAGE && !settings.wechatEnabled) ||
          (packageName == ALIPAY_PACKAGE && !settings.alipayEnabled)) {
          result = "automation_disabled"
          return@launch
        }
        val candidate = awaitPaymentPageCandidate(pause = { delay(it) }) {
          val root = rootInActiveWindow ?: run {
            reasons.add("root_unavailable")
            scanDetails["attempt${reasons.size}.reason"] = "root_unavailable"
            return@awaitPaymentPageCandidate null
          }
          val texts = try {
            scanDetails["attempt${reasons.size + 1}.rootClass"] = root.className?.toString().orEmpty()
            if (root.packageName?.toString() != packageName ||
              (window.second >= 0 && root.windowId != window.second)) {
              result = "window_changed"
              scanDetails["activePackage"] = root.packageName?.toString().orEmpty()
              scanDetails["activeWindowId"] = root.windowId.toString()
              return@launch
            }
            collectPaymentPageSnapshot(root)
          } finally {
            @Suppress("DEPRECATION")
            root.recycle()
          }
          val inspection = PaymentPageParser.inspect(packageName, texts.text, System.currentTimeMillis())
          reasons.add(inspection.reason)
          val attempt = reasons.size
          scanDetails["attempt$attempt.reason"] = inspection.reason
          scanDetails["attempt$attempt.textCount"] = texts.text.size.toString()
          scanDetails["attempt$attempt.tree"] = texts.statistics()
          if (retainContent) scanContent.addAll(texts.diagnosticContent(attempt, inspection.reason))
          inspection.candidate
        }
        if (candidate == null) {
          result = "scan_exhausted"
          lastPage = null
          session = UUID.randomUUID().toString()
          return@launch
        }
        val identity = "$packageName|${candidate.providerReference}|${candidate.amountCents}|${candidate.description}|${candidate.occurredAt.takeIf { candidate.hasExactTime }}"
        if (identity == lastPage) {
          result = "page_repeated"
          return@launch
        }
        withContext(NonCancellable + Dispatchers.Default) {
          app.automationCoordinator.receivePaymentPage(packageName, scanSession, candidate)
        }
        lastPage = identity
        result = "parsed"
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: SecurityException) {
        result = "access_revoked"
      } catch (error: Exception) {
        result = "scan_error"
        errorType = error.javaClass.simpleName
      } finally {
        // One summary per scan, dispatched outside the retry loop and service lifetime.
        val details = eventDetails + mapOf("attempts" to reasons.size.toString(),
          "reasons" to reasons.joinToString(",")) + scanDetails +
          (errorType?.let { mapOf("errorType" to it) } ?: emptyMap())
        val finalResult = result
        app.applicationScope.launch {
          diagnostics.record("page", "scan", finalResult, packageName, scanSession, details,
            content = { scanContent.toList() })
        }
      }
    }
  }

  override fun onInterrupt() {
    (application as SextantApplication).automationCoordinator.noteServiceEvent("page", "page_service_interrupted")
  }

  override fun onDestroy() {
    (application as SextantApplication).automationCoordinator.noteServiceEvent("page", "page_service_destroyed")
    serviceScope.cancel()
    super.onDestroy()
  }
}

/** Mini-program nodes can arrive after the last window event. Retry briefly, never poll forever. */
internal suspend inline fun awaitPaymentPageCandidate(
  pause: suspend (Long) -> Unit,
  readPage: () -> ParsedTransactionCandidate?,
): ParsedTransactionCandidate? {
  for (waitMillis in listOf(350L, 500L, 1_000L, 2_000L)) {
    pause(waitMillis)
    readPage()?.let { return it }
  }
  return null
}

/** Bound work and ignore editable/password nodes; retain node boundaries for label/value parsing. */
internal fun collectPaymentPageText(root: AccessibilityNodeInfo): List<String> {
  return collectPaymentPageSnapshot(root).text
}

internal data class PaymentPageSnapshot(
  val text: List<String>,
  val visited: Int,
  val invisible: Int,
  val sensitive: Int,
  val limits: Set<String>,
) {
  fun statistics(): String = "visited=$visited,invisible=$invisible,sensitive=$sensitive,limits=${limits.joinToString("|")}"

  // Reserve room for every retry within the recorder's 200-line / 8,000-character cap.
  fun diagnosticContent(attempt: Int, reason: String): List<String> {
    var remaining = 1_700
    val lines = text.take(45).mapNotNull { line ->
      line.take(minOf(remaining, 200)).also { remaining -= it.length }.takeIf { it.isNotEmpty() }
    }
    return listOf("attempt=$attempt reason=$reason textCount=${text.size} contentTruncated=${lines != text}") + lines
  }
}

internal fun collectPaymentPageSnapshot(root: AccessibilityNodeInfo): PaymentPageSnapshot {
  val result = mutableListOf<String>()
  var visited = 0
  var invisible = 0
  var sensitive = 0
  val limits = mutableSetOf<String>()
  fun visit(node: AccessibilityNodeInfo, depth: Int) {
    if (visited >= 500) { limits.add("nodes"); return }
    if (depth > 30) { limits.add("depth"); return }
    if (result.size >= 200) { limits.add("texts"); return }
    visited++
    if (node.isPassword || node.isEditable) { sensitive++; return }
    if (node.isVisibleToUser) {
      val text = node.text?.toString()?.takeIf(String::isNotBlank)
        ?: node.contentDescription?.toString()?.takeIf(String::isNotBlank)
      if (text != null) {
        if (text.length > 200) limits.add("textLength")
        result.add(text.take(200))
      }
    } else invisible++
    if (node.childCount > 100) limits.add("children")
    for (index in 0 until node.childCount.coerceAtMost(100)) {
      val child = node.getChild(index) ?: continue
      try { visit(child, depth + 1) } finally {
        @Suppress("DEPRECATION")
        child.recycle()
      }
    }
  }
  visit(root, 0)
  return PaymentPageSnapshot(result, visited, invisible, sensitive, limits)
}

fun isPaymentPageServiceEnabled(context: Context): Boolean {
  val expected = ComponentName(context, SextantPaymentAccessibilityService::class.java)
  return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
}
