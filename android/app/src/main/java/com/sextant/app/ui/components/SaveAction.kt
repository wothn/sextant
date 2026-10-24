package com.sextant.app.ui.components

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 等待提交后才关闭表单；失败保留草稿，取消不伪装成业务错误。 */
class SaveAction(private val externalScope: CoroutineScope? = null) : ViewModel() {
  var completion by mutableStateOf(0L)
    private set
  private var acknowledged = 0L
  val hasCompletion get() = completion > acknowledged
  fun acknowledge() { acknowledged = completion }
  var busy by mutableStateOf(false)
    private set
  var error by mutableStateOf<String?>(null)
    private set

  fun launch(work: suspend () -> Unit) {
    if (busy || hasCompletion) return
    error = null
    busy = true
    (externalScope ?: viewModelScope).launch {
      try {
        work()
        completion++
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        error = (failure as? IllegalArgumentException)?.message ?: "保存失败，请重试。"
      } finally {
        busy = false
      }
    }
  }

  fun dismissError() { error = null }
}

@Composable
fun rememberSaveAction(key: String, showFeedback: Boolean = true, onSuccess: () -> Unit = {}): SaveAction {
  val context = LocalContext.current
  val owner = remember(context) {
    var current = context
    while (current is ContextWrapper && current !is ComponentActivity) current = current.baseContext
    requireNotNull(current as? ComponentActivity) { "保存操作需要 Activity 的 ViewModelStore" }
  }
  val action = remember(owner, key) { ViewModelProvider(owner)["save:$key", SaveAction::class.java] }
  val success by rememberUpdatedState(onSuccess)
  LaunchedEffect(action.completion) {
    if (action.hasCompletion) {
      success()
      action.acknowledge()
    }
  }
  // 写入通常只有几十毫秒，不再为它弹一个不可取消的模态框；调用方用 `busy` 禁用按钮即可。
  action.error?.takeIf { showFeedback }?.let { message ->
    SextantAlertDialog(
      onDismissRequest = action::dismissError,
      title = { Text("操作未完成") },
      text = { Text(message) },
      confirmButton = { SextantTextButton(onClick = action::dismissError) { Text("返回修改") } },
    )
  }
  return action
}
