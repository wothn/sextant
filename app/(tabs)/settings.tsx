import * as Sharing from "expo-sharing";
import { useEffect, useMemo, useState } from "react";

import { SettingsScreenContent } from "@/src/components/screens/settings/SettingsScreenContent";
import { upsertMonthlyBudget } from "@/src/features/budgets/budget.service";
import { listCategories } from "@/src/features/transactions/transaction.service";
import { exportTransactionsCsv } from "@/src/lib/backup/backup.service";
import type { Category } from "@/src/types/domain";
import { AppScreen } from "@/src/components/layout/AppScreen";

export default function SettingsScreen() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [budgetText, setBudgetText] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let mounted = true;

    const loadCategories = async (): Promise<void> => {
      const rows = await listCategories("expense");
      const spendingRows = rows.filter((item) => item.includeInSpending === 1);
      if (!mounted) {
        return;
      }
      setCategories(spendingRows);
      setSelectedCategory((current) => current ?? spendingRows[0]?.id ?? null);
    };

    void loadCategories();

    return () => {
      mounted = false;
    };
  }, []);

  const selectedName = useMemo(
    () => categories.find((category) => category.id === selectedCategory)?.name ?? "未选择",
    [categories, selectedCategory],
  );

  const handleBudgetSave = async (): Promise<void> => {
    const amount = Number(budgetText);
    if (!selectedCategory || !amount || amount <= 0) {
      setMessage("请输入有效预算金额");
      return;
    }
    await upsertMonthlyBudget(selectedCategory, amount, 0.8);
    setMessage(`预算已保存：${selectedName} ¥${amount.toFixed(2)}`);
    setBudgetText("");
  };

  const handleExportCsv = async (): Promise<void> => {
    try {
      const path = await exportTransactionsCsv();
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(path);
      }
      setMessage(`导出成功：${path}`);
    } catch (error) {
      setMessage(`导出失败：${String(error)}`);
    }
  };

  return (
    <AppScreen contentContainerStyle={{ paddingBottom: 132 }}>
      <SettingsScreenContent
        categories={categories}
        selectedCategory={selectedCategory}
        selectedName={selectedName}
        budgetText={budgetText}
        message={message}
        onSelectCategory={setSelectedCategory}
        onChangeBudgetText={setBudgetText}
        onSaveBudget={handleBudgetSave}
        onExportCsv={handleExportCsv}
      />
    </AppScreen>
  );
}
