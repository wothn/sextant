package com.sextant.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListScope
import com.sextant.app.agent.AgentCoordinator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AgentDraft
import com.sextant.app.data.AppState
import com.sextant.app.data.storageValue
import com.sextant.app.ui.util.agentDraftDate
import com.sextant.app.ui.util.formatCurrency

internal fun LazyListScope.draftItems(drafts: List<AgentDraft>, ledger: AppState, enabled: Boolean, coordinator: AgentCoordinator) {
  val groups = drafts.groupBy { if (it.status == "candidate") it.choiceGroup ?: it.id else it.id }
  groups.entries.toList().asReversed().forEach { (id, group) ->
    item(key = id) {
      if (group.first().status == "candidate") CandidateList(group, enabled, coordinator::choose)
      else DraftCard(group.single(), ledger, enabled, coordinator)
    }
  }
}

@Composable
private fun DraftCard(draft: AgentDraft, ledger: AppState, canDecide: Boolean, coordinator: AgentCoordinator) {
  var editing by rememberSaveable(draft.id) { mutableStateOf(false) }
  if (editing && draft.status == "pending") DraftEditor(draft, ledger, coordinator) { editing = false }
  val colors = MaterialTheme.colorScheme
  val deleting = draft.action == "delete"
  // For a deletion, identify the actual record that the repository will remove.
  val deleted = draft.before?.takeIf { deleting }
  val type = direction(deleted?.type?.storageValue() ?: draft.type)
  val amount = formatCurrency(deleted?.amountCents ?: draft.amountCents)
  val description = deleted?.description ?: draft.description
  val categoryId = if (deleted != null) deleted.categoryId else draft.categoryId
  val methodId = if (deleted != null) deleted.paymentMethodId else draft.paymentMethodId
  fun category(id: String?) = ledger.categories.firstOrNull { it.id == id }?.name ?: "未分类"
  fun method(id: String?) = ledger.allPaymentMethods.firstOrNull { it.id == id }?.name ?: "未设置来源"

  val accent = if (deleting) colors.error else colors.primary
  val verb = when (draft.action) { "create" -> "新增"; "update" -> "修改"; else -> "删除" }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = colors.surfaceContainerLow,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    if (draft.status == "pending") {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(when (draft.action) { "create" -> Icons.Outlined.Add; "update" -> Icons.Outlined.Edit; else -> Icons.Outlined.DeleteOutline },
            null, Modifier.size(18.dp), tint = accent)
          Text("$verb$type", style = MaterialTheme.typography.titleSmall)
          Spacer(Modifier.weight(1f))
          Text("待确认", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        val before = draft.before?.takeIf { draft.action == "update" }
        if (before != null && before.amountCents != draft.amountCents) {
          ChangeLine("金额", formatCurrency(before.amountCents), amount)
        } else Text(amount, style = MaterialTheme.typography.headlineSmall)
        if (before != null && before.description != description) {
          ChangeLine("备注", before.description.ifBlank { "无备注" }, description.ifBlank { "无备注" })
        } else if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
        val date = agentDraftDate(deleted?.transactionDate ?: draft.transactionDate)
        val details = buildList {
          if (before == null || before.categoryId == categoryId) add(category(categoryId))
          if (before == null || before.paymentMethodId == methodId) add(method(methodId))
          if (before == null || before.transactionDate == draft.transactionDate) add(date)
        }
        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (before != null) {
          if (before.type.storageValue() != draft.type) ChangeLine("类型", direction(before.type.storageValue()), type)
          if (before.categoryId != categoryId) ChangeLine("分类", category(before.categoryId), category(categoryId))
          if (before.paymentMethodId != methodId) ChangeLine("来源", method(before.paymentMethodId), method(methodId))
          if (before.transactionDate != draft.transactionDate) ChangeLine("日期", agentDraftDate(before.transactionDate), date)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
          if (!deleting) {
            TextButton(onClick = { editing = true }, enabled = canDecide) { Text("调整") }
            Spacer(Modifier.weight(1f))
          }
          TextButton(onClick = { coordinator.decide(draft.id, false) }, enabled = canDecide) { Text("拒绝") }
          Spacer(Modifier.width(8.dp))
          Button(onClick = { coordinator.decide(draft.id, true) }, enabled = canDecide, colors = ButtonDefaults.buttonColors(containerColor = accent,
            contentColor = if (deleting) colors.onError else colors.onPrimary)) {
            Text(if (deleting) "确认删除" else "确认保存")
          }
        }
      }
    } else {
      Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val rejected = draft.status == "rejected"
        Icon(if (rejected) Icons.Outlined.Close else Icons.Outlined.Check, null,
          Modifier.size(18.dp), tint = if (rejected) colors.onSurfaceVariant else colors.primary)
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
          val status = when (draft.status) {
            "rejected" -> "已拒绝$verb"
            "undo_prepared" -> "已发起撤销"
            "applied" -> when (draft.action) { "create" -> "已记账"; "update" -> "已修改"; else -> "已删除" }
            else -> draft.result ?: "已处理"
          }
          Text(status, style = MaterialTheme.typography.labelLarge)
          Text("$type $amount · ${description.ifBlank { category(categoryId) }}", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (draft.status == "applied") TextButton(onClick = { coordinator.undo(draft.id) }, enabled = canDecide) { Text("撤销") }
      }
    }
  }
}

@Composable
private fun ChangeLine(label: String, previous: String, proposed: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("$previous → $proposed", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
  }
}

private fun direction(type: String) = when (type) {
  "income" -> "收入"
  "transfer" -> "转移"
  else -> "支出"
}
