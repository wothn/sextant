import { useMemo, useState } from "react";
import { type LayoutChangeEvent, View } from "react-native";

import {
  listCurrentMonthBudgetProgress,
  type BudgetProgressItem,
} from "@/src/features/budgets/budget.service";
import { DonutChart } from "@/src/components/charts/DonutChart";
import { TrendLineChart } from "@/src/components/charts/TrendLineChart";
import {
  getCurrentMonthAccountSummaries,
  getCurrentMonthCategoryBreakdown,
  getCurrentMonthSummary,
  getMonthlyTrend,
  type AccountMonthlySummary,
  type CategoryBreakdownItem,
  type MonthlyTrendPoint,
} from "@/src/features/transactions/transaction.service";
import { useAsyncData } from "@/src/hooks/use-async-data";
import { formatCompactPercent, formatCurrency, formatSignedCurrency } from "@/src/lib/format";
import { useUIStore } from "@/src/store/ui.store";
import { getAccountTypeLabel } from "@/src/types/domain";
import { Button, Card, ProgressBar, Screen, Text, useTheme } from "@/src/ui";

interface AnalyticsScreenData {
  summary: {
    income: number;
    expense: number;
    net: number;
  };
  trend: MonthlyTrendPoint[];
  categories: CategoryBreakdownItem[];
  accounts: AccountMonthlySummary[];
  budgets: BudgetProgressItem[];
}

const INITIAL_ANALYTICS_SCREEN_DATA: AnalyticsScreenData = {
  summary: {
    income: 0,
    expense: 0,
    net: 0,
  },
  trend: [],
  categories: [],
  accounts: [],
  budgets: [],
};

async function loadAnalyticsScreenData(): Promise<AnalyticsScreenData> {
  const [summary, budgets, trend, categories, accounts] = await Promise.all([
    getCurrentMonthSummary(),
    listCurrentMonthBudgetProgress(),
    getMonthlyTrend(6),
    getCurrentMonthCategoryBreakdown(),
    getCurrentMonthAccountSummaries(),
  ]);

  return {
    summary,
    budgets,
    trend,
    categories,
    accounts,
  };
}

