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

const DAILY_LINES = [
  "把今天折成一页",
  "让心事慢慢落地",
  "给生活一点微光",
  "把日子写轻一点",
  "把勇气放进清晨",
  "把温柔留给自己",
  "把风交给方向",
  "把念头归于安静",
  "把时间交给当下",
  "让步子从容些",
  "把忙碌变得可爱",
  "把期待留给晴天",
  "把浪漫装进口袋",
  "把心跳交给节奏",
];

function getDayOfYear(date: Date): number {
  const start = new Date(date.getFullYear(), 0, 0);
  const diff = date.getTime() - start.getTime();
  return Math.floor(diff / 86_400_000);
}

function getGreeting(): { greeting: string; mood: string } {
  const now = new Date();
  const hour = now.getHours();
  const dayOfYear = getDayOfYear(now);
  const greeting = DAILY_LINES[dayOfYear % DAILY_LINES.length] ?? "把今天折成一页";

  if (hour >= 0 && hour < 5) {
    return { greeting, mood: "夜色未央" };
  } else if (hour >= 5 && hour < 9) {
    return { greeting, mood: "晨光微澜" };
  } else if (hour >= 9 && hour < 11) {
    return { greeting, mood: "风从窗边过" };
  } else if (hour >= 11 && hour < 14) {
    return { greeting, mood: "日光正好" };
  } else if (hour >= 14 && hour < 17) {
    return { greeting, mood: "云影缓行" };
  } else if (hour >= 17 && hour < 19) {
    return { greeting, mood: "暮色初至" };
  } else {
    return { greeting, mood: "灯火将眠" };
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

function getTransactionCategoryLabel(item: TransactionListItem): string {
  if (item.categoryName) {
    return item.categoryName;
  }

  return item.type === "transfer" ? "转移记录" : "未分类";
}

function getTransactionTypeLabel(item: TransactionListItem): string {
  if (item.type === "income") {
    return "收入";
  }

  if (item.type === "transfer") {
    return "转移";
  }

  return item.includeInSpending === 1 ? "支出" : "支出（不计入统计）";
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

    return {
      categoryName: getTransactionCategoryLabel(selectedTransaction),
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
      typeLabel: getTransactionTypeLabel(selectedTransaction),
      spendingLabel:
        selectedTransaction.type !== "expense"
          ? "不适用"
          : selectedTransaction.includeInSpending === 1
            ? "计入支出统计"
            : "不计入支出统计",
      paymentMethodName: selectedTransaction.paymentMethodName ?? "未设置",
      description: selectedTransaction.description.trim() || "未填写",
    };
  }, [selectedTransaction]);

  const { greeting, mood } = getGreeting();

  return (
    <>
      <Screen contentContainerStyle={{ paddingBottom: 132 }}>
        <View style={{ gap: 4 }}>
          <Text variant="headlineMedium" style={{ fontWeight: "700", letterSpacing: -0.4 }}>
            {greeting}
          </Text>
          <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
            {formatDate()} · {mood}
          </Text>
        </View>

        <Card style={{ borderRadius: 22 }}>
          <Card.Content style={{ gap: 12 }}>
            <View style={{ gap: 6 }}>
              <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
                今天已经花了
              </Text>
              <View
                style={{
                  flexDirection: "row",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: 12,
                }}
              >
                <Text
                  variant="headlineSmall"
                  tabularNums
                  style={{ color: theme.colors.danger, fontWeight: "800" }}
                >
                  {formatCurrency(todaySummary.expense)}
                </Text>
                <View style={{ alignItems: "flex-end", gap: 2 }}>
                  <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                    净变化
                  </Text>
                  <Text variant="bodySmall" style={{ color: theme.colors.textMuted }} tabularNums>
                    {formatSignedCurrency(todaySummary.net)}
                  </Text>
                </View>
              </View>
            </View>

            <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
              <View style={{ flex: 1, gap: 4 }}>
                <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                  本月支出
                </Text>
                <Text variant="titleMedium" tabularNums style={{ color: theme.colors.danger }}>
                  {formatCurrency(monthSummary.expense)}
                </Text>
              </View>
              <View style={{ flex: 1, gap: 4, alignItems: "flex-end" }}>
                <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                  本月收入
                </Text>
                <Text variant="titleMedium" tabularNums style={{ color: theme.colors.success }}>
                  {formatCurrency(monthSummary.income)}
                </Text>
              </View>
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
                  <Text variant="labelMedium" style={{ color: theme.colors.danger }} tabularNums>
                    支 {formatCurrency(group.totalExpense)}
                  </Text>
                  <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                    ·
                  </Text>
                  <Text variant="labelMedium" style={{ color: theme.colors.success }} tabularNums>
                    收 {formatCurrency(group.totalIncome)}
                  </Text>
                </View>
              </View>

              {group.transactions.map((item, index) => {
                const categoryName = getTransactionCategoryLabel(item);
                const description = item.description.trim();
                const paymentMethodName = item.paymentMethodName?.trim() ?? "";

                return (
                  <View key={item.id}>
                    {index > 0 ? <Divider style={{ marginVertical: 6 }} /> : null}
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`${categoryName} 交易详情`}
                      onPress={() => setSelectedTransaction(item)}
                      style={({ pressed }) => [
                        {
                          borderRadius: 12,
                          paddingVertical: 6,
                          paddingHorizontal: 4,
                        },
                        pressed ? { backgroundColor: theme.colors.surfaceAlt } : null,
                      ]}
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
                            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
                              {description}
                            </Text>
                          ) : null}
                          {paymentMethodName ? (
                            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
                              {paymentMethodName}
                            </Text>
                          ) : null}
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
                            tabularNums
                          >
                            {formatSignedCurrency(
                              item.type === "expense" ? -item.amount : item.amount,
                            )}
                          </Text>
                          <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
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
                  tabularNums
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
                    label: "支付方式",
                    value: selectedTransactionSummary.paymentMethodName,
                  },
                  { label: "统计口径", value: selectedTransactionSummary.spendingLabel },
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
