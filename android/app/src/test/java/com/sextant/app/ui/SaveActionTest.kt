package com.sextant.app.ui

import com.sextant.app.ui.components.SaveAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SaveActionTest {
  @Test
  fun completionWaitsForTheRecreatedScreenBeforeAcceptingAnotherWrite() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val action = SaveAction(scope)
      var writes = 0
      action.launch { writes++ }
      assertTrue(action.hasCompletion)
      action.launch { writes++ }
      assertEquals(1, writes)
      action.acknowledge()
      assertFalse(action.hasCompletion)
      action.launch { writes++ }
      assertEquals(2, writes)
    } finally { scope.cancel() }
  }

  @Test
  fun waitsForCommitAndRejectsDuplicateSubmission() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val action = SaveAction(scope)
      val commit = CompletableDeferred<Unit>()
      var closed = false
      var writes = 0
      action.launch { writes++; commit.await(); closed = true }
      action.launch { writes++ }
      assertTrue(action.busy)
      assertFalse(closed)
      assertEquals(1, writes)
      commit.complete(Unit)
      assertTrue(closed)
      assertFalse(action.busy)
      assertNull(action.error)
    } finally { scope.cancel() }
  }

  @Test
  fun failureKeepsFormOpenAndAllowsRetry() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val action = SaveAction(scope)
      var closed = false
      action.launch { require(false) { "来源名称已存在" }; closed = true }
      assertFalse(closed)
      assertEquals("来源名称已存在", action.error)
      assertFalse(action.busy)
      action.dismissError()
      action.launch { closed = true }
      assertTrue(closed)
      assertNull(action.error)
    } finally { scope.cancel() }
  }

  @Test
  fun cancellationDoesNotReportBusinessFailureOrCloseForm() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val action = SaveAction(scope)
    var closed = false
    action.launch { CompletableDeferred<Unit>().await(); closed = true }
    scope.cancel()
    assertFalse(closed)
    assertFalse(action.busy)
    assertNull(action.error)
  }
}
