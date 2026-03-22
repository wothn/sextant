import { useMemo, useState } from "react";

import { HomeScreenContent } from "@/src/components/screens/home/HomeScreenContent";
import {
  getCurrentMonthSummary,
  getTodaySummary,
  listTransactionGroupsByDay,
  type DailyTransactionGroup,
  type TransactionListItem,
  type TodaySummary,
} from "@/src/features/transactions/transaction.service";
import { useAsyncData } from "@/src/hooks/use-async-data";
import { formatSignedCurrency } from "@/src/lib/format";
import { useUIStore } from "@/src/store/ui.store";
import { Screen, useTheme } from "@/src/ui";

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

  const selectedTransactionTone =
    selectedTransaction?.type === "expense"
      ? theme.colors.danger
      : selectedTransaction?.type === "income"
        ? theme.colors.success
        : theme.colors.accent;

  return (
    <Screen contentContainerStyle={{ paddingBottom: 132 }}>
      <HomeScreenContent
        groups={data.groups}
        monthSummary={data.monthSummary}
        todaySummary={data.todaySummary}
        loading={loading}
        error={error}
        onReload={reload}
        onSelectTransaction={setSelectedTransaction}
        selectedTransactionTone={selectedTransactionTone}
        selectedTransactionSummary={selectedTransactionSummary}
        onDismissTransaction={() => setSelectedTransaction(null)}
      />
    </Screen>
  );
}
