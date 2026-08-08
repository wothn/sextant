package com.sextant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
  onChanged: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  var showAdd by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<PaymentMethod?>(null) }
  var pendingDelete by remember { mutableStateOf<PaymentMethod?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
        }
        SectionTitle("来源管理")
      }
      TextButton(onClick = { showAdd = true }) { Text("新增来源") }
    }

    Text(
      "停用后不会出现在记账和筛选中，历史流水仍会保留来源名称。",
      color = SextantColors.TextMuted,
      style = MaterialTheme.typography.bodyMedium,
    )

    if (methods.isEmpty()) {
      EmptyCard("暂无来源", "新增现金、银行卡或支付平台等消费来源。")
    } else {
      methods.forEach { method ->
        PaymentMethodRow(
          method = method,
          onEdit = { editing = method },
          onActiveChanged = { active ->
            repository.setPaymentMethodActive(method.id, active)
            onChanged()
          },
          onDelete = if (!method.isBuiltIn && method.transactionCount == 0L) {
            { pendingDelete = method }
          } else {
            null
          },
        )
      }
    }
  }

  if (showAdd) {
    PaymentMethodDialog(
      methods = methods,
      initial = null,
      onDismiss = { showAdd = false },
      onSubmit = { name, color ->
        repository.addPaymentMethod(name, color)
        showAdd = false
        onChanged()
      },
    )
  }

  editing?.let { method ->
    PaymentMethodDialog(
      methods = methods,
      initial = method,
      onDismiss = { editing = null },
      onSubmit = { name, color ->
        repository.updatePaymentMethod(method.id, name, color)
        editing = null
        onChanged()
      },
    )
  }

  pendingDelete?.let { method ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("删除“${method.name}”？") },
      text = { Text("这个来源尚未被流水使用，删除后无法恢复。") },
      confirmButton = {
        TextButton(onClick = {
          repository.deletePaymentMethod(method.id)
          pendingDelete = null
          onChanged()
        }) { Text("删除") }
      },
      dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
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
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ColorDot(argbColor(method.color))
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(method.name, fontWeight = FontWeight.SemiBold)
        Text(
          buildList {
            add(if (method.isBuiltIn) "内置来源" else "自定义来源")
            add(if (method.isActive) "使用中" else "已停用")
            if (method.transactionCount > 0) add("${method.transactionCount} 笔流水")
          }.joinToString(" · "),
          color = SextantColors.TextMuted,
          style = MaterialTheme.typography.bodySmall,
        )
      }
      if (onDelete != null) {
        IconButton(onClick = onDelete) {
          Icon(Icons.Outlined.Delete, contentDescription = "删除${method.name}")
        }
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Outlined.Edit, contentDescription = "编辑${method.name}")
      }
      Switch(
        checked = method.isActive,
        onCheckedChange = onActiveChanged,
        colors = sextantSwitchColors(),
        modifier = Modifier.semantics { contentDescription = if (method.isActive) "停用${method.name}" else "启用${method.name}" },
      )
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
  var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
  var color by remember(initial) { mutableStateOf(initial?.color ?: categoryColorOptions.first().first) }
  val trimmedName = name.trim()
  val nameError = when {
    trimmedName.isEmpty() -> null
    trimmedName.length > 40 -> "名称不能超过 40 个字符"
    methods.any { it.id != initial?.id && it.name.equals(trimmedName, ignoreCase = true) } -> "这个来源名称已存在"
    else -> null
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "添加来源" else "编辑来源") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
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
      Button(
        onClick = { onSubmit(trimmedName, color) },
        enabled = trimmedName.isNotEmpty() && nameError == null,
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}
