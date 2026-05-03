import { Card, Progress, Text, XStack, YStack, useTheme } from "tamagui";

import type { BudgetProgressItem } from "@/src/features/budgets/budget.service";
import { formatCompactPercent, formatCurrency } from "@/src/lib/format";
import { getThemeColors, type ThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface BudgetProgressCardProps {
  budgets: BudgetProgressItem[];
}

function getProgressTone(colors: ThemeColors, progress: number, alertThreshold: number): string {
  if (progress >= 1) {
    return colors.danger;
  }

  if (progress >= alertThreshold) {
    return colors.warning;
  }

  return colors.accent;
}

export function BudgetProgressCard({ budgets }: BudgetProgressCardProps) {
  const colors = getThemeColors(useTheme());
  const totalBudgetAmount = budgets.reduce((sum, item) => sum + item.budgetAmount, 0);
  const totalBudgetExpense = budgets.reduce((sum, item) => sum + item.expenseAmount, 0);
  const totalProgress = totalBudgetAmount > 0 ? totalBudgetExpense / totalBudgetAmount : 0;
  const riskBudgetCount = budgets.filter(
    (item) =>
      item.budgetAmount > 0 && item.expenseAmount / item.budgetAmount >= item.alertThreshold,
  ).length;
  const overviewTone = getProgressTone(colors, totalProgress, 0.8);

  return (
    <Card
      borderRadius={18}
      borderWidth={1}
      borderColor={colors.border}
      backgroundColor={colors.surface}
      padding={16}
    >
      <YStack gap={12}>
        <XStack justifyContent="space-between" alignItems="center" gap={12}>
          <Text style={TEXT_VARIANTS.titleMedium}>预算进度</Text>
          <Text
            style={[
              TEXT_VARIANTS.bodySmall,
              { color: riskBudgetCount > 0 ? colors.warning : colors.textMuted },
            ]}
          >
            {riskBudgetCount > 0 ? `${riskBudgetCount} 个分类需留意` : "状态稳定"}
          </Text>
        </XStack>

        <YStack
          borderRadius={14}
          borderWidth={1}
          borderColor={colors.border}
          backgroundColor={colors.surfaceAlt}
          padding={12}
          gap={8}
        >
          <Text style={TEXT_VARIANTS.labelLarge}>总览</Text>
          <Text style={[TEXT_VARIANTS.titleMedium, { fontVariant: ["tabular-nums"] }]}>
            {formatCurrency(totalBudgetExpense)} / {formatCurrency(totalBudgetAmount)}
          </Text>
          <Progress
            value={100 * Math.min(totalProgress, 1)}
            max={100}
            height={7}
            borderRadius={999}
            backgroundColor={colors.surfaceStrong}
          >
            <Progress.Indicator backgroundColor={overviewTone} />
          </Progress>
        </YStack>

        {budgets.length === 0 ? (
          <Text style={[TEXT_VARIANTS.bodyMedium, { color: colors.textMuted }]}>
            还没有本月预算，先选择一个支出分类添加。
          </Text>
        ) : null}

        {budgets.map((item) => {
          const progress = item.budgetAmount > 0 ? item.expenseAmount / item.budgetAmount : 0;
          const tone = getProgressTone(colors, progress, item.alertThreshold);
          const remaining = Math.max(item.budgetAmount - item.expenseAmount, 0);

          return (
            <YStack
              key={item.categoryId}
              borderRadius={14}
              borderWidth={1}
              borderColor={colors.border}
              backgroundColor={colors.surfaceAlt}
              padding={12}
              gap={8}
            >
              <XStack justifyContent="space-between" gap={12}>
                <Text style={TEXT_VARIANTS.bodyMedium}>{item.categoryName}</Text>
                <Text style={[TEXT_VARIANTS.bodyMedium, { fontVariant: ["tabular-nums"] }]}>
                  {formatCompactPercent(progress)}
                </Text>
              </XStack>
              <Progress
                value={100 * Math.min(progress, 1)}
                max={100}
                height={6}
                borderRadius={999}
                backgroundColor={colors.surfaceStrong}
              >
                <Progress.Indicator backgroundColor={tone} />
              </Progress>
              <XStack justifyContent="space-between" gap={12}>
                <Text style={[TEXT_VARIANTS.bodySmall, { color: colors.textMuted }]}>
                  已用 {formatCurrency(item.expenseAmount)}
                </Text>
                <Text style={[TEXT_VARIANTS.bodySmall, { color: tone }]}>
                  {progress >= 1
                    ? `超出 ${formatCurrency(item.expenseAmount - item.budgetAmount)}`
                    : `剩余 ${formatCurrency(remaining)}`}
                </Text>
              </XStack>
            </YStack>
          );
        })}
      </YStack>
    </Card>
  );
}
