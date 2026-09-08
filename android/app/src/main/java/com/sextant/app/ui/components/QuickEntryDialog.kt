package com.sextant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.Category
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayName
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.label
import com.sextant.app.ui.util.parseAmountCents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class EntryPanel { Form, Category, Source }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuickEntryDialog(
  state: AppState,
  editing: TransactionListItem? = null,
  initialPaymentMethodId: String? = null,
  onDismiss: () -> Unit,
  onDelete: (() -> Unit)? = null,
  onSubmit: (TransactionType, Long, String?, String?, String, Long) -> Unit,
) {
  var type by remember(editing) { mutableStateOf(editing?.transaction?.type ?: TransactionType.Expense) }
  var amountText by remember(editing) { mutableStateOf(editing?.transaction?.amountCents?.let(::formatAmountPlain).orEmpty()) }
  var categoryId by remember(editing) { mutableStateOf(editing?.transaction?.categoryId ?: state.categories.firstOrNull { it.type == TransactionType.Expense }?.id) }
  var paymentMethodId by remember(editing) { mutableStateOf(editing?.transaction?.paymentMethodId ?: initialPaymentMethodId ?: state.paymentMethods.firstOrNull()?.id) }
  var description by remember(editing) { mutableStateOf(editing?.transaction?.description.orEmpty()) }
  var transactionDate by remember(editing) { mutableStateOf(editing?.transaction?.transactionDate ?: System.currentTimeMillis()) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }
  var amountError by remember { mutableStateOf(false) }
  var panel by remember { mutableStateOf(EntryPanel.Form) }
  var browsingParentId by remember { mutableStateOf<String?>(null) }
  val typeCategories = state.categories.filter { it.type == type }
  val parentCategories = typeCategories.filter { it.parentCategoryId == null }
  val selectedCategory = typeCategories.firstOrNull { it.id == categoryId }
  val paymentMethodOptions = state.paymentMethods.toMutableList().apply {
    val inactive = editing?.transaction?.paymentMethodId?.let { id ->
      state.allPaymentMethods.firstOrNull { it.id == id && !it.isActive }
    }
    if (inactive != null) add(inactive)
  }
  val selectedMethod = paymentMethodOptions.firstOrNull { it.id == paymentMethodId }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    // Keep boundary flings inside selection lists instead of moving the sheet.
    sheetGesturesEnabled = panel == EntryPanel.Form,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = SextantColors.Background,
    tonalElevation = 0.dp,
    dragHandle = { BottomSheetDefaults.DragHandle(color = SextantColors.Border) },
  ) {
    val focus = LocalFocusManager.current
    BackHandler(enabled = panel != EntryPanel.Form) { panel = EntryPanel.Form }
    val panelHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Column(
      Modifier.fillMaxWidth()
        .then(if (panel == EntryPanel.Form) Modifier.heightIn(max = panelHeight) else Modifier.height(panelHeight))
        .padding(horizontal = 24.dp),
    ) {
      Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (panel != EntryPanel.Form) {
          IconButton(onClick = { panel = EntryPanel.Form }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回记账")
          }
        }
        Text(
          when (panel) {
            EntryPanel.Form -> if (editing == null) "记一笔" else "编辑记录"
            EntryPanel.Category -> "选择分类"
            EntryPanel.Source -> "选择来源"
          },
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = SextantColors.Text,
        )
        if (panel == EntryPanel.Form && onDelete != null) {
          TextButton(onClick = onDelete) { Text("删除", color = SextantColors.Danger) }
        }
        IconButton(onClick = { if (panel == EntryPanel.Form) onDismiss() else panel = EntryPanel.Form }) {
          Icon(Icons.Outlined.Close, contentDescription = if (panel == EntryPanel.Form) "关闭记账" else "关闭选择",
            tint = SextantColors.TextSubtle, modifier = Modifier.size(20.dp))
        }
      }
      // Only the body scrolls. The action stays within reach when the IME or a large font reduces space.
      key(panel) {
        Column(
          Modifier.weight(1f, fill = panel != EntryPanel.Form).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          when (panel) {
            EntryPanel.Form -> {
              Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                  .background(SextantColors.SurfaceAlt).padding(4.dp).selectableGroup(),
              ) {
                TransactionType.entries.forEach { option ->
                  val selected = option == type
                  Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                      .background(if (selected) SextantColors.Surface else Color.Transparent)
                      .selectable(selected, role = Role.Tab, onClick = {
                        type = option
                        categoryId = state.categories.firstOrNull { it.type == option && it.parentCategoryId == null }?.id
                      }).heightIn(min = 44.dp).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(option.label, color = if (selected) SextantColors.AccentStrong else SextantColors.TextMuted,
                      style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  }
                }
              }
              Column(Modifier.padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${type.label}金额", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text("¥", fontSize = 28.sp, color = SextantColors.TextMuted, fontWeight = FontWeight.Medium)
                  BasicTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = false },
                    modifier = Modifier.weight(1f).heightIn(min = 60.dp).semantics { contentDescription = "金额" },
                    textStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 42.sp,
                      fontWeight = FontWeight.SemiBold, color = SextantColors.Text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(SextantColors.Accent),
                    singleLine = true,
                    decorationBox = { inner ->
                      Box(contentAlignment = Alignment.CenterStart) {
                        if (amountText.isEmpty()) Text("0.00", fontSize = 42.sp, fontWeight = FontWeight.SemiBold,
                          color = SextantColors.TextSubtle.copy(alpha = 0.55f))
                        inner()
                      }
                    },
                  )
                }
                HorizontalDivider(color = if (amountError) SextantColors.Danger else SextantColors.Border)
                if (amountError) Text("请输入最多两位小数的有效金额", color = SextantColors.Danger,
                  style = MaterialTheme.typography.bodySmall)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryDetailTile("分类", selectedCategory?.displayName(state.categories) ?: "选择分类",
                  categoryIcon(selectedCategory?.name.orEmpty()), Modifier.weight(1f)) {
                  focus.clearFocus()
                  browsingParentId = selectedCategory?.parentCategoryId ?: selectedCategory?.id
                  panel = EntryPanel.Category
                }
                EntryDetailTile("来源", selectedMethod?.name ?: "选择来源", Icons.Outlined.AccountBalanceWallet,
                  Modifier.weight(1f)) {
                  focus.clearFocus()
                  panel = EntryPanel.Source
                }
              }
              Surface(color = SextantColors.Surface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                  Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(21.dp))
                    BasicTextField(
                      value = description, onValueChange = { description = it }, singleLine = true,
                      textStyle = MaterialTheme.typography.bodyMedium.copy(color = SextantColors.Text),
                      cursorBrush = SolidColor(SextantColors.Accent),
                      modifier = Modifier.weight(1f).padding(vertical = 16.dp).semantics { contentDescription = "备注（可选）" },
                      decorationBox = { inner ->
                        Box {
                          if (description.isEmpty()) Text("添加备注…", color = SextantColors.TextSubtle,
                            style = MaterialTheme.typography.bodyMedium)
                          inner()
                        }
                      },
                    )
                  }
                  HorizontalDivider(color = SextantColors.Border.copy(alpha = 0.55f))
                  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(20.dp))
                    TextButton(onClick = { focus.clearFocus(); showDatePicker = true }, modifier = Modifier.weight(1f)) {
                      Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(transactionDate), color = SextantColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { focus.clearFocus(); showTimePicker = true }) {
                      Text(SimpleDateFormat("HH:mm", Locale.CHINA).format(transactionDate), color = SextantColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium)
                    }
                  }
                }
              }
            }
            EntryPanel.Category -> {
              Text("${type.label}分类", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelMedium)
              EntryCategoryGrid(parentCategories, browsingParentId) { category ->
                browsingParentId = category.id
                if (typeCategories.none { it.parentCategoryId == category.id }) {
                  categoryId = category.id
                  panel = EntryPanel.Form
                }
              }
              val parent = parentCategories.firstOrNull { it.id == browsingParentId }
              val children = typeCategories.filter { it.parentCategoryId == parent?.id && parent != null }
              if (parent != null && children.isNotEmpty()) {
                HorizontalDivider(color = SextantColors.Border)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                  Text(parent.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                  TextButton(onClick = { categoryId = parent.id; panel = EntryPanel.Form }) { Text("使用此分类") }
                }
                EntryCategoryGrid(children, categoryId) { category -> categoryId = category.id; panel = EntryPanel.Form }
              }
              if (parentCategories.isEmpty()) Text("暂无可选分类", color = SextantColors.TextMuted)
            }
            EntryPanel.Source -> {
              Text("选择这笔记录的支付来源", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodyMedium)
              paymentMethodOptions.forEach { method ->
                val selected = method.id == paymentMethodId
                Surface(
                  modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).selectable(selected,
                    role = Role.RadioButton, onClick = { paymentMethodId = method.id; panel = EntryPanel.Form }),
                  shape = RoundedCornerShape(18.dp),
                  color = if (selected) SextantColors.AccentSoft else SextantColors.Surface,
                  border = if (selected) BorderStroke(1.dp, SextantColors.AccentMuted) else null,
                ) {
                  Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EntryIcon(Icons.Outlined.AccountBalanceWallet, argbColor(method.color))
                    Text(method.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = "已选择", tint = SextantColors.AccentStrong,
                      modifier = Modifier.size(20.dp))
                  }
                }
              }
              if (paymentMethodOptions.isEmpty()) Text("暂无可选来源", color = SextantColors.TextMuted)
            }
          }
          Spacer(Modifier.height(4.dp))
        }
      }
      Button(
        onClick = {
          if (panel != EntryPanel.Form) {
            panel = EntryPanel.Form
          } else {
            val cents = parseAmountCents(amountText)
            amountError = cents == null
            if (cents != null) onSubmit(type, cents, categoryId, paymentMethodId, description.trim(), transactionDate)
          }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp).height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SextantColors.Accent, contentColor = SextantColors.OnAccent),
      ) {
        Text(if (panel != EntryPanel.Form) "返回记账" else if (editing == null) "记好了" else "保存修改",
          style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
    }
  }
  if (showDatePicker) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = transactionDate)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          pickerState.selectedDateMillis?.let { selected ->
            val old = Calendar.getInstance().apply { timeInMillis = transactionDate }
            transactionDate = Calendar.getInstance().apply {
              timeInMillis = selected
              set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
              set(Calendar.MINUTE, old.get(Calendar.MINUTE))
            }.timeInMillis
          }
          showDatePicker = false
        }) { Text("确定") }
      },
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
    ) { DatePicker(state = pickerState) }
  }
  if (showTimePicker) {
    val current = Calendar.getInstance().apply { timeInMillis = transactionDate }
    val timeState = rememberTimePickerState(current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true)
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      confirmButton = {
        TextButton(onClick = {
          transactionDate = Calendar.getInstance().apply {
            timeInMillis = transactionDate
            set(Calendar.HOUR_OF_DAY, timeState.hour)
            set(Calendar.MINUTE, timeState.minute)
          }.timeInMillis
          showTimePicker = false
        }) { Text("确定") }
      },
      dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
      text = { TimePicker(state = timeState) },
    )
  }
}
@Composable
private fun EntryDetailTile(label: String, value: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
  Surface(modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(18.dp), color = SextantColors.Surface) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SextantColors.TextSubtle)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
          maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun EntryCategoryGrid(categories: List<Category>, selectedId: String?, onSelect: (Category) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    categories.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { category ->
          val selected = category.id == selectedId
          Surface(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
              .selectable(selected, role = Role.RadioButton, onClick = { onSelect(category) }),
            shape = RoundedCornerShape(16.dp),
            color = if (selected) SextantColors.AccentSoft else SextantColors.Surface,
            border = if (selected) BorderStroke(1.dp, SextantColors.AccentMuted) else null,
          ) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
              EntryIcon(categoryIcon(category.name), argbColor(category.color))
              Text(category.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                color = if (selected) SextantColors.AccentStrong else SextantColors.Text, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            }
          }
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun EntryIcon(icon: ImageVector, color: Color) {
  Box(Modifier.size(36.dp).background(color.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
  }
}

private fun categoryIcon(name: String): ImageVector = when {
  listOf("餐", "食", "饭", "外卖").any(name::contains) -> Icons.Outlined.Restaurant
  listOf("茶", "咖啡").any(name::contains) -> Icons.Outlined.LocalCafe
  listOf("交通", "出行", "地铁").any(name::contains) -> Icons.Outlined.DirectionsBus
  listOf("购物", "超市").any(name::contains) -> Icons.Outlined.ShoppingBag
  listOf("住房", "房租", "家").any(name::contains) -> Icons.Outlined.Home
  listOf("学习", "教育").any(name::contains) -> Icons.Outlined.School
  listOf("娱乐", "游戏").any(name::contains) -> Icons.Outlined.SportsEsports
  listOf("人情", "礼").any(name::contains) -> Icons.Outlined.CardGiftcard
  listOf("工资", "基金", "储蓄", "还款").any(name::contains) -> Icons.Outlined.AccountBalanceWallet
  else -> Icons.Outlined.Category
}

