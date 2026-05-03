import { useEffect, useMemo, useState } from "react";

import { BudgetScreenContent } from "@/src/components/screens/budgets/BudgetScreenContent";
import {
  listCurrentMonthBudgetProgress,
  upsertMonthlyBudget,
  type BudgetProgressItem,
} from "@/src/features/budgets/budget.service";
import { listCategories } from "@/src/features/transactions/transaction.service";
import { AppScreen } from "@/src/components/layout/AppScreen";
import { useAsyncData } from "@/src/hooks/use-async-data";
import { formatCurrency } from "@/src/lib/format";
import { useUIStore } from "@/src/store/ui.store";
import type { Category } from "@/src/types/domain";

interface BudgetScreenData {
  budgets: BudgetProgressItem[];
  categories: Category[];
}

const INITIAL_BUDGET_SCREEN_DATA: BudgetScreenData = {
  budgets: [],
  categories: [],
};

async function loadBudgetScreenData(): Promise<BudgetScreenData> {
  const [budgets, categories] = await Promise.all([
    listCurrentMonthBudgetProgress(),
    listCategories("expense"),
  ]);

  return {
    budgets,
    categories: categories.filter((item) => item.includeInSpending === 1),
  };
}

export default function BudgetScreen() {
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [budgetText, setBudgetText] = useState("");
  const [message, setMessage] = useState("");
  const refreshKey = useUIStore((state) => state.refreshKey);
  const bumpRefreshKey = useUIStore((state) => state.bumpRefreshKey);
  const { data, loading, error, reload } = useAsyncData<BudgetScreenData>({
    initialData: INITIAL_BUDGET_SCREEN_DATA,
    fetcher: loadBudgetScreenData,
    deps: [refreshKey],
  });

  useEffect(() => {
    setSelectedCategory((current) => current ?? data.categories[0]?.id ?? null);
  }, [data.categories]);

  const selectedName = useMemo(
    () => data.categories.find((category) => category.id === selectedCategory)?.name ?? "未选择",
    [data.categories, selectedCategory],
  );

  const handleBudgetSave = async (): Promise<void> => {
    const amount = Number(budgetText);
    if (!selectedCategory || !amount || amount <= 0) {
      setMessage("请输入有效预算金额");
      return;
    }

    await upsertMonthlyBudget(selectedCategory, amount, 0.8);
    setMessage(`预算已保存：${selectedName} ${formatCurrency(amount)}`);
    setBudgetText("");
    bumpRefreshKey();
  };

  return (
    <AppScreen contentContainerStyle={{ paddingBottom: 132 }}>
      <BudgetScreenContent
        budgets={data.budgets}
        categories={data.categories}
        selectedCategory={selectedCategory}
        selectedName={selectedName}
        budgetText={budgetText}
        message={message}
        loading={loading}
        error={error}
        onReload={reload}
        onSelectCategory={setSelectedCategory}
        onChangeBudgetText={setBudgetText}
        onSaveBudget={handleBudgetSave}
      />
    </AppScreen>
  );
}
