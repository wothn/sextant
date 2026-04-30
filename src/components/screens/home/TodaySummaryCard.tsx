import { Card, Text, XStack, YStack, useTheme } from "tamagui";

import { formatCurrency, formatSignedCurrency } from "@/src/lib/format";
import type { TodaySummary } from "@/src/features/transactions/transaction.service";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface MonthSummary {
  income: number;
  expense: number;
  net: number;
}

interface TodaySummaryCardProps {
  todaySummary: TodaySummary;
  monthSummary: MonthSummary;
}

export function TodaySummaryCard({ todaySummary, monthSummary }: TodaySummaryCardProps) {
  const colors = getThemeColors(useTheme());

  return (
    <Card borderRadius={22} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={12}>
        <YStack gap={6}>
          <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.textMuted }]}>
            今天已经花了
          </Text>
          <XStack alignItems="center" justifyContent="space-between" gap={12}>
            <Text
              style={[
                TEXT_VARIANTS.headlineSmall,
                { color: colors.danger, fontWeight: "800", fontVariant: ["tabular-nums"] },
              ]}
            >
              {formatCurrency(todaySummary.expense)}
            </Text>
            <YStack alignItems="flex-end" gap={2}>
              <Text style={[TEXT_VARIANTS.labelSmall, { color: colors.textMuted }]}>
                净变化
              </Text>
              <Text
                style={[
                  TEXT_VARIANTS.bodySmall,
                  { color: colors.textMuted, fontVariant: ["tabular-nums"] },
                ]}
              >
                {formatSignedCurrency(todaySummary.net)}
              </Text>
            </YStack>
          </XStack>
        </YStack>

        <XStack alignItems="center" gap={12}>
          <YStack flex={1} gap={4}>
            <Text style={[TEXT_VARIANTS.labelSmall, { color: colors.textMuted }]}>
              本月支出
            </Text>
            <Text
              style={[
                TEXT_VARIANTS.titleMedium,
                { color: colors.danger, fontVariant: ["tabular-nums"] },
              ]}
            >
              {formatCurrency(monthSummary.expense)}
            </Text>
          </YStack>
          <YStack flex={1} gap={4} alignItems="flex-end">
            <Text style={[TEXT_VARIANTS.labelSmall, { color: colors.textMuted }]}>
              本月收入
            </Text>
            <Text
              style={[
                TEXT_VARIANTS.titleMedium,
                { color: colors.success, fontVariant: ["tabular-nums"] },
              ]}
            >
              {formatCurrency(monthSummary.income)}
            </Text>
          </YStack>
        </XStack>
      </YStack>
    </Card>
  );
}
