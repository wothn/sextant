import { View } from "react-native";

import type {
  DailyTransactionGroup,
  TransactionListItem,
  TodaySummary,
} from "@/src/features/transactions/transaction.service";
import { Text, useTheme } from "@/src/ui";
import { AsyncStateCard } from "@/src/components/screens/shared/AsyncStateCard";
import { EmptyStateCard } from "@/src/components/screens/shared/EmptyStateCard";
import { HomeGreetingSection } from "@/src/components/screens/home/HomeGreetingSection";
import { TodaySummaryCard } from "@/src/components/screens/home/TodaySummaryCard";
import { TransactionDetailSheet } from "@/src/components/screens/home/TransactionDetailSheet";
import { TransactionGroupCard } from "@/src/components/screens/home/TransactionGroupCard";

interface HomeScreenContentProps {
  groups: DailyTransactionGroup[];
  monthSummary: {
    income: number;
    expense: number;
    net: number;
  };
  todaySummary: TodaySummary;
  loading: boolean;
  error: Error | null;
  onReload: () => void;
  onSelectTransaction: (item: TransactionListItem) => void;
  selectedTransactionTone: string;
  selectedTransactionSummary: {
    categoryName: string;
    amount: string;
    time: string;
    typeLabel: string;
    spendingLabel: string;
    paymentMethodName: string;
    description: string;
  } | null;
  onDismissTransaction: () => void;
}

export function HomeScreenContent({
  groups,
  monthSummary,
  todaySummary,
  loading,
  error,
  onReload,
  onSelectTransaction,
  selectedTransactionTone,
  selectedTransactionSummary,
  onDismissTransaction,
}: HomeScreenContentProps) {
  const theme = useTheme();

  return (
    <>
      <View style={{ gap: 4 }}>
        <HomeGreetingSection />
      </View>

      <TodaySummaryCard todaySummary={todaySummary} monthSummary={monthSummary} />

      {error ? (
        <AsyncStateCard
          title="数据加载失败"
          description={error.message || "请稍后重试"}
          actionLabel="重试"
          onAction={onReload}
        />
      ) : null}

      {loading && !error ? <Text>正在整理你的账本…</Text> : null}
      {!loading && !error && groups.length === 0 ? (
        <EmptyStateCard title="还没有记录" description="记一笔后，这里会按日期排好最近的明细。" />
      ) : null}

      {groups.map((group) => (
        <TransactionGroupCard
          key={group.dateKey}
          group={group}
          onSelectTransaction={onSelectTransaction}
        />
      ))}

      <TransactionDetailSheet
        visible={selectedTransactionSummary !== null}
        amountTone={selectedTransactionTone}
        summary={selectedTransactionSummary}
        onDismiss={onDismissTransaction}
      />
    </>
  );
}
