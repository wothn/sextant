package com.sextant.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sextant.app.data.Category
import com.sextant.app.data.PaymentMethod
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.signedAmount

@Composable
fun SummaryCard(title: String, income: Long, expense: Long) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("收入", income, SextantColors.Success)
        Metric("支出", expense, SextantColors.Danger)
        Metric("结余", income - expense, SextantColors.Accent)
      }
    }
  }
}

@Composable
private fun Metric(label: String, value: Long, color: Color) {
  Column {
    Text(label, color = SextantColors.TextMuted, style = MaterialTheme.typography.labelMedium)
    Text(formatCurrency(value), color = color, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun TransactionRow(item: TransactionListItem) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(argbColor(item.categoryColor))
        Spacer(Modifier.width(12.dp))
        Column {
          Text(item.categoryName, fontWeight = FontWeight.SemiBold)
          Text(
            item.paymentMethodName,
            color = SextantColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      Text(
        text = signedAmount(item),
        color = if (item.transaction.type == TransactionType.Expense) SextantColors.Danger else SextantColors.Success,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun ProgressRow(label: String, value: Long, total: Long, color: Color) {
  val percent = if (total <= 0) 0f else (value.toDouble() / total).toFloat().coerceIn(0f, 1f)
  Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text("${formatCurrency(value)} / ${formatCurrency(total)}", color = SextantColors.TextMuted)
      }
      Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(SextantColors.SurfaceStrong)) {
        Box(Modifier.fillMaxWidth(percent).height(8.dp).clip(CircleShape).background(color))
      }
  }
}

@Composable
fun EmptyCard(title: String, description: String) {
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.SurfaceAlt)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(description, color = SextantColors.TextMuted)
    }
  }
}

@Composable
fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
fun ColorDot(color: Color) {
  Canvas(Modifier.size(8.dp)) {
    drawCircle(color)
  }
}

@Composable
fun <T> FlowChips(
  items: List<T>,
  selectedId: String?,
  label: (T) -> String,
  color: (T) -> Color,
  modifier: Modifier = Modifier,
  onSelect: (T) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    items.forEach { item ->
      val id = when (item) {
        is Category -> item.id
        is PaymentMethod -> item.id
        is Pair<*, *> -> item.first.toString()
        null -> null
        else -> ""
      }
      val isSelected = id == selectedId
      val itemColor = color(item)
      FilterChip(
        selected = isSelected,
        onClick = { onSelect(item) },
        label = { Text(label(item)) },
        leadingIcon = { ColorDot(itemColor) },
        colors = sextantFilterChipColors(),
      )
    }
  }
}

@Composable
fun sextantFilterChipColors() = FilterChipDefaults.filterChipColors(
  selectedContainerColor = SextantColors.AccentSoft,
  selectedLabelColor = SextantColors.AccentStrong,
  selectedLeadingIconColor = SextantColors.AccentStrong,
  containerColor = SextantColors.Surface,
  labelColor = SextantColors.Text,
)

@Composable
fun sextantSwitchColors() = SwitchDefaults.colors(
  checkedThumbColor = SextantColors.OnAccent,
  checkedTrackColor = SextantColors.Accent,
  checkedBorderColor = SextantColors.Accent,
  uncheckedThumbColor = SextantColors.Surface,
  uncheckedTrackColor = SextantColors.SurfaceStrong,
  uncheckedBorderColor = SextantColors.Border,
  disabledCheckedThumbColor = SextantColors.OnAccent,
  disabledCheckedTrackColor = SextantColors.Accent,
  disabledCheckedBorderColor = SextantColors.Accent,
  disabledUncheckedThumbColor = SextantColors.Surface,
  disabledUncheckedTrackColor = SextantColors.SurfaceStrong,
  disabledUncheckedBorderColor = SextantColors.Border,
)

