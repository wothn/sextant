package com.sextant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.label

/**
 * Shared hierarchy and rows for entry, filtering and category management.
 *
 * 当前层级由调用方持有（[parentId] / [onParentChange]），列表只负责画出这一层，因为同一时刻
 * 只能有一个地方表示「你在哪」：分类管理把父分类名放进页面标题栏，选择面板把父分类名放在
 * 面板内的列表首行。
 *
 * 每一层只有一个升级入口：
 *
 * - 可选上下文（[onSelect] 不为空）里，列表首行是返回箭头加当前父分类名。这一行同时也是
 *   这一层的选项——`allowAll` 时是「全部分类」，在记账面板里就是父分类本身——所以点一下就
 *   选中，下方不再重复一条同名行。
 * - 可选上下文中父分类本身是一个选项，整行留给选中，带子分类的行右侧因此有一个独立的
 *   「查看子分类」按钮；管理上下文没有选中动作，整行点击就是进子级，箭头退回成纯提示。
 * - 管理上下文不画这一行：父分类名由页面标题栏承担，浏览器再画一行就是同一个位置说两遍。
 * - 回退手势和返回箭头走同一个回调，系统返回不会绕过当前层级。
 */
@Composable
fun CategoryBrowser(
  categories: List<Category>,
  parentId: String?,
  onParentChange: (String?) -> Unit,
  selectedId: String? = null,
  allowAll: Boolean = false,
  onSelect: ((Category?) -> Unit)? = null,
  actions: (@Composable (Category) -> Unit)? = null,
) {
  val parent = categories.firstOrNull { it.id == parentId }
  val visible = categories.filter { it.parentCategoryId == parent?.id }
  // 可选上下文里这一行同时是「全部分类」/父分类这个选项和返回入口，必须保留；分类管理
  // 没有选中动作，父分类名由页面标题栏承担，这里再画一行就是同一个位置说两遍。
  val showsLevelRow = onSelect != null && (parent != null || allowAll)
  BackHandler(enabled = parent != null) { onParentChange(null) }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (showsLevelRow) {
      CategoryListRow(
        name = parent?.name ?: "全部分类",
        color = parent?.let { argbColor(it.color) } ?: SextantColors.TextMuted,
        detail = parent?.type?.label,
        selected = parent != null && selectedId == parent.id,
        leading = { DepthBackButton(onNavigateUp = { onParentChange(null) }) },
        onClick = if (parent != null) ({ onSelect?.invoke(parent) }) else null,
        onClickLabel = if (parent != null) "选择全部${parent.name}" else null,
      )
    }
    visible.forEach { category ->
      key(category.id) {
        val children = categories.count { it.parentCategoryId == category.id }
        val selectable = onSelect != null
        CategoryListRow(
          name = category.name,
          color = argbColor(category.color),
          detail = listOfNotNull(category.type.label, if (children > 0) "$children 个子分类" else null,
            if (actions != null) { if (category.includeInSpending) "计入支出" else "不计支出" } else null).joinToString(" · "),
          selected = selectable && selectedId == category.id,
          onClick = when {
            selectable -> { { onSelect(category) } }
            // 管理上下文没有选中动作：点整行直接进子级，钻进去以后再管其中的分类。
            children > 0 -> { { onParentChange(category.id) } }
            else -> null
          },
          trailing = {
            if (children > 0) {
              // 管理上下文里整行点击已经进子级，箭头只做提示，不再是一个需要辨认的额外按钮。
              CategoryDrillInButton(
                category = category,
                interactive = selectable,
                onClick = { onParentChange(category.id) },
              )
            }
            actions?.invoke(category)
          },
        )
      }
    }
    if (visible.isEmpty()) Text("暂无可选分类", color = SextantColors.TextMuted)
  }
}

/** 子分类列表首行唯一的返回入口；父分类名字由行标题承担，不再重复一遍。 */
@Composable
private fun DepthBackButton(onNavigateUp: () -> Unit) {
  SextantIconButton(onClick = onNavigateUp) {
    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回全部分类")
  }
}

/**
 * 展开子分类的箭头。
 *
 * 可选上下文里父分类本身也是一个选项，所以整行留给选中，箭头必须是独立的按钮；
 * 管理上下文没有选中动作，整行点击就是进子级，箭头退回成纯提示，不再和「更多」并排成两个按钮。
 */
@Composable
private fun CategoryDrillInButton(category: Category, interactive: Boolean, onClick: () -> Unit) {
  val icon: @Composable () -> Unit = {
    Icon(Icons.Outlined.ChevronRight, contentDescription = "查看${category.name}的子分类",
      tint = SextantColors.TextSubtle, modifier = Modifier.size(20.dp))
  }
  if (interactive) {
    SextantIconButton(onClick = onClick) { icon() }
  } else {
    icon()
  }
}

/**
 * 一行分类。[onClick] 为空表示这一行在这个上下文里不可点（管理最外层的没有子分类的分类），
 * 此时它只承载图标、名称、说明和管理菜单。
 */
@Composable
private fun CategoryListRow(
  name: String,
  color: Color,
  detail: String? = null,
  selected: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  onClickLabel: String? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  SextantSelectableSurface(
    selected = selected,
    onClick = onClick ?: {},
    enabled = onClick != null,
    role = Role.RadioButton,
    onClickLabel = onClickLabel,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      leading?.invoke()
      CategoryIcon(categoryIcon(name), color)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SextantColors.TextSubtle)
      }
      if (selected) Icon(Icons.Outlined.Check, "已选", tint = SextantColors.AccentStrong, modifier = Modifier.size(20.dp))
      trailing?.invoke()
    }
  }
}
