package com.sextant.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.screens.AgentChatScrollState
import com.sextant.app.ui.screens.rememberAgentChatScrollState
import com.sextant.app.ui.components.SextantTabContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentChatScrollStateTest {
  @get:Rule val compose = createComposeRule()
  private lateinit var scroll: AgentChatScrollState
  private var chatVisible by mutableStateOf(true)
  private var messageHeight by mutableStateOf(900)
  private var newMessages by mutableIntStateOf(0)
  private var viewportHeight by mutableIntStateOf(400)

  @Composable
  private fun Content() {
    val pages = rememberSaveableStateHolder()
    SextantTabContent(selectedIndex = if (chatVisible) 0 else 1) { tab ->
      if (tab == 0) pages.SaveableStateProvider("chat") {
      scroll = rememberAgentChatScrollState("conversation")
      Column {
        LazyColumn(
          Modifier.size(320.dp, viewportHeight.dp).testTag("messages").nestedScroll(scroll.userScroll),
          state = scroll.list,
          reverseLayout = true,
        ) {
          item(key = "bottom") { Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("bottom")) }
          items(newMessages, key = { "new-$it" }) { Text("New message $it", Modifier.height(80.dp)) }
          item(key = "answer") { Box(Modifier.height(messageHeight.dp)) { Text("Latest answer") } }
          items(30, key = { "old-$it" }) { Text("Old message $it", Modifier.height(80.dp)) }
        }
        TextButton(onClick = scroll::requestBottom, modifier = Modifier.testTag("jump")) { Text("回到底部") }
      }
    }
    }
  }

  private fun readOlderMessages() {
    compose.onNodeWithTag("messages").performTouchInput { swipeDown() }
    compose.waitForIdle()
    compose.runOnIdle {
      assertTrue("Gesture should leave the bottom", scroll.list.canScrollBackward)
      assertFalse("Reading older messages must disable following", scroll.followLatest)
    }
  }

  @Test fun switchingTabsRestoresReadingPositionAndDoesNotResumeFollowing() {
    compose.setContent { Content() }
    readOlderMessages()
    var index = 0
    var offset = 0
    compose.runOnIdle {
      index = scroll.list.firstVisibleItemIndex
      offset = scroll.list.firstVisibleItemScrollOffset
      chatVisible = false
    }
    compose.waitForIdle()
    compose.runOnIdle { chatVisible = true }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(index, scroll.list.firstVisibleItemIndex)
      assertEquals(offset, scroll.list.firstVisibleItemScrollOffset)
      assertFalse(scroll.followLatest)
    }
  }

  @Test fun jumpReachesActualBottomOfAnAnswerTallerThanViewport() {
    compose.setContent { Content() }
    readOlderMessages()
    compose.onNodeWithTag("jump").performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("bottom").assertIsDisplayed()
    compose.runOnIdle {
      assertFalse(scroll.list.canScrollBackward)
      assertEquals(0, scroll.list.firstVisibleItemIndex)
      assertEquals(0, scroll.list.firstVisibleItemScrollOffset)
      assertTrue(scroll.followLatest)
    }
  }

  @Test fun growingAnswerRemainsAtBottomWithoutChangingItemCount() {
    compose.setContent { Content() }
    compose.runOnIdle { messageHeight = 1600 }
    compose.waitForIdle()
    compose.onNodeWithTag("bottom").assertIsDisplayed()
    compose.runOnIdle { assertFalse(scroll.list.canScrollBackward) }
  }

  @Test fun newMessagesAndSmallerViewportKeepFollowingTheBottom() {
    compose.setContent { Content() }
    compose.runOnIdle {
      newMessages = 3
      viewportHeight = 250
    }
    compose.waitForIdle()
    compose.onNodeWithTag("bottom").assertIsDisplayed()
    compose.runOnIdle { assertFalse(scroll.list.canScrollBackward) }
  }

  @Test fun updatesWhileReadingDoNotPullReaderBackToBottom() {
    compose.setContent { Content() }
    readOlderMessages()
    var visibleKey: Any? = null
    var visibleOffset = 0
    compose.runOnIdle {
      visibleKey = scroll.list.layoutInfo.visibleItemsInfo.first().key
      visibleOffset = scroll.list.firstVisibleItemScrollOffset
      newMessages = 3
    }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(visibleKey, scroll.list.layoutInfo.visibleItemsInfo.first().key)
      assertEquals(visibleOffset, scroll.list.firstVisibleItemScrollOffset)
      assertFalse(scroll.followLatest)
    }
  }

  @Test fun savedInstanceRestoresPositionTogetherWithFollowPreference() {
    val restoration = StateRestorationTester(compose)
    restoration.setContent { Content() }
    readOlderMessages()
    var index = 0
    var offset = 0
    compose.runOnIdle {
      index = scroll.list.firstVisibleItemIndex
      offset = scroll.list.firstVisibleItemScrollOffset
    }
    restoration.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(index, scroll.list.firstVisibleItemIndex)
      assertEquals(offset, scroll.list.firstVisibleItemScrollOffset)
      assertFalse(scroll.followLatest)
    }
  }
}
