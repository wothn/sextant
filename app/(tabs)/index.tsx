import { useMemo, useState } from "react";
import { Pressable, View } from "react-native";

import { formatCurrency, formatDateGroupTitle, formatSignedCurrency } from "@/src/lib/format";
import {
  getCurrentMonthSummary,
  getTodaySummary,
  listTransactionGroupsByDay,
  type DailyTransactionGroup,
  type TransactionListItem,
  type TodaySummary,
} from "@/src/features/transactions/transaction.service";
import { useAsyncData } from "@/src/hooks/use-async-data";
import { useUIStore } from "@/src/store/ui.store";
import { Button, Card, Divider, Modal, Portal, Screen, Text, useTheme } from "@/src/ui";

interface HomeScreenData {
  todaySummary: TodaySummary;
  monthSummary: {
    income: number;
    expense: number;
    net: number;
  };
  groups: DailyTransactionGroup[];
}

const INITIAL_HOME_SCREEN_DATA: HomeScreenData = {
  todaySummary: {
    income: 0,
    expense: 0,
    net: 0,
    topCategories: [],
  },
  monthSummary: {
    income: 0,
    expense: 0,
    net: 0,
  },
  groups: [],
};

async function loadHomeScreenData(): Promise<HomeScreenData> {
  const [todaySummary, monthSummary, groups] = await Promise.all([
    getTodaySummary(),
    getCurrentMonthSummary(),
    listTransactionGroupsByDay(30, 150),
  ]);

  return {
    todaySummary,
    monthSummary,
    groups,
  };
}

function getGreeting(): { greeting: string; subtext: string } {
  const hour = new Date().getHours();

  if (hour >= 0 && hour < 5) {
    return { greeting: "深夜了", subtext: "星辰相伴，早些休息" };
  } else if (hour >= 5 && hour < 9) {
    return { greeting: "早安", subtext: "晨光微熹，新的一天开始了" };
  } else if (hour >= 9 && hour < 11) {
    return { greeting: "上午好", subtext: "愿时光温柔以待" };
  } else if (hour >= 11 && hour < 14) {
    return { greeting: "午安", subtext: "今日正好" };
  } else if (hour >= 14 && hour < 17) {
    return { greeting: "下午好", subtext: "岁月静好" };
  } else if (hour >= 17 && hour < 19) {
    return { greeting: "傍晚了", subtext: "落日余晖正温柔" };
  } else {
    return { greeting: "晚安", subtext: "今夜好梦" };
  }
}

function formatDate(): string {
  const now = new Date();
  const weekdays = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
  const month = now.getMonth() + 1;
  const date = now.getDate();
  const weekday = weekdays[now.getDay()];
  return `${month}月${date}日 ${weekday}`;
}

