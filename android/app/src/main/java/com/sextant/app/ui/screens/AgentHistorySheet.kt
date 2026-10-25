package com.sextant.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AgentConversationSummary
import com.sextant.app.ui.util.agentConversationDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentHistorySheet(
  history: List<AgentConversationSummary>,
  activeId: String?,
  running: Boolean,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Text("对话历史", style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    if (running) Text("任务运行中，停止后可切换对话。", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    if (history.isEmpty()) {
      Text("还没有历史对话，发送第一条消息后会自动保存。", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp))
    } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
      items(history, key = { it.id }) { entry ->
        val current = entry.id == activeId
        ListItem(
          modifier = Modifier.semantics { selected = current }
            .clickable(enabled = !running, role = Role.Button) { onSelect(entry.id) },
          colors = ListItemDefaults.colors(containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow),
          leadingContent = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
          overlineContent = { Text(agentConversationDate(entry.updatedAt) + if (current) " · 当前对话" else "") },
          headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          supportingContent = { Text(entry.preview, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        )
      }
    }
  }
}
