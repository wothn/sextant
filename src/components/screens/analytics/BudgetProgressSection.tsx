import { Card, Progress, Text, XStack, YStack, useTheme } from "tamagui";

import type { BudgetProgressItem } from "@/src/features/budgets/budget.service";
import { formatCompactPercent, formatCurrency } from "@/src/lib/format";
import { getThemeColors, type ThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface BudgetProgressSectionProps {
  budgets: BudgetProgressItem[];
}

function getNestedCardStyle(colors: ThemeColors) {
  return {
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceAlt,
    padding: 12,
    gap: 8,
  } as const;
}

export function BudgetProgressSection({ budgets }: BudgetProgressSectionProps) {
  const colors = getThemeColors(useTheme());
  const nestedCardStyle = getNestedCardStyle(colors);
  const riskBudgetCount = budgets.filter(
    (item) =>
      item.budgetAmount > 0 && item.expenseAmount / item.budgetAmount >= item.alertThreshold,
  ).length;
  const totalBudgetAmount = budgets.reduce((sum, item) => sum + item.budgetAmount, 0);
  const totalBudgetExpense = budgets.reduce((sum, item) => sum + item.expenseAmount, 0);

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={12}>
        <XStack justifyContent="space-between" alignItems="center">
          <Text style={TEXT_VARIANTS.titleMedium}>预算进度</Text>
          <Text
            style={[
              TEXT_VARIANTS.bodySmall,
              { color: riskBudgetCount > 0 ? colors.danger : colors.textMuted },
            ]}
          >
            {riskBudgetCount > 0 ? `${riskBudgetCount} 个分类接近超支` : "预算状态稳定"}
          </Text>
        </XStack>

        <YStack style={nestedCardStyle}>
          <Text style={TEXT_VARIANTS.labelLarge}>预算池概览</Text>
          <Text style={[TEXT_VARIANTS.titleMedium, { fontVariant: ["tabular-nums"] }]}>
            已使用 {formatCurrency(totalBudgetExpense)} / {formatCurrency(totalBudgetAmount)}
          </Text>
          <Progress
            value={
              100 * (totalBudgetAmount > 0 ? Math.min(totalBudgetExpense / totalBudgetAmount, 1) : 0)
            }
            max={100}
            height={6}
            borderRadius={999}
            backgroundColor={colors.surfaceStrong}
          >
            <Progress.Indicator backgroundColor={riskBudgetCount > 0 ? colors.danger : colors.accent} />
          </Progress>
        </YStack>

        {budgets.length === 0 ? <Text style={TEXT_VARIANTS.bodyMedium}>当前还没有预算，先去设置页添加。</Text> : null}
        {budgets.map((item) => {
          const progress = item.budgetAmount > 0 ? item.expenseAmount / item.budgetAmount : 0;
          const over = progress >= item.alertThreshold;

          return (
            <YStack key={item.categoryId} style={nestedCardStyle}>
              <XStack justifyContent="space-between">
                <Text style={TEXT_VARIANTS.bodyMedium}>{item.categoryName}</Text>
                <Text
                  style={[TEXT_VARIANTS.bodyMedium, { fontVariant: ["tabular-nums"] }]}
                >{`${formatCurrency(item.expenseAmount)} / ${formatCurrency(item.budgetAmount)}`}</Text>
              </XStack>
              <Progress
                value={100 * Math.min(progress, 1)}
                max={100}
                height={6}
                borderRadius={999}
                backgroundColor={colors.surfaceStrong}
              >
                <Progress.Indicator backgroundColor={over ? colors.danger : colors.accent} />
              </Progress>
              <Text
                style={[
                  TEXT_VARIANTS.bodySmall,
                  { color: over ? colors.danger : colors.textMuted },
                ]}
              >
                {over
                  ? `已达到预警阈值 ${formatCompactPercent(item.alertThreshold)}`
                  : `剩余 ${formatCurrency(Math.max(item.budgetAmount - item.expenseAmount, 0))}`}
              </Text>
            </YStack>
          );
        })}
      </YStack>
    </Card>
  );
}
