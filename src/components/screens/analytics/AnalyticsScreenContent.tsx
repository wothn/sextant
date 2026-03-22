import type { BudgetProgressItem } from "@/src/features/budgets/budget.service";
import type {
  CategoryBreakdownItem,
  MonthlyTrendPoint,
} from "@/src/features/transactions/transaction.service";
import { AnalyticsSummarySection } from "@/src/components/screens/analytics/AnalyticsSummarySection";
import { BudgetProgressSection } from "@/src/components/screens/analytics/BudgetProgressSection";
import { SpendingStructureSection } from "@/src/components/screens/analytics/SpendingStructureSection";
import { TrendSection } from "@/src/components/screens/analytics/TrendSection";

interface AnalyticsScreenContentProps {
  summary: {
    income: number;
    expense: number;
    net: number;
  };
  trend: MonthlyTrendPoint[];
  categories: CategoryBreakdownItem[];
  budgets: BudgetProgressItem[];
  loading: boolean;
  error: Error | null;
  onReload: () => void;
}

export function AnalyticsScreenContent({
  summary,
  trend,
  categories,
  budgets,
  loading,
  error,
  onReload,
}: AnalyticsScreenContentProps) {
  return (
    <>
      <AnalyticsSummarySection summary={summary} />
      <TrendSection
        trend={trend}
        loading={loading}
        error={error}
        onReload={onReload}
        topSpendingCategoryName={categories[0]?.categoryName ?? null}
      />
      <SpendingStructureSection categories={categories} error={error} />
      <BudgetProgressSection budgets={budgets} />
    </>
  );
}
