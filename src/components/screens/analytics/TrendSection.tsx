import { useState } from "react";
import type { LayoutChangeEvent } from "react-native";
import { Button, Card, Text, XStack, YStack, useTheme } from "tamagui";

import { TrendLineChart } from "@/src/components/charts/TrendLineChart";
import type { MonthlyTrendPoint } from "@/src/features/transactions/transaction.service";
import { formatCurrency } from "@/src/lib/format";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface TrendSectionProps {
  trend: MonthlyTrendPoint[];
  loading: boolean;
  error: Error | null;
  onReload: () => void;
  topSpendingCategoryName: string | null;
}

export function TrendSection({
  trend,
  loading,
  error,
  onReload,
  topSpendingCategoryName,
}: TrendSectionProps) {
  const colors = getThemeColors(useTheme());
  const [trendChartWidth, setTrendChartWidth] = useState<number>(0);
  const trendChartPoints = trend.map((item) => ({
    label: item.label,
    value: Math.max(item.income, item.expense, 0),
  }));

  const handleTrendChartLayout = (event: LayoutChangeEvent): void => {
    const nextWidth = Math.floor(event.nativeEvent.layout.width);
    if (nextWidth > 0 && nextWidth !== trendChartWidth) {
      setTrendChartWidth(nextWidth);
    }
  };

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={12}>
        <XStack justifyContent="space-between" alignItems="center" gap={12}>
          <YStack flex={1} minWidth={0} gap={4}>
            <Text style={TEXT_VARIANTS.titleMedium}>近 6 月趋势</Text>
            <Text style={[TEXT_VARIANTS.bodySmall, { color: colors.textMuted }]}>
              看清每个月收支变化，不靠月底的记忆力补账。
            </Text>
          </YStack>
          {topSpendingCategoryName ? (
            <YStack alignItems="flex-end" gap={2} maxWidth="45%" flexShrink={1}>
              <Text style={[TEXT_VARIANTS.labelMedium, { color: colors.textMuted }]}>
                本月最高支出
              </Text>
              <Text
                numberOfLines={1}
                ellipsizeMode="tail"
                style={[TEXT_VARIANTS.titleSmall, { textAlign: "right" }]}
                testID="top-spending-category"
              >
                {topSpendingCategoryName}
              </Text>
            </YStack>
          ) : null}
        </XStack>

        {error ? (
          <YStack gap={10}>
            <Text style={TEXT_VARIANTS.titleMedium}>数据加载失败</Text>
            <Text style={[TEXT_VARIANTS.bodyMedium, { color: colors.textMuted }]}>
              {error.message || "请稍后重试"}
            </Text>
            <Text style={[TEXT_VARIANTS.bodySmall, { color: colors.textMuted }]}>
              当前展示已有数据（如有）。
            </Text>
            <Button
              unstyled
              alignSelf="flex-start"
              minHeight={44}
              borderRadius={12}
              borderWidth={1.25}
              borderColor={colors.borderStrong}
              backgroundColor={colors.surface}
              paddingHorizontal={16}
              paddingVertical={10}
              onPress={onReload}
            >
              重新加载
            </Button>
          </YStack>
        ) : null}

        <YStack
          onLayout={handleTrendChartLayout}
          width="100%"
          testID="trend-chart-container"
        >
          {loading && !error ? <Text style={TEXT_VARIANTS.bodyMedium}>正在生成趋势图...</Text> : null}
          {!loading && !error && trend.length > 0 && trendChartWidth > 0 ? (
            <TrendLineChart
              points={trendChartPoints}
              width={trendChartWidth}
              color={colors.accent}
              fillColor={colors.accentSoft}
              axisColor={colors.borderStrong}
              labelColor={colors.textMuted}
            />
          ) : null}
          {!loading && !error && trend.length === 0 ? (
            <Text style={TEXT_VARIANTS.bodyMedium}>暂无趋势数据</Text>
          ) : null}
        </YStack>

        <XStack justifyContent="space-between" gap={12} flexWrap="wrap">
          {trend.slice(-3).map((item) => (
            <YStack key={item.monthKey} minWidth={88} gap={4}>
              <Text style={[TEXT_VARIANTS.labelMedium, { color: colors.textMuted }]}>
                {item.label}
              </Text>
              <Text style={[TEXT_VARIANTS.bodyMedium, { fontVariant: ["tabular-nums"] }]}>
                收入 {formatCurrency(item.income)}
              </Text>
              <Text style={[TEXT_VARIANTS.bodyMedium, { fontVariant: ["tabular-nums"] }]}>
                支出 {formatCurrency(item.expense)}
              </Text>
            </YStack>
          ))}
        </XStack>
      </YStack>
    </Card>
  );
}
