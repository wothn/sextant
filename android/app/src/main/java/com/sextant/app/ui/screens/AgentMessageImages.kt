package com.sextant.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sextant.app.data.AgentImageStore
import kotlinx.coroutines.CancellationException

@Composable
internal fun AgentMessageImages(ids: List<String>, store: AgentImageStore, onRemove: ((String) -> Unit)? = null) {
  var viewing by remember { mutableStateOf<String?>(null) }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ids.forEachIndexed { index, id -> key(id) {
      Box(Modifier.size(if (onRemove == null) 160.dp else 88.dp)) {
        AgentImage(id, store, false, Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
          .clickable(role = Role.Button, onClickLabel = "查看第${index + 1}张图片") { viewing = id })
        if (onRemove != null) FilledIconButton(onClick = { onRemove(id) }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
          Icon(Icons.Outlined.Close, "移除第${index + 1}张图片", Modifier.size(18.dp))
        }
      }
    } }
  }
  viewing?.let { id ->
    Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
          AgentImage(id, store, true, Modifier.fillMaxSize().padding(16.dp))
          IconButton(onClick = { viewing = null }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Outlined.Close, "关闭图片") }
        }
      }
    }
  }
}

@Composable
private fun AgentImage(id: String, store: AgentImageStore, fullSize: Boolean, modifier: Modifier) {
  val result by produceState<Result<Bitmap>?>(null, id, fullSize) {
    value = try { Result.success(store.thumbnail(id, fullSize)) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
  }
  val bitmap = result?.getOrNull()
  if (bitmap != null) Image(bitmap.asImageBitmap(), "上传的图片",
    modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = ContentScale.Fit)
  else Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Icon(Icons.Outlined.Image, null)
      if (result?.isFailure == true) Text("图片不可用", style = MaterialTheme.typography.labelSmall)
    }
  }
}