export default function AnalyticsScreen() {
  const theme = useTheme();
  const refreshKey = useUIStore((state) => state.refreshKey);
  const [trendChartWidth, setTrendChartWidth] = useState<number>(0);
  const { data, loading, error, reload } = useAsyncData<AnalyticsScreenData>({
    initialData: INITIAL_ANALYTICS_SCREEN_DATA,
    fetcher: loadAnalyticsScreenData,
    deps: [refreshKey],
  });
  const { accounts, budgets, categories, summary, trend } = data;

  const riskBudgetCount = useMemo(
    () =>
      budgets.filter(
        (item) =>
          item.budgetAmount > 0 && item.expenseAmount / item.budgetAmount >= item.alertThreshold,
      ).length,
    [budgets],
  );
  const totalBudgetAmount = useMemo(
    () => budgets.reduce((sum, item) => sum + item.budgetAmount, 0),
    [budgets],
  );
  const totalBudgetExpense = useMemo(
    () => budgets.reduce((sum, item) => sum + item.expenseAmount, 0),
    [budgets],
  );
  const trendChartPoints = useMemo(
    () =>
      trend.map((item) => ({ label: item.label, value: Math.max(item.income, item.expense, 0) })),
    [trend],
  );
  const donutSegments = useMemo(
    () => categories.slice(0, 5).map((item) => ({ value: item.amount, color: item.categoryColor })),
    [categories],
  );
  const topSpendingCategory = categories[0] ?? null;
  const handleTrendChartLayout = (event: LayoutChangeEvent): void => {
    const nextWidth = Math.floor(event.nativeEvent.layout.width);
    if (nextWidth > 0 && nextWidth !== trendChartWidth) {
      setTrendChartWidth(nextWidth);
    }
  };

  const nestedCardStyle = {
    borderRadius: 14,
    borderWidth: 1,
    borderColor: theme.colors.border,
    backgroundColor: theme.colors.surfaceAlt,
    padding: 12,
    gap: 8,
  } as const;

  return (
    <Screen contentContainerStyle={{ paddingBottom: 132 }}>
      <View style={{ flexDirection: "row", gap: 12, flexWrap: "wrap" }}>
        {[
          { label: "本月收入", value: formatCurrency(summary.income), tone: theme.colors.success },
          { label: "本月支出", value: formatCurrency(summary.expense), tone: theme.colors.danger },
          {
            label: "本月净额",
            value: formatSignedCurrency(summary.net),
            tone: summary.net >= 0 ? theme.colors.success : theme.colors.danger,
          },
        ].map((item) => (
          <Card key={item.label} style={{ flexBasis: "31%", flexGrow: 1, borderRadius: 16 }}>
            <Card.Content style={{ gap: 8 }}>
              <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
                {item.label}
              </Text>
              <Text
                variant="titleLarge"
                tabularNums
                style={{ color: item.tone, fontWeight: "700" }}
              >
                {item.value}
              </Text>
            </Card.Content>
          </Card>
        ))}
      </View>

      {error ? (
        <Card style={{ borderRadius: 18 }}>
          <Card.Content style={{ gap: 10 }}>
            <Text variant="titleMedium">数据加载失败</Text>
            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
              {error.message || "请稍后重试"}
            </Text>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              当前展示已有数据（如有）。
            </Text>
            <Button mode="outlined" onPress={reload}>
              重新加载
            </Button>
          </Card.Content>
        </Card>
      ) : null}

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
            {topSpendingCategory ? (
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
                  {topSpendingCategory.categoryName}
                </Text>
              </View>
            ) : null}
          </View>

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

      <Card style={{ borderRadius: 18 }}>
        <Card.Content style={{ gap: 14 }}>
          <Text variant="titleMedium">支出结构</Text>
          {!error && categories.length === 0 ? (
            <Text>本月还没有支出记录，结构图会在你花第一笔钱后出现。</Text>
          ) : (
            <View style={{ flexDirection: "row", gap: 16, alignItems: "center" }}>
              <DonutChart segments={donutSegments} />
              <View style={{ flex: 1, gap: 10 }}>
                {categories.slice(0, 5).map((item) => (
                  <View key={item.categoryName} style={{ gap: 4 }}>
                    <View
                      style={{
                        flexDirection: "row",
                        justifyContent: "space-between",
                        alignItems: "center",
                      }}
                    >
                      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, flex: 1 }}>
                        <View
                          style={{
                            width: 10,
                            height: 10,
                            borderRadius: 5,
                            backgroundColor: item.categoryColor,
                          }}
                        />
                        <Text variant="bodyMedium">{item.categoryName}</Text>
                      </View>
                      <Text variant="labelLarge" tabularNums>
                        {formatCompactPercent(item.share)}
                      </Text>
                    </View>
                    <Text variant="bodySmall" style={{ color: theme.colors.textMuted }} tabularNums>
                      {formatCurrency(item.amount)} · {item.transactionCount} 笔
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          )}
        </Card.Content>
      </Card>

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

          {!error && budgets.length === 0 ? <Text>当前还没有预算，先去设置页添加。</Text> : null}
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

      <Card style={{ borderRadius: 18 }}>
        <Card.Content style={{ gap: 12 }}>
          <Text variant="titleMedium">账户视图</Text>
          {!error && accounts.length === 0 ? <Text>还没有可展示的账户数据。</Text> : null}
          {accounts.map((account) => (
            <View key={account.accountId} style={nestedCardStyle}>
              <View
                style={{
                  flexDirection: "row",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <View>
                  <Text variant="titleSmall">{account.accountName}</Text>
                  <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
                    {getAccountTypeLabel(account.accountType)} · {account.transactionCount} 笔
                  </Text>
                </View>
                <Text variant="titleSmall" tabularNums>
                  余额 {formatCurrency(account.balance)}
                </Text>
              </View>
              <View style={{ flexDirection: "row", justifyContent: "space-between", gap: 12 }}>
                <Text variant="bodyMedium" style={{ color: theme.colors.success }} tabularNums>
                  收入 {formatCurrency(account.income)}
                </Text>
                <Text variant="bodyMedium" style={{ color: theme.colors.danger }} tabularNums>
                  支出 {formatCurrency(account.expense)}
                </Text>
                <Text variant="bodyMedium" style={{ color: theme.colors.accent }} tabularNums>
                  净额 {formatSignedCurrency(account.net)}
                </Text>
              </View>
            </View>
          ))}
        </Card.Content>
      </Card>
    </Screen>
  );
}
