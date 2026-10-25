package com.sextant.app.automation

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaymentPageSnapshotTest {
  @Test fun invisibleAndEditableRootsExplainMissingTextWithoutRetainingSecrets() {
    val root = AccessibilityNodeInfo.obtain()
    try {
      root.text = "secret"
      root.isVisibleToUser = false
      val invisible = collectPaymentPageSnapshot(root)
      assertTrue(invisible.text.isEmpty())
      assertEquals(1, invisible.invisible)
      root.isVisibleToUser = true
      root.isEditable = true
      val editable = collectPaymentPageSnapshot(root)
      assertTrue(editable.text.isEmpty())
      assertEquals(1, editable.sensitive)
      root.isEditable = false
      root.isPassword = true
      assertTrue(collectPaymentPageSnapshot(root).text.isEmpty())
    } finally { root.recycle() }
  }

  @Test fun contentDescriptionsAndTruncatedTextAreReported() {
    val root = AccessibilityNodeInfo.obtain()
    try {
      root.isVisibleToUser = true
      root.contentDescription = "支付成功"
      assertEquals(listOf("支付成功"), collectPaymentPageSnapshot(root).text)
      root.text = "商".repeat(250)
      val snapshot = collectPaymentPageSnapshot(root)
      assertEquals(listOf("商".repeat(200)), snapshot.text)
      assertTrue("textLength" in snapshot.limits)
      assertEquals(1, snapshot.visited)
    } finally { root.recycle() }
  }
}
