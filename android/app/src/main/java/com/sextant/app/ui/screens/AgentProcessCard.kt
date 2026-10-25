package com.sextant.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AgentTraceEntry
import com.sextant.app.ui.util.agentElapsedTime
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun AgentProcess(entries: List<AgentTraceEntry>, running: Boolean, status: String) {
  val runId = entries.first().runId
  // An explicit choice survives streaming updates, completion and scrolling off screen.
  var expandedOverride by rememberSaveable(runId) { mutableStateOf<Boolean?>(null) }
  val expanded = expandedOverride ?: running
  val failed = status == "failed"
  val label = when {
    running -> "正在处理"
    failed -> "执行失败"
    status == "cancelled" -> "已停止"
    status == "interrupted" -> "已中断"
    else -> "已完成"
  }
  val elapsed = entries.sumOf { it.elapsedMillis }
  val tools = entries.count { it.kind == "tool" }
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起执行过程" else "展开执行过程") {
          expandedOverride = !expanded
        }.heightIn(min = 48.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (running && !expanded) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = muted)
      else if (failed) Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
      Text("$label · ${agentElapsedTime(elapsed)}" + if (tools > 0) " · ${tools} 次工具调用" else "",
        style = MaterialTheme.typography.labelMedium, color = if (failed) MaterialTheme.colorScheme.error else muted,
        modifier = Modifier.weight(1f))
      Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = muted)
    }
    AnimatedVisibility(expanded) {
      Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))
        entries.forEach { entry -> key(entry.id) {
          AgentProcessEntry(entry, showLoading = running && entry.id == entries.last().id)
        } }
      }
    }
  }
}

@Composable
private fun AgentProcessEntry(entry: AgentTraceEntry, showLoading: Boolean) {
  var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
  val model = entry.kind == "model"
  val busy = entry.status == "running"
  val failed = entry.status == "failed"
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  val title = if (model) "思考" else when (entry.title) {
    "inspect_ledger_schema" -> "读取账本结构"
    "query_ledger" -> "查询账本"
    "prepare_transaction_change" -> "准备记账变更"
    else -> entry.title
  }
  val summary = entry.summary.ifBlank {
    if (model) entry.reasoning.ifBlank {
      when {
        busy && entry.text.isNotBlank() -> "正在组织回答…"
        busy -> "正在分析你的问题…"
        entry.status == "cancelled" -> "已停止"
        entry.status == "interrupted" -> "已中断"
        else -> "未返回思考内容"
      }
    } else if (busy) "正在执行…" else "查看参数与结果"
  }.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起$title" else "展开$title") { expanded = !expanded }
        .heightIn(min = 48.dp).padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (busy && showLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = muted)
      else Icon(when {
        failed -> Icons.Outlined.ErrorOutline
        model -> Icons.Outlined.Psychology
        else -> Icons.Outlined.Terminal
      }, null, Modifier.size(16.dp), tint = if (failed) MaterialTheme.colorScheme.error else muted)
      Text(title, style = MaterialTheme.typography.bodySmall, color = muted)
      Text("· $summary", style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else muted.copy(alpha = 0.8f),
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      if (!busy && entry.elapsedMillis > 0) Text(agentElapsedTime(entry.elapsedMillis),
        style = MaterialTheme.typography.labelSmall, color = muted)
      Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null,
        Modifier.size(16.dp), tint = muted)
    }
    AnimatedVisibility(expanded) {
      Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 8.dp)) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (model) {
            SelectionContainer {
              Text(entry.reasoning.ifBlank { if (busy) "等待模型返回思考内容…" else "模型未返回可展示的思考内容。" },
                style = MaterialTheme.typography.bodySmall, color = muted)
            }
          } else {
            Text(entry.title, style = MaterialTheme.typography.labelSmall, color = muted)
            if (entry.arguments.isNotBlank()) AgentToolContent("参数", entry.arguments)
            if (entry.result.isNotBlank()) AgentToolContent("结果", entry.result)
            else if (busy) Text("等待工具返回结果…", style = MaterialTheme.typography.bodySmall, color = muted)
          }
          if (entry.summary.isNotBlank()) Text(entry.summary, style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else muted)
          if (entry.truncated) Text("展示内容已截断", style = MaterialTheme.typography.labelSmall, color = muted)
        }
      }
    }
  }
}

@Composable
private fun AgentToolContent(label: String, value: String) {
  Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  SelectionContainer {
    Text(remember(value) { prettyJson(value) }, modifier = Modifier.horizontalScroll(rememberScrollState()),
      style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
  }
}

private fun prettyJson(value: String): String = runCatching {
  if (value.trimStart().startsWith("[")) JSONArray(value).toString(2) else JSONObject(value).toString(2)
}.getOrDefault(value)
