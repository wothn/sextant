import type { BudgetProgressItem } from "@/src/features/budgets/budget.service";
import type { Category } from "@/src/types/domain";

import { BudgetEditorCard } from "@/src/components/screens/budgets/BudgetEditorCard";
import { BudgetProgressCard } from "@/src/components/screens/budgets/BudgetProgressCard";
import { FeedbackMessageCard } from "@/src/components/screens/settings/FeedbackMessageCard";
import { AsyncStateCard } from "@/src/components/screens/shared/AsyncStateCard";

interface BudgetScreenContentProps {
  budgets: BudgetProgressItem[];
  categories: Category[];
  selectedCategory: string | null;
  selectedName: string;
  budgetText: string;
  message: string;
  loading: boolean;
  error: unknown;
  onReload: () => void;
  onSelectCategory: (categoryId: string) => void;
  onChangeBudgetText: (value: string) => void;
  onSaveBudget: () => void;
}

function getErrorDescription(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  if (typeof error === "string") {
    return error;
  }

  return "请稍后再试。";
}

export function BudgetScreenContent({
  budgets,
  categories,
  selectedCategory,
  selectedName,
  budgetText,
  message,
  loading,
  error,
  onReload,
  onSelectCategory,
  onChangeBudgetText,
  onSaveBudget,
}: BudgetScreenContentProps) {
  if (loading) {
    return <AsyncStateCard title="正在读取预算" description="稍等一下，本月预算进度马上就好。" />;
  }

  if (error) {
    return (
      <AsyncStateCard
        title="预算加载失败"
        description={getErrorDescription(error)}
        actionLabel="重试"
        onAction={onReload}
      />
    );
  }

  return (
    <>
      <BudgetProgressCard budgets={budgets} />
      <BudgetEditorCard
        categories={categories}
        selectedCategory={selectedCategory}
        selectedName={selectedName}
        budgetText={budgetText}
        onSelectCategory={onSelectCategory}
        onChangeBudgetText={onChangeBudgetText}
        onSave={onSaveBudget}
      />
      <FeedbackMessageCard message={message} />
    </>
  );
}
