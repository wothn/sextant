package com.sextant.app.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sextant.app.agent.AgentCoordinator
import com.sextant.app.data.*
import com.sextant.app.ui.components.SextantTabContent
import com.sextant.app.ui.screens.AgentChatScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AgentChatScreenNavigationTest {
  @get:Rule val compose = createComposeRule()
  private val context get() = RuntimeEnvironment.getApplication()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private lateinit var repository: SextantRepository
  private lateinit var coordinator: AgentCoordinator
  private var chatVisible by mutableStateOf(true)

  @Before fun setUp() = runBlocking {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    repository = SextantRepository(context)
    val store = AgentConversationStore(context, "chat-navigation-test.json")
    store.load()
    store.update { AgentConversation(messages = (0..15).flatMap { index ->
      listOf(
        AgentMessage("user-$index", "user", "历史问题 $index"),
        AgentMessage("answer-$index", "assistant", buildString {
          append("## 回答 $index\n\n")
          repeat(12) { paragraph ->
            append("段落 $index/$paragraph：这是用于检查切换页面后阅读位置是否保持的长回答。\n\n")
            append("- 第一条说明\n- **第二条说明**\n\n")
          }
        }),
      )
    }) }
    coordinator = AgentCoordinator(context, repository, scope, store = store)
    withTimeout(10_000) { coordinator.ready.first { it } }
    Unit
  }

  @After fun tearDown() {
    scope.cancel()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }

  private fun showPages() {
    compose.setContent {
      MaterialTheme {
        val pages = rememberSaveableStateHolder()
        Box(Modifier.size(360.dp, 640.dp)) {
          SextantTabContent(selectedIndex = if (chatVisible) 0 else 1) { tab ->
            pages.SaveableStateProvider(tab) {
              if (tab == 0) AgentChatScreen(coordinator, repository) else Text("其他页面")
            }
          }
        }
      }
    }
  }

  @Test fun completedMarkdownConversationKeepsReadingPositionAcrossTabs() {
    showPages()
    val messages = compose.onNode(hasScrollToIndexAction())
    // Read inside a long completed answer, not just at a message boundary.
    messages.performScrollToIndex(12)
    messages.performTouchInput { swipeDown() }
    compose.waitForIdle()
    val before = messages.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    val visibleBefore = visibleParagraphs()
    Assert.assertTrue("The test must actually be reading a Markdown answer", visibleBefore.isNotEmpty())
    repeat(3) {
      compose.runOnIdle { chatVisible = false }
      compose.waitForIdle()
      compose.runOnIdle { chatVisible = true }
      compose.waitForIdle()
      val after = messages.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
      assertEquals("Scroll position changed after recreating the full chat screen", before, after, 0.01f)
      assertEquals("The visible Markdown paragraphs changed", visibleBefore, visibleParagraphs())
    }
  }

  @Test fun jumpButtonReachesBottomOfRealMarkdownConversation() {
    showPages()
    val messages = compose.onNode(hasScrollToIndexAction())
    messages.performScrollToIndex(12)
    messages.performTouchInput { swipeDown() }
    compose.onNodeWithContentDescription("回到底部").performClick()
    compose.waitForIdle()
    val position = messages.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    assertEquals(0f, position, 0.01f)
    compose.onNodeWithContentDescription("回到底部").assertDoesNotExist()
  }

  private fun visibleParagraphs(): List<String> =
    compose.onAllNodes(hasText("段落", substring = true)).fetchSemanticsNodes()
      .filter { it.boundsInRoot.height > 0f }
      .map { it.config[SemanticsProperties.Text].joinToString { text -> text.text } }
}
