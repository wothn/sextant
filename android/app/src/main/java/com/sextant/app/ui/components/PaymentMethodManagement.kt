package com.sextant.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sextant.app.data.PaymentMethod
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.categoryColorOptions

@Composable
fun PaymentMethodManagement(
  methods: List<PaymentMethod>,
  repository: SextantRepository,
  onNavigateBack: () -> Unit,
) {
  var showAdd by rememberSaveable { mutableStateOf(false) }
  var editingId by rememberSaveable { mutableStateOf<String?>(null) }
  val editing = methods.firstOrNull { it.id == editingId }
  var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
  val pendingDelete = methods.firstOrNull { it.id == pendingDeleteId }
  // 写来源是挂起调用（Room 不允许主线程访问数据库），而开关/对话框回调不是协程。
  val saveAction = rememberSaveAction("payments") {
    showAdd = false
    editingId = null
    pendingDeleteId = null
  }

  val listState = rememberLazyListState()
  Column(Modifier.fillMaxSize()) {
    SextantManagementHeader("来源管理", "返回设置", onNavigateBack, "新增来源", { showAdd = true })
    LazyColumn(
      state = listState, modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp + LocalBottomBarInset.current),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Text("停用后不再出现在记账和筛选中，历史流水保留来源名称。",
          color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(bottom = 12.dp))
      }
      if (methods.isEmpty()) {
        item { EmptyCard("暂无来源", "新增现金、银行卡或支付平台等消费来源。") }
      } else {
        listOf(true, false).forEach { active ->
          val group = methods.filter { it.isActive == active }
          if (group.isNotEmpty()) {
            item(key = "group:$active") {
              Text("${if (active) "使用中" else "已停用"} · ${group.size} 个",
                color = SextantColors.TextMuted, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = if (active) 0.dp else 16.dp, bottom = 4.dp))
            }
            items(group, key = { it.id }) { method ->
              PaymentMethodRow(method,
                onEdit = { editingId = method.id },
                onActiveChanged = { value -> saveAction.launch { repository.setPaymentMethodActive(method.id, value) } },
                onDelete = if (!method.isBuiltIn && method.transactionCount == 0L) ({ pendingDeleteId = method.id }) else null)
            }
          }
        }
      }
    }
  }

  if (showAdd) {
    PaymentMethodDialog(
      methods = methods,
      initial = null,
      onDismiss = { showAdd = false },
      onSubmit = { name, color ->
        saveAction.launch {
          repository.addPaymentMethod(name, color)
        }
      },
    )
  }

  editing?.let { method ->
    PaymentMethodDialog(
      methods = methods,
      initial = method,
      onDismiss = { editingId = null },
      onSubmit = { name, color ->
        saveAction.launch {
          repository.updatePaymentMethod(method.id, name, color)
        }
      },
    )
  }

  pendingDelete?.let { method ->
    SextantAlertDialog(
      onDismissRequest = { pendingDeleteId = null },
      title = { Text("删除“${method.name}”？") },
      text = { Text("这个来源尚未被流水使用，删除后无法恢复。") },
      confirmButton = {
        SextantTextButton(onClick = {
          saveAction.launch {
            repository.deletePaymentMethod(method.id)
          }
        }) { Text("删除") }
      },
      dismissButton = { SextantTextButton(onClick = { pendingDeleteId = null }) { Text("取消") } },
    )
  }
}

@Composable
private fun PaymentMethodRow(
  method: PaymentMethod,
  onEdit: () -> Unit,
  onActiveChanged: (Boolean) -> Unit,
  onDelete: (() -> Unit)?,
) {
  var showActions by rememberSaveable { mutableStateOf(false) }
  Row(
    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
      .clickable(onClickLabel = "编辑${method.name}", onClick = onEdit)
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CategoryIcon(Icons.Outlined.AccountBalanceWallet, argbColor(method.color))
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(method.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        buildList {
          add(if (method.isActive) "使用中" else "已停用")
          if (method.transactionCount > 0) add("${method.transactionCount} 笔流水")
        }.joinToString(" · "),
        color = SextantColors.TextMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    SextantSwitch(
      checked = method.isActive,
      onCheckedChange = onActiveChanged,
      modifier = Modifier.semantics { contentDescription = if (method.isActive) "停用${method.name}" else "启用${method.name}" },
    )
    Box {
      SextantIconButton(onClick = { showActions = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "管理${method.name}")
      }
      SextantDropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
        SextantDropdownMenuItem(text = { Text("编辑") }, onClick = { showActions = false; onEdit() })
        if (onDelete != null) {
          SextantDropdownMenuItem(text = { Text("删除", color = SextantColors.Danger) },
            onClick = { showActions = false; onDelete() })
        }
      }
    }
  }
}

@Composable
private fun PaymentMethodDialog(
  methods: List<PaymentMethod>,
  initial: PaymentMethod?,
  onDismiss: () -> Unit,
  onSubmit: (String, Long) -> Unit,
) {
  var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
  var color by rememberSaveable(initial?.id) { mutableStateOf(initial?.color ?: categoryColorOptions.first().first) }
  val trimmedName = name.trim()
  val nameError = when {
    trimmedName.isEmpty() -> null
    trimmedName.length > 40 -> "名称不能超过 40 个字符"
    methods.any { it.id != initial?.id && it.name.equals(trimmedName, ignoreCase = true) } -> "这个来源名称已存在"
    else -> null
  }

  SextantAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "添加来源" else "编辑来源") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SextantTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("来源名称") },
          placeholder = { Text("例如：信用卡、公司报销") },
          singleLine = true,
          isError = nameError != null,
          supportingText = nameError?.let { message -> ({ Text(message) }) },
          modifier = Modifier.fillMaxWidth(),
        )
        Text("标识颜色", style = MaterialTheme.typography.labelLarge)
        FlowChips(
          items = categoryColorOptions,
          selectedId = color.toString(),
          label = { it.second },
          color = { argbColor(it.first) },
          onSelect = { color = it.first },
        )
      }
    },
    confirmButton = {
      SextantButton(onClick = { onSubmit(trimmedName, color) },
        enabled = trimmedName.isNotEmpty() && nameError == null,
      ) { Text("保存") }
    },
    dismissButton = { SextantTextButton(onClick = onDismiss) { Text("取消") } },
  )
}
