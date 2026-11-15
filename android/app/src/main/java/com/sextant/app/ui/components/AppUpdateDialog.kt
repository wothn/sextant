package com.sextant.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import com.sextant.app.ui.util.releaseNotesForDisplay
import com.sextant.app.update.UpdateCoordinator

@Composable
fun AppUpdateDialog(coordinator: UpdateCoordinator) {
  val state by coordinator.state.collectAsState()
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle

  fun install() {
    runCatching { coordinator.installerIntent()?.let(context::startActivity) }
      .onFailure { coordinator.installationError() }
  }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    if (coordinator.canInstall()) install() else coordinator.installationError()
  }
  DisposableEffect(lifecycle, coordinator) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) coordinator.check(automatic = true)
    }
    lifecycle.addObserver(observer)
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) coordinator.check(automatic = true)
    onDispose { lifecycle.removeObserver(observer) }
  }

  val release = state.release
  if (state.showDialog && release != null) {
    val notes = releaseNotesForDisplay(release.notes, release.version)
      .ifBlank { "此版本未提供更新说明。" }
    SextantAlertDialog(
      onDismissRequest = coordinator::dismiss,
      title = { Text("发现新版本 ${release.version}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("当前版本 ${coordinator.currentVersion}")
          Markdown(
            markdownState = rememberMarkdownState(notes, retainState = true, immediate = true),
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
            typography = markdownTypography(
              h1 = MaterialTheme.typography.titleLarge,
              h2 = MaterialTheme.typography.titleMedium,
              h3 = MaterialTheme.typography.titleSmall,
              h4 = MaterialTheme.typography.titleSmall,
              h5 = MaterialTheme.typography.titleSmall,
              h6 = MaterialTheme.typography.titleSmall,
            ),
            error = { Text(notes) },
          )
          when {
            state.downloading -> {
              LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
              Text("正在下载 ${ (state.progress * 100).toInt() }%")
            }
            state.ready -> Text("下载与校验完成，点击安装更新。")
            else -> Text("确认后下载安装包，安装需要系统授权。")
          }
          state.message?.let { Text(it) }
        }
      },
      confirmButton = {
        SextantTextButton(enabled = !state.busy, onClick = {
          if (state.ready) {
            if (coordinator.canInstall()) install()
            else runCatching { permissionLauncher.launch(coordinator.permissionIntent()) }
              .onFailure { coordinator.installationError() }
          } else coordinator.download()
        }) { Text(if (state.ready) "安装更新" else "下载更新") }
      },
      dismissButton = {
        SextantTextButton(onClick = coordinator::dismiss) {
          Text(if (state.downloading) "后台下载" else "稍后")
        }
      },
    )
  } else if (state.message != null) {
    SextantAlertDialog(
      onDismissRequest = coordinator::dismissMessage,
      title = { Text("检查更新") },
      text = { Text(state.message.orEmpty()) },
      confirmButton = { SextantTextButton(onClick = coordinator::dismissMessage) { Text("知道了") } },
    )
  }
}
