import { View } from "react-native";

import type { BudgetProgressItem } from "@/src/features/budgets/budget.service";
import { formatCompactPercent, formatCurrency } from "@/src/lib/format";
import { Card, ProgressBar, Text, useTheme } from "@/src/ui";

interface BudgetProgressSectionProps {
  budgets: BudgetProgressItem[];
}

function getNestedCardStyle(theme: ReturnType<typeof useTheme>) {
  return {
    borderRadius: 14,
    borderWidth: 1,
    borderColor: theme.colors.border,
    backgroundColor: theme.colors.surfaceAlt,
    padding: 12,
    gap: 8,
  } as const;
}

export function BudgetProgressSection({ budgets }: BudgetProgressSectionProps) {
  const theme = useTheme();
  const nestedCardStyle = getNestedCardStyle(theme);
  const riskBudgetCount = budgets.filter(
    (item) =>
      item.budgetAmount > 0 && item.expenseAmount / item.budgetAmount >= item.alertThreshold,
  ).length;
  const totalBudgetAmount = budgets.reduce((sum, item) => sum + item.budgetAmount, 0);
  const totalBudgetExpense = budgets.reduce((sum, item) => sum + item.expenseAmount, 0);

  return (
    <Card style={{ borderRadius: 18 }}>
      <Card.Content style={{ gap: 12 }}>
        <View
          style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}
        >
          <Text variant="titleMedium">预算进度</Text>
          <Text
            variant="bodySmall"
            style={{ color: riskBudgetCount > 0 ? theme.colors.danger : theme.colors.textMuted }}
          >
            {riskBudgetCount > 0 ? `${riskBudgetCount} 个分类接近超支` : "预算状态稳定"}
          </Text>
        </View>

        <View style={nestedCardStyle}>
          <Text variant="labelLarge">预算池概览</Text>
          <Text variant="titleMedium" tabularNums>
            已使用 {formatCurrency(totalBudgetExpense)} / {formatCurrency(totalBudgetAmount)}
          </Text>
          <ProgressBar
            progress={
              totalBudgetAmount > 0 ? Math.min(totalBudgetExpense / totalBudgetAmount, 1) : 0
            }
            color={riskBudgetCount > 0 ? theme.colors.danger : theme.colors.accent}
          />
        </View>

        {budgets.length === 0 ? <Text>当前还没有预算，先去设置页添加。</Text> : null}
        {budgets.map((item) => {
          const progress = item.budgetAmount > 0 ? item.expenseAmount / item.budgetAmount : 0;
          const over = progress >= item.alertThreshold;

          return (
            <View key={item.categoryId} style={nestedCardStyle}>
              <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                <Text>{item.categoryName}</Text>
                <Text
                  tabularNums
                >{`${formatCurrency(item.expenseAmount)} / ${formatCurrency(item.budgetAmount)}`}</Text>
              </View>
              <ProgressBar
                progress={Math.min(progress, 1)}
                color={over ? theme.colors.danger : theme.colors.accent}
              />
              <Text
                variant="bodySmall"
                style={{ color: over ? theme.colors.danger : theme.colors.textMuted }}
              >
                {over
                  ? `已达到预警阈值 ${formatCompactPercent(item.alertThreshold)}`
                  : `剩余 ${formatCurrency(Math.max(item.budgetAmount - item.expenseAmount, 0))}`}
              </Text>
            </View>
          );
        })}
      </Card.Content>
    </Card>
  );
}
