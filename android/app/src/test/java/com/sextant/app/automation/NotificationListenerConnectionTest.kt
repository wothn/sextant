package com.sextant.app.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationListenerConnectionTest {
  private val context get() = RuntimeEnvironment.getApplication()

  private fun connection(granted: Boolean) = NotificationListenerConnection.create(
    context,
    NotificationListenerAccess { granted },
  )

  @Test
  fun `status starts unknown and unauthorized`() {
    val connection = connection(granted = false)
    assertEquals(ListenerConnectionState.Unknown, connection.status.value.connection)
    assertFalse(connection.status.value.authorized)
    assertNull(connection.status.value.lastCapturedAt)
  }

  @Test
  fun `rebind reports not connected when access is not granted`() = runBlocking {
    val connection = connection(granted = false)
    connection.markConnected()

    assertFalse(connection.rebind(context))

    assertFalse(connection.status.value.authorized)
    assertEquals(ListenerConnectionState.Unknown, connection.status.value.connection)
  }

  @Test
  fun `rebind refreshes authorization when access is granted later`() = runBlocking {
    var granted = false
    val connection = NotificationListenerConnection.create(
      context,
      NotificationListenerAccess { granted },
    )

    connection.rebind(context)
    assertFalse(connection.status.value.authorized)

    granted = true
    connection.rebind(context)

    assertTrue(connection.status.value.authorized)
  }

  @Test
  fun `service connection callbacks drive the reported state`() {
    val connection = connection(granted = true)

    connection.markConnected()
    assertTrue(connection.status.value.authorized)
    assertEquals(ListenerConnectionState.Connected, connection.status.value.connection)

    connection.markDisconnected()
    assertEquals(ListenerConnectionState.Disconnected, connection.status.value.connection)
  }

  @Test
  fun `noteCaptured records the last capture time`() {
    val connection = connection(granted = true)
    connection.noteCaptured()

    assertNotNull(connection.status.value.lastCapturedAt)
  }
}
