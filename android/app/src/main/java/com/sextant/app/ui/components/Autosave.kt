package com.sextant.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 开关改完多久没再动就落库：太短会把连点拆成多次写入，太长则「立刻返回」看起来像没生效。 */
const val AUTOSAVE_QUIET_PERIOD_MILLIS = 400L

/**
 * 没有保存按钮的设置的保存状态。
 *
 * 开关类设置改一下就该生效，让人再去找一颗「保存设置」只是在要求他确认自己刚才的动作。
 * 但每次改动都立刻写库也不合适：连点几个开关会写几次，而除最后一次之外的写入没有任何意义。
 * 所以这里负责三件事——合并连续改动、写入期间不接受第二个（并发写同一组键会互相覆盖）、
 * 失败时保留原样并说清楚，而不是静默丢掉这次修改。
 *
 * 只有 [failure] 是给界面看的：写入成功、正在写入都是正常路径，各自再写一行字只是噪音。
 */
class AutosaveController {
  internal var saving by mutableStateOf(false)
    private set
  var failure by mutableStateOf<String?>(null)
    private set
  var pending: Any? = null
    private set

  /**
   * 提交草稿，把结果写成可供界面读取的状态。
   *
   * 失败不吞掉：原因留在 [failure] 里展示，异常继续往上抛给调用方（通常是 `runCatching`）。
   * 取消（离开设置页）不是失败，原样抛出，不写成一条「保存失败」，也不清掉 [pending]——
   * 下次进来还能重试。
   */
  suspend fun save(draft: Any, write: suspend (Any) -> Unit) {
    saving = true
    failure = null
    pending = draft
    try {
      write(draft)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      this.failure = (failure as? IllegalArgumentException)?.message ?: "保存失败，请重试。"
      throw failure
    } finally {
      saving = false
    }
    // 写成功才清 pending：留着的话，从别处返回本页时会误以为还有一次改动没落库。
    pending = null
  }

  fun takePending(): Any? = pending
}

/**
 * 在 [spec] 变化后等一个安静窗口再保存，并保证同一次改动不会被写两遍。
 *
 * 判断「要不要写」的比较基准是 [load] 返回的已落库值而不是界面快照，所以从别的页面返回本页时，
 * 只要没有真的动过开关就不会产生一次多余的写入。
 *
 * [key] 用于隔离不同的编辑对象（例如 `rememberSaveable` 的恢复 key）：同一屏换了对象后基准值
 * 必须重新取，否则会把新对象的状态当成一次改动直接写进库。
 */
@Composable
fun rememberAutosave(
  spec: Any?,
  key: Any? = null,
  quietPeriodMillis: Long = AUTOSAVE_QUIET_PERIOD_MILLIS,
  load: suspend () -> Any?,
  save: suspend (Any) -> Unit,
): AutosaveController {
  val controller = remember { AutosaveController() }
  val onSave by rememberUpdatedState(save)
  val onLoad by rememberUpdatedState(load)

  // 挂载时的快照就是「用户看到的样子」，它天然是这一轮比较的基准：界面此刻显示什么，
  // 库里的值就当作什么。之后只有这个基准真的变了才算一次改动。
  var baseline by remember(key) { mutableStateOf(spec) }

  LaunchedEffect(key) {
    // 读失败不该连页面一起炸掉：基准值退回 null，最坏的结果只是下一次改动多写一遍。
    baseline = runCatching { onLoad() }.getOrNull()
  }

  LaunchedEffect(key, spec) {
    val draft = spec ?: return@LaunchedEffect
    if (controller.saving || draft == baseline) return@LaunchedEffect
    delay(quietPeriodMillis)
    runCatching { controller.save(draft, onSave) }
    // 无论写成没写成都不再重试这一次改动：失败已经写在页面上，硬重试只会掩盖真实原因，
    // 而且用户再动一下开关就会带上整份草稿重来。
    baseline = draft
  }

  return controller
}
