package com.sextant.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sextant.app.agent.AgentCoordinator
import com.sextant.app.data.*
import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.components.rememberSaveAction
import com.sextant.app.ui.util.*

@Composable
internal fun DraftEditor(draft: AgentDraft, ledger: AppState, coordinator: AgentCoordinator, dismiss: () -> Unit) {
  var amount by rememberSaveable { mutableStateOf(formatAmountPlain(draft.amountCents)) }
  var selected by rememberSaveable { mutableStateOf(draft.categoryId) }
  var note by rememberSaveable { mutableStateOf(draft.description) }
  var choosing by remember { mutableStateOf(false) }
  val save = rememberSaveAction("agent-draft-${draft.id}", showFeedback = false, onSuccess = dismiss)
  val cents = parseAmountCents(amount)
  SextantAlertDialog(onDismissRequest = { if (!save.busy) dismiss() }, title = { Text("调整草稿") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(amount, { amount = it }, label = { Text("金额（元）") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = amount.isNotEmpty() && cents == null, enabled = !save.busy)
        Box {
          OutlinedButton(onClick = { choosing = true }, enabled = !save.busy) {
            Text("分类 · " + (ledger.categories.find { it.id == selected }?.displayName(ledger.categories) ?: "未分类"))
          }
          DropdownMenu(choosing, { choosing = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            DropdownMenuItem(text = { Text("未分类") }, onClick = { selected = null; choosing = false })
            ledger.categories.filter { it.type.storageValue() == draft.type }.forEach { category ->
              DropdownMenuItem(text = { Text(category.displayName(ledger.categories)) },
                onClick = { selected = category.id; choosing = false })
            }
          }
        }
        OutlinedTextField(note, { if (it.length <= 1000) note = it }, label = { Text("备注") }, maxLines = 4, enabled = !save.busy)
        save.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = {
      TextButton(enabled = cents != null && !save.busy, onClick = {
        if (cents != null) save.launch { coordinator.adjust(draft.id, cents, selected, note) }
      }) { Text("保存草稿") }
    }, dismissButton = { TextButton(onClick = dismiss, enabled = !save.busy) { Text("取消") } })
}

@Composable
internal fun CandidateList(drafts: List<AgentDraft>, enabled: Boolean, choose: (String) -> Unit) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Text(if (drafts.first().action == "delete") "选择要删除的记录" else "选择要修改的记录",
      Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
    drafts.forEachIndexed { index, draft ->
      val record = draft.before ?: return@forEachIndexed
      if (index > 0) HorizontalDivider()
      ListItem(
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button) { choose(draft.id) },
        headlineContent = { Text(record.merchantName?.takeIf { it.isNotBlank() } ?: record.description.ifBlank { "无备注" }) },
        supportingContent = { Text(listOfNotNull(agentDraftDate(record.transactionDate),
          record.description.takeIf { !record.merchantName.isNullOrBlank() && it.isNotBlank() && it != record.merchantName }).joinToString(" · ")) },
        trailingContent = { Text(formatCurrency(record.amountCents)) },
      )
    }
  }
}
