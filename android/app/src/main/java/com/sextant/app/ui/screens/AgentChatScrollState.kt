package com.sextant.app.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/** The chat uses reverseLayout, so index zero is always the bottom, even as messages grow. */
@Stable
internal class AgentChatScrollState(
  val list: LazyListState = LazyListState(),
  initialFollowLatest: Boolean = true,
) {
  var followLatest by mutableStateOf(initialFollowLatest)
    private set
  var bottomRequest by mutableIntStateOf(0)
    private set

  val userScroll = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      // Stop following before the drag moves the list, rather than racing a scroll animation.
      if (source == NestedScrollSource.UserInput && available.y > 0f) followLatest = false
      return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
        followLatest = !list.canScrollBackward
      }
      return Offset.Zero
    }
  }

  fun requestBottom() {
    followLatest = true
    bottomRequest++
  }

  companion object {
    val Saver = listSaver<AgentChatScrollState, Any>(
      save = { listOf(it.list.firstVisibleItemIndex, it.list.firstVisibleItemScrollOffset, it.followLatest) },
      restore = { AgentChatScrollState(LazyListState(it[0] as Int, it[1] as Int), it[2] as Boolean) },
    )
  }
}

@Composable
internal fun rememberAgentChatScrollState(conversationId: String?): AgentChatScrollState {
  val state = key(conversationId) {
    rememberSaveable(saver = AgentChatScrollState.Saver) { AgentChatScrollState() }
  }
  LaunchedEffect(state, state.followLatest, state.bottomRequest) {
    if (state.followLatest) state.list.animateScrollToItem(0)
  }
  return state
}
