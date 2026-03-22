import { AnalyticsScreenContent } from "@/src/components/screens/analytics/AnalyticsScreenContent";
import {
  listCurrentMonthBudgetProgress,
  type BudgetProgressItem,
} from "@/src/features/budgets/budget.service";
import {
  getCurrentMonthCategoryBreakdown,
  getCurrentMonthSummary,
  getMonthlyTrend,
  type CategoryBreakdownItem,
  type MonthlyTrendPoint,
} from "@/src/features/transactions/transaction.service";
import { useAsyncData } from "@/src/hooks/use-async-data";
import { useUIStore } from "@/src/store/ui.store";
import { Screen } from "@/src/ui";

interface AnalyticsScreenData {
  summary: {
    income: number;
    expense: number;
    net: number;
  };
  trend: MonthlyTrendPoint[];
  categories: CategoryBreakdownItem[];
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
  budgets: [],
};

async function loadAnalyticsScreenData(): Promise<AnalyticsScreenData> {
  const [summary, budgets, trend, categories] = await Promise.all([
    getCurrentMonthSummary(),
    listCurrentMonthBudgetProgress(),
    getMonthlyTrend(6),
    getCurrentMonthCategoryBreakdown(),
  ]);

  return {
    summary,
    budgets,
    trend,
    categories,
  };
}

export default function AnalyticsScreen() {
  const refreshKey = useUIStore((state) => state.refreshKey);
  const { data, loading, error, reload } = useAsyncData<AnalyticsScreenData>({
    initialData: INITIAL_ANALYTICS_SCREEN_DATA,
    fetcher: loadAnalyticsScreenData,
    deps: [refreshKey],
  });

  return (
    <Screen contentContainerStyle={{ paddingBottom: 132 }}>
      <AnalyticsScreenContent
        summary={data.summary}
        trend={data.trend}
        categories={data.categories}
        budgets={data.budgets}
        loading={loading}
        error={error}
        onReload={reload}
      />
    </Screen>
  );
}
