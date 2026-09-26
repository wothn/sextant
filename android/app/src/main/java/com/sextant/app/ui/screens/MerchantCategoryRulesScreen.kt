package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.*
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantCategoryRulesScreen(state: AppState, repository: SextantRepository, onNavigateBack: () -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  val rules = state.merchantCategoryRules.filter { it.merchantName.contains(query.trim(), ignoreCase = true) }
  var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
  var deletingKey by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
  var parentId by rememberSaveable { mutableStateOf<String?>(null) }
  val save = rememberSaveAction("merchant-rule-edit") { editingKey = null }
  val delete = rememberSaveAction("merchant-rule-delete") { deletingKey = null }
  Column(Modifier.fillMaxSize().imePadding()) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      SextantIconButton(onClick = onNavigateBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回自动记账")
      }
      SectionTitle("商户分类")
    }
    SextantTextField(
      value = query,
      onValueChange = { query = it },
      label = { Text("搜索商户") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp + LocalBottomBarInset.current),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "explanation") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("消费入账时优先使用你记住的分类，其次匹配常见商户。无法确定时保留未分类。", color = SextantColors.TextMuted)
          Text("内置：星巴克、瑞幸等 → 奶茶咖啡；肯德基、麦当劳等 → 正餐；滴滴出行等 → 打车。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodySmall)
          Text("编辑有已识别商户的消费记录时，可选择「以后该商户也使用此分类」。规则按完整商户名匹配，不合并不同门店。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodySmall)
          Text("修改或删除规则只影响以后的消费，不会改动已有流水。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
      }
      item(key = "count") {
        Text(if (query.isBlank()) "已记住 ${rules.size} 个商户" else "找到 ${rules.size} 个商户", style = MaterialTheme.typography.titleMedium)
      }
      if (rules.isEmpty()) {
        item(key = "empty") {
          EmptyCard(
            title = if (state.merchantCategoryRules.isEmpty()) "还没有记住的商户分类" else "没有找到匹配的商户",
            description = if (state.merchantCategoryRules.isEmpty()) "编辑已识别商户的消费记录，勾选「以后该商户也使用此分类」即可添加。" else "试试其他商户名称，或清空搜索查看全部规则。",
          )
        }
      }
      items(rules, key = { it.merchantKey }) { rule ->
        SextantCard {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rule.merchantName, style = MaterialTheme.typography.titleSmall)
            Text(state.categories.firstOrNull { it.id == rule.categoryId && it.type == TransactionType.Expense }
              ?.displayName(state.categories) ?: "分类已停用或变更，请重新选择", color = SextantColors.TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SextantTextButton(onClick = {
                selectedCategoryId = rule.categoryId
                parentId = null
                editingKey = rule.merchantKey
              }) { Text("修改分类") }
              SextantTextButton(onClick = { deletingKey = rule.merchantKey }) { Text("删除规则", color = SextantColors.Danger) }
            }
          }
        }
      }
    }
  }
  editingKey?.let { key ->
    SextantBottomSheet(onDismissRequest = { editingKey = null },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("修改商户分类", style = MaterialTheme.typography.titleLarge)
        Text(state.merchantCategoryRules.firstOrNull { it.merchantKey == key }?.merchantName.orEmpty())
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
          CategoryBrowser(categories = state.categories.filter { it.type == TransactionType.Expense },
            parentId = parentId, onParentChange = { parentId = it }, selectedId = selectedCategoryId,
            onSelect = { selectedCategoryId = it?.id })
        }
        SextantButton(onClick = {
          selectedCategoryId?.let { id -> save.launch { repository.updateMerchantCategoryRule(key, id) } }
        }, enabled = state.categories.any { it.id == selectedCategoryId && it.type == TransactionType.Expense }, modifier = Modifier.fillMaxWidth()) {
          Text("保存分类")
        }
      }
    }
  }
  deletingKey?.let { key ->
    SextantAlertDialog(onDismissRequest = { deletingKey = null }, title = { Text("删除商户规则？") },
      text = { Text("仅删除记住的分类，已有流水不变。今后该商户将重新使用内置规则，无法匹配时保留未分类。") },
      confirmButton = { SextantTextButton(onClick = { delete.launch { repository.deleteMerchantCategoryRule(key) } }) { Text("删除") } },
      dismissButton = { SextantTextButton(onClick = { deletingKey = null }) { Text("取消") } })
  }
}
