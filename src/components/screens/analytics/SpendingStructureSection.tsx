import { Card, Text, XStack, YStack, useTheme } from "tamagui";

import { DonutChart } from "@/src/components/charts/DonutChart";
import type { CategoryBreakdownItem } from "@/src/features/transactions/transaction.service";
import { formatCompactPercent, formatCurrency } from "@/src/lib/format";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface SpendingStructureSectionProps {
  categories: CategoryBreakdownItem[];
  error: Error | null;
}

export function SpendingStructureSection({ categories, error }: SpendingStructureSectionProps) {
  const colors = getThemeColors(useTheme());
  const donutSegments = categories
    .slice(0, 5)
    .map((item) => ({ value: item.amount, color: item.categoryColor }));

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={14}>
        <Text style={TEXT_VARIANTS.titleMedium}>支出结构</Text>
        {!error && categories.length === 0 ? (
          <Text style={TEXT_VARIANTS.bodyMedium}>本月还没有支出记录，结构图会在你花第一笔钱后出现。</Text>
        ) : (
          <XStack gap={16} alignItems="center">
            <DonutChart segments={donutSegments} trackColor={colors.border} />
            <YStack flex={1} gap={10}>
              {categories.slice(0, 5).map((item) => (
                <YStack key={item.categoryName} gap={4}>
                  <XStack justifyContent="space-between" alignItems="center">
                    <XStack alignItems="center" gap={8} flex={1}>
                      <YStack
                        width={10}
                        height={10}
                        borderRadius={5}
                        backgroundColor={item.categoryColor}
                      />
                      <Text style={TEXT_VARIANTS.bodyMedium}>{item.categoryName}</Text>
                    </XStack>
                    <Text style={[TEXT_VARIANTS.labelLarge, { fontVariant: ["tabular-nums"] }]}>
                      {formatCompactPercent(item.share)}
                    </Text>
                  </XStack>
                  <Text
                    style={[
                      TEXT_VARIANTS.bodySmall,
                      { color: colors.textMuted, fontVariant: ["tabular-nums"] },
                    ]}
                  >
                    {formatCurrency(item.amount)} · {item.transactionCount} 笔
                  </Text>
                </YStack>
              ))}
            </YStack>
          </XStack>
        )}
      </YStack>
    </Card>
  );
}
