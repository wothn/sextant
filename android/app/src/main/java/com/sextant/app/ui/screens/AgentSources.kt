package com.sextant.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.sextant.app.data.AgentWebSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentSources(sources: List<AgentWebSource>) {
  val hosts = remember(sources) { sources.mapNotNull { Uri.parse(it.url).host }.distinct().take(3) }
  var expanded by rememberSaveable { mutableStateOf(false) }
  Surface(onClick = { expanded = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
        hosts.forEach { SiteIcon(it) }
      }
      Text("${sources.size} 个来源", style = MaterialTheme.typography.labelLarge)
      Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
    }
  }
  if (expanded) {
    val handler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = { expanded = false }) {
      Text("回答来源", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
      LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(sources) { source ->
          val uri = remember(source.url) { Uri.parse(source.url) }
          ListItem(
            modifier = Modifier.clickable(enabled = uri.scheme in setOf("http", "https") && uri.host != null,
              role = Role.Button) { runCatching { handler.openUri(source.url) } },
            leadingContent = { SiteIcon(uri.host.orEmpty()) },
            headlineContent = { Text(source.title.ifBlank { source.url }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(uri.host.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          )
        }
      }
    }
  }
}

@Composable
private fun SiteIcon(host: String) {
  val fallback: @Composable () -> Unit = {
    Box(contentAlignment = Alignment.Center) {
      Text(host.removePrefix("www.").take(1).uppercase(), style = MaterialTheme.typography.labelSmall)
    }
  }
  Surface(Modifier.size(24.dp).clearAndSetSemantics {}, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
    SubcomposeAsyncImage(
      model = Uri.Builder().scheme("https").authority(host).path("/favicon.ico").build().toString(),
      contentDescription = null, modifier = Modifier.padding(3.dp),
      loading = { fallback() }, error = { fallback() },
    )
  }
}