export default function HomeScreen() {
  const theme = useTheme();
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionListItem | null>(null);
  const refreshKey = useUIStore((state) => state.refreshKey);
  const { data, loading, error, reload } = useAsyncData<HomeScreenData>({
    initialData: INITIAL_HOME_SCREEN_DATA,
    fetcher: loadHomeScreenData,
    deps: [refreshKey],
  });
  const { groups, monthSummary, todaySummary } = data;

  const selectedTransactionSummary = useMemo(() => {
    if (!selectedTransaction) {
      return null;
    }

    const categoryName =
      selectedTransaction.categoryName ??
      (selectedTransaction.type === "transfer" ? "转账" : "未分类");

    return {
      categoryName,
      amount:
        selectedTransaction.type === "expense"
          ? formatSignedCurrency(-selectedTransaction.amount)
          : formatSignedCurrency(selectedTransaction.amount),
      time: new Date(selectedTransaction.transactionDate).toLocaleString("zh-CN", {
        month: "numeric",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }),
      typeLabel:
        selectedTransaction.type === "expense"
          ? "支出"
          : selectedTransaction.type === "income"
            ? "收入"
            : "转账",
      description: selectedTransaction.description.trim() || "未填写",
    };
  }, [selectedTransaction]);

  const { greeting, subtext } = getGreeting();

  return (
    <>
      <Screen contentContainerStyle={{ paddingBottom: 132 }}>
        <View style={{ gap: 4 }}>
          <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
            Sextant Ledger
          </Text>
          <Text variant="headlineMedium" style={{ fontWeight: "700", letterSpacing: -0.4 }}>
            {greeting}
          </Text>
          <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
            {subtext} · {formatDate()}
          </Text>
        </View>

        <Card
          style={{
            borderRadius: 22,
          }}
        >
          <Card.Content style={{ gap: 18 }}>
            <View
              style={{
                borderRadius: 16,
                padding: 16,
                backgroundColor: theme.colors.surface,
                borderWidth: 1,
                borderColor: theme.colors.border,
                gap: 8,
              }}
            >
              <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
                今天已经花了
              </Text>
              <Text
                variant="displaySmall"
                style={{ color: theme.colors.danger, fontWeight: "800" }}
              >
                {formatCurrency(todaySummary.expense)}
              </Text>
              <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
                净变化 {formatSignedCurrency(todaySummary.net)}
              </Text>
            </View>

            <View style={{ flexDirection: "row", gap: 10, flexWrap: "wrap" }}>
              {[
                {
                  label: "本月支出",
                  value: formatCurrency(monthSummary.expense),
                  tone: theme.colors.danger,
                },
                {
                  label: "本月收入",
                  value: formatSignedCurrency(monthSummary.net),
                  tone: monthSummary.net >= 0 ? theme.colors.success : theme.colors.danger,
                },
              ].map((item) => (
                <View
                  key={item.label}
                  style={{
                    flex: 1,
                    minWidth: 132,
                    borderRadius: 16,
                    borderWidth: 1,
                    borderColor: theme.colors.border,
                    paddingHorizontal: 14,
                    paddingVertical: 12,
                    backgroundColor: theme.colors.surface,
                    gap: 4,
                  }}
                >
                  <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
                    {item.label}
                  </Text>
                  <Text variant="titleLarge" style={{ color: item.tone, fontWeight: "700" }}>
                    {item.value}
                  </Text>
                </View>
              ))}
            </View>
          </Card.Content>
        </Card>

        {error ? (
          <Card style={{ borderRadius: 18 }}>
            <Card.Content style={{ gap: 10 }}>
              <Text variant="titleMedium">数据加载失败</Text>
              <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
                {error.message || "请稍后重试"}
              </Text>
              <Button mode="outlined" onPress={reload}>
                重试
              </Button>
            </Card.Content>
          </Card>
        ) : null}

        {loading && !error ? <Text>正在整理你的账本…</Text> : null}
        {!loading && !error && groups.length === 0 ? (
          <Card style={{ borderRadius: 18 }}>
            <Card.Content style={{ gap: 12 }}>
              <Text variant="titleMedium">还没有记录</Text>
              <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
                记一笔后，这里会按日期排好最近的明细。
              </Text>
            </Card.Content>
          </Card>
        ) : null}

        {groups.map((group) => (
          <Card key={group.dateKey} style={{ borderRadius: 18 }}>
            <Card.Content style={{ gap: 8 }}>
              <View
                style={{
                  flexDirection: "row",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                }}
              >
                <View style={{ gap: 4, flex: 1 }}>
                  <Text variant="titleSmall">{formatDateGroupTitle(group.dateKey)}</Text>
                  <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                    共 {group.transactionCount} 笔
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    gap: 6,
                    flexWrap: "wrap",
                    justifyContent: "flex-end",
                  }}
                >
                    <Text variant="labelMedium" style={{ color: theme.colors.danger }}>
                    支 {formatCurrency(group.totalExpense)}
                  </Text>
                    <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                    ·
                  </Text>
                    <Text variant="labelMedium" style={{ color: theme.colors.success }}>
                    收 {formatCurrency(group.totalIncome)}
                  </Text>
                </View>
              </View>

              {group.transactions.map((item, index) => {
                const categoryName =
                  item.categoryName ?? (item.type === "transfer" ? "转账" : "未分类");
                const description = item.description.trim();

                return (
                  <View key={item.id}>
                    {index > 0 ? <Divider style={{ marginVertical: 6 }} /> : null}
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`${categoryName} 交易详情`}
                      onPress={() => setSelectedTransaction(item)}
                    >
                      <View
                        style={{
                          flexDirection: "row",
                          justifyContent: "space-between",
                          gap: 12,
                        }}
                      >
                        <View style={{ flex: 1, gap: description ? 2 : 1 }}>
                          <Text variant="titleMedium">{categoryName}</Text>
                          {description ? (
                            <Text
                              variant="bodyMedium"
                              style={{ color: theme.colors.textMuted }}
                            >
                              {description}
                            </Text>
                          ) : null}
                          <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
                            {item.accountName}
                          </Text>
                        </View>
                        <View style={{ alignItems: "flex-end", gap: 4 }}>
                          <Text
                            variant="titleMedium"
                            style={{
                              color:
                                item.type === "expense"
                                  ? theme.colors.danger
                                  : item.type === "income"
                                    ? theme.colors.success
                                    : theme.colors.accent,
                              fontWeight: "700",
                            }}
                          >
                            {formatSignedCurrency(
                              item.type === "expense" ? -item.amount : item.amount,
                            )}
                          </Text>
                          <Text
                            variant="bodyMedium"
                            style={{ color: theme.colors.textMuted }}
                          >
                            {new Date(item.transactionDate).toLocaleTimeString("zh-CN", {
                              hour: "2-digit",
                              minute: "2-digit",
                            })}
                          </Text>
                        </View>
                      </View>
                    </Pressable>
                  </View>
                );
              })}
            </Card.Content>
          </Card>
        ))}
      </Screen>

      <Portal>
        <Modal
          visible={selectedTransaction !== null}
          onDismiss={() => setSelectedTransaction(null)}
          contentContainerStyle={{ flex: 1, justifyContent: "flex-end" }}
        >
          {selectedTransactionSummary ? (
            <View
              style={{
                backgroundColor: theme.colors.surface,
                borderTopLeftRadius: 24,
                borderTopRightRadius: 24,
                borderWidth: 1,
                borderColor: theme.colors.border,
                padding: 20,
                gap: 16,
              }}
            >
              <View
                style={{
                  flexDirection: "row",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <Text variant="titleLarge">交易详情</Text>
                <Button compact onPress={() => setSelectedTransaction(null)}>
                  关闭
                </Button>
              </View>

              <View style={{ gap: 4 }}>
                <Text variant="headlineSmall">{selectedTransactionSummary.categoryName}</Text>
                <Text
                  variant="titleLarge"
                  style={{
                    color:
                      selectedTransaction?.type === "expense"
                        ? theme.colors.danger
                        : selectedTransaction?.type === "income"
                          ? theme.colors.success
                          : theme.colors.accent,
                    fontWeight: "700",
                  }}
                >
                  {selectedTransactionSummary.amount}
                </Text>
              </View>

              <View style={{ gap: 12 }}>
                {[
                  {
                    label: "类型",
                    value: selectedTransactionSummary.typeLabel,
                  },
                  {
                    label: "账户",
                    value: selectedTransaction?.accountName ?? "",
                  },
                  { label: "时间", value: selectedTransactionSummary.time },
                  {
                    label: "备注",
                    value: selectedTransactionSummary.description,
                  },
                ].map((item) => (
                  <View key={item.label} style={{ gap: 4 }}>
                    <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                      {item.label}
                    </Text>
                    <Text variant="bodyLarge">{item.value}</Text>
                  </View>
                ))}
              </View>
            </View>
          ) : null}
        </Modal>
      </Portal>
    </>
  );
}
