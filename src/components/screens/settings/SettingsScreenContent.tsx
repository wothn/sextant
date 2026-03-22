import type { Category } from "@/src/types/domain";

import { BudgetSettingsCard } from "@/src/components/screens/settings/BudgetSettingsCard";
import { DataManagementCard } from "@/src/components/screens/settings/DataManagementCard";
import { FeedbackMessageCard } from "@/src/components/screens/settings/FeedbackMessageCard";

interface SettingsScreenContentProps {
  categories: Category[];
  selectedCategory: string | null;
  selectedName: string;
  budgetText: string;
  message: string;
  onSelectCategory: (categoryId: string) => void;
  onChangeBudgetText: (value: string) => void;
  onSaveBudget: () => void;
  onExportCsv: () => void;
}

export function SettingsScreenContent({
  categories,
  selectedCategory,
  selectedName,
  budgetText,
  message,
  onSelectCategory,
  onChangeBudgetText,
  onSaveBudget,
  onExportCsv,
}: SettingsScreenContentProps) {
  return (
    <>
      <BudgetSettingsCard
        categories={categories}
        selectedCategory={selectedCategory}
        selectedName={selectedName}
        budgetText={budgetText}
        onSelectCategory={onSelectCategory}
        onChangeBudgetText={onChangeBudgetText}
        onSave={onSaveBudget}
      />
      <DataManagementCard onExportCsv={onExportCsv} />
      <FeedbackMessageCard message={message} />
    </>
  );
}
