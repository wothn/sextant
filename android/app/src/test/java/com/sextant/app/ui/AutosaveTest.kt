package com.sextant.app.ui

import com.sextant.app.ui.components.AutosaveController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * 自动保存的状态机：只有失败要能被界面看见并重试，成功之后不能留着「还有一次改动」。
 *
 * 这里不驱动重组，也不等真实的安静窗口——合并连续改动那段由
 * [com.sextant.app.ui.components.rememberAutosave] 的 `LaunchedEffect` 承担，需要 Compose
 * 测试环境才能断言。
 */
class AutosaveTest {
  private class FakeSettings {
    var stored: String? = null
    var writes = 0
    var failure: Exception? = null

    suspend fun update(value: String) {
      failure?.let { throw it }
      writes++
      stored = value
    }
  }

  @Test
  fun successfulWriteIsSilentAndClearsPending() = runBlocking {
    val repo = FakeSettings()
    val controller = AutosaveController()

    controller.save("enabled=true") { repo.update(it as String) }

    assertEquals(1, repo.writes)
    assertEquals("enabled=true", repo.stored)
    // 正常路径没有文案：页面只读 failure，它必须保持为空。
    assertNull(controller.failure)
    // 留着 pending 会让「从别处返回本页」误以为还有一次改动没落库。
    assertNull(controller.pending)
  }

  @Test
  fun failedWriteReportsTheReasonAndKeepsTheDraftRetryable() = runBlocking {
    val repo = FakeSettings()
    val controller = AutosaveController()

    repo.failure = IllegalStateException("数据库写入失败")
    val thrown = runCatching {
      controller.save("enabled=true") { repo.update(it as String) }
    }.exceptionOrNull()

    assertNotNull("写入失败必须继续抛出，由调用方决定怎么处理", thrown)
    assertEquals("保存失败，请重试。", controller.failure)
    assertFalse(controller.saving)
    assertEquals("enabled=true", controller.takePending())

    repo.failure = null
    controller.save("enabled=true") { repo.update(it as String) }

    assertEquals(1, repo.writes)
    assertNull(controller.failure)
    assertNull(controller.pending)
  }

  @Test
  fun savingAndPendingRemainSetUntilTheWriteCompletes() = runBlocking {
    val repo = FakeSettings()
    val controller = AutosaveController()
    val commit = CompletableDeferred<Unit>()
    val write = launch(start = CoroutineStart.UNDISPATCHED) {
      controller.save("enabled=true") { value ->
        commit.await()
        repo.update(value as String)
      }
    }

    try {
      assertTrue(controller.saving)
      assertEquals("enabled=true", controller.takePending())
      assertEquals(0, repo.writes)
      assertNull(repo.stored)
      commit.complete(Unit)
      write.join()
      assertEquals(1, repo.writes)
      assertEquals("enabled=true", repo.stored)
      assertFalse(controller.saving)
      assertNull(controller.pending)
    } finally { write.cancel() }
  }

  @Test
  fun canceledWriteIsNotReportedAsFailure() = runBlocking {
    val controller = AutosaveController()

    // 离开设置页时这次写入会被取消；取消是「这次不算数」，不是「保存失败」。
    val thrown = runCatching {
      controller.save("enabled=true") { throw CancellationException("page left") }
    }.exceptionOrNull()

    assertTrue(thrown is CancellationException)
    assertFalse(controller.saving)
    assertNull(controller.failure)
    // 草稿留着：回到这一页时它还是「没写进去的改动」。
    assertEquals("enabled=true", controller.takePending())
  }
}
