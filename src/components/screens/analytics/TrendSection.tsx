import { useState } from "react";
import { type LayoutChangeEvent, View } from "react-native";

import { TrendLineChart } from "@/src/components/charts/TrendLineChart";
import type { MonthlyTrendPoint } from "@/src/features/transactions/transaction.service";
import { formatCurrency } from "@/src/lib/format";
import { Button, Card, Text, useTheme } from "@/src/ui";

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
  const theme = useTheme();
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
    <Card style={{ borderRadius: 18 }}>
      <Card.Content style={{ gap: 12 }}>
        <View
          style={{
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            gap: 12,
          }}
        >
          <View style={{ flex: 1, minWidth: 0, gap: 4 }}>
            <Text variant="titleMedium">近 6 月趋势</Text>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              看清每个月收支变化，不靠月底的记忆力补账。
            </Text>
          </View>
          {topSpendingCategoryName ? (
            <View style={{ alignItems: "flex-end", gap: 2, maxWidth: "45%", flexShrink: 1 }}>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                本月最高支出
              </Text>
              <Text
                variant="titleSmall"
                numberOfLines={1}
                ellipsizeMode="tail"
                style={{ textAlign: "right" }}
                testID="top-spending-category"
              >
                {topSpendingCategoryName}
              </Text>
            </View>
          ) : null}
        </View>

        {error ? (
          <View style={{ gap: 10 }}>
            <Text variant="titleMedium">数据加载失败</Text>
            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
              {error.message || "请稍后重试"}
            </Text>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              当前展示已有数据（如有）。
            </Text>
            <Button mode="outlined" onPress={onReload}>
              重新加载
            </Button>
          </View>
        ) : null}

        <View
          onLayout={handleTrendChartLayout}
          style={{ width: "100%" }}
          testID="trend-chart-container"
        >
          {loading && !error ? <Text>正在生成趋势图…</Text> : null}
          {!loading && !error && trend.length > 0 && trendChartWidth > 0 ? (
            <TrendLineChart
              points={trendChartPoints}
              width={trendChartWidth}
              color={theme.colors.accent}
              fillColor={theme.colors.accentSoft}
              axisColor={theme.colors.borderStrong}
              labelColor={theme.colors.textMuted}
            />
          ) : null}
          {!loading && !error && trend.length === 0 ? <Text>暂无趋势数据</Text> : null}
        </View>

        <View
          style={{
            flexDirection: "row",
            justifyContent: "space-between",
            gap: 12,
            flexWrap: "wrap",
          }}
        >
          {trend.slice(-3).map((item) => (
            <View key={item.monthKey} style={{ minWidth: 88, gap: 4 }}>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                {item.label}
              </Text>
              <Text variant="bodyMedium" tabularNums>
                收入 {formatCurrency(item.income)}
              </Text>
              <Text variant="bodyMedium" tabularNums>
                支出 {formatCurrency(item.expense)}
              </Text>
            </View>
          ))}
        </View>
      </Card.Content>
    </Card>
  );
}
