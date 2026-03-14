import { useCallback, useEffect, useState } from "react";

import { listAccounts } from "@/src/features/transactions/account.service";
import {
  createTransaction,
  listCategories,
} from "@/src/features/transactions/transaction.service";
import QuickEntrySheetForm, {
  type QuickEntryFormValue,
} from "@/src/features/transactions/components/QuickEntrySheetForm";
import { useUIStore } from "@/src/store/ui.store";
import type { Account, Category } from "@/src/types/domain";

interface QuickEntrySheetContainerProps {
  visible?: boolean;
  onMount?: () => void;
  onDismiss?: () => void;
}

export default function QuickEntrySheetContainer({
  visible,
  onMount,
  onDismiss,
}: QuickEntrySheetContainerProps) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  const quickEntry = useUIStore((state) => state.quickEntry);
  const quickEntrySheetVisible = useUIStore(
    (state) => state.quickEntrySheetVisible,
  );
  const setQuickEntry = useUIStore((state) => state.setQuickEntry);
  const resetQuickEntry = useUIStore((state) => state.resetQuickEntry);
  const bumpRefreshKey = useUIStore((state) => state.bumpRefreshKey);
  const openQuickEntrySheet = useUIStore((state) => state.openQuickEntrySheet);
  const closeQuickEntrySheet = useUIStore(
    (state) => state.closeQuickEntrySheet,
  );

  const resolvedVisible = visible ?? quickEntrySheetVisible;

  useEffect(() => {
    if (!visible) {
      return;
    }

    if (onMount) {
      onMount();
      return;
    }

    if (!quickEntrySheetVisible) {
      openQuickEntrySheet();
    }
  }, [onMount, openQuickEntrySheet, quickEntrySheetVisible, visible]);

  const syncDefaults = useCallback(
    (accountRows: Account[], categoryRows: Category[]) => {
      const nextAccountId =
        quickEntry.accountId &&
        accountRows.some((item) => item.id === quickEntry.accountId)
          ? quickEntry.accountId
          : (accountRows[0]?.id ?? null);

      const nextCategoryId =
        quickEntry.categoryId &&
        categoryRows.some((item) => item.id === quickEntry.categoryId)
          ? quickEntry.categoryId
          : (categoryRows[0]?.id ?? null);

      if (
        nextAccountId !== quickEntry.accountId ||
        nextCategoryId !== quickEntry.categoryId
      ) {
        setQuickEntry({
          accountId: nextAccountId,
          categoryId: nextCategoryId,
        });
      }
    },
    [quickEntry.accountId, quickEntry.categoryId, setQuickEntry],
  );

  const loadData = useCallback(async () => {
    const [accountRows, categoryRows] = await Promise.all([
      listAccounts(),
      listCategories(quickEntry.type),
    ]);

    setAccounts(accountRows);
    setCategories(categoryRows);
    syncDefaults(accountRows, categoryRows);
  }, [quickEntry.type, syncDefaults]);

  useEffect(() => {
    if (!resolvedVisible) {
      return;
    }

    void loadData();
  }, [loadData, resolvedVisible]);

  const handleDismiss = useCallback(() => {
    closeQuickEntrySheet();
    setMessage("");
    onDismiss?.();
  }, [closeQuickEntrySheet, onDismiss]);

  const handleChange = useCallback(
    (partial: Partial<QuickEntryFormValue>) => {
      setMessage("");
      setQuickEntry(partial);
    },
    [setQuickEntry],
  );

  const handleSubmit = useCallback(async () => {
    const amount = Number(quickEntry.amountText);

    if (
      !quickEntry.accountId ||
      !quickEntry.categoryId ||
      !amount ||
      amount <= 0
    ) {
      setMessage("请先补全账户、分类和金额");
      return;
    }

    setSaving(true);
    setMessage("");

    try {
      await createTransaction({
        accountId: quickEntry.accountId,
        categoryId: quickEntry.categoryId,
        amount,
        type: quickEntry.type,
        description: quickEntry.description,
        transactionDate: quickEntry.transactionDate,
      });

      bumpRefreshKey();
      closeQuickEntrySheet();
      resetQuickEntry();
      setMessage("");
      onDismiss?.();
    } finally {
      setSaving(false);
    }
  }, [
    bumpRefreshKey,
    closeQuickEntrySheet,
    onDismiss,
    quickEntry.accountId,
    quickEntry.amountText,
    quickEntry.categoryId,
    quickEntry.description,
    quickEntry.transactionDate,
    quickEntry.type,
    resetQuickEntry,
  ]);

  return (
    <QuickEntrySheetForm
      visible={resolvedVisible}
      categories={categories}
      saving={saving}
      message={message}
      value={{
        type: quickEntry.type,
        amountText: quickEntry.amountText,
        categoryId: quickEntry.categoryId,
        transactionDate: quickEntry.transactionDate,
      }}
      onChange={handleChange}
      onDismiss={handleDismiss}
      onSubmit={handleSubmit}
    />
  );
}
