import { useCallback, useEffect, useState } from "react";

import {
  createTransaction,
  listCategories,
  listPaymentMethods,
} from "@/src/features/transactions/transaction.service";
import QuickEntrySheetForm, {
  type QuickEntryFormValue,
} from "@/src/features/transactions/components/QuickEntrySheetForm";
import { useUIStore } from "@/src/store/ui.store";
import type { Category, PaymentMethod } from "@/src/types/domain";

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
  const [categories, setCategories] = useState<Category[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  const quickEntry = useUIStore((state) => state.quickEntry);
  const quickEntrySheetVisible = useUIStore((state) => state.quickEntrySheetVisible);
  const setQuickEntry = useUIStore((state) => state.setQuickEntry);
  const resetQuickEntry = useUIStore((state) => state.resetQuickEntry);
  const bumpRefreshKey = useUIStore((state) => state.bumpRefreshKey);
  const openQuickEntrySheet = useUIStore((state) => state.openQuickEntrySheet);
  const closeQuickEntrySheet = useUIStore((state) => state.closeQuickEntrySheet);

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
    (categoryRows: Category[], paymentMethodRows: PaymentMethod[]) => {
      const nextCategoryId =
        quickEntry.categoryId && categoryRows.some((item) => item.id === quickEntry.categoryId)
          ? quickEntry.categoryId
          : (categoryRows[0]?.id ?? null);

      const nextPaymentMethodId =
        quickEntry.paymentMethodId &&
        paymentMethodRows.some((item) => item.id === quickEntry.paymentMethodId)
          ? quickEntry.paymentMethodId
          : null;

      if (
        nextCategoryId !== quickEntry.categoryId ||
        nextPaymentMethodId !== quickEntry.paymentMethodId
      ) {
        setQuickEntry({
          categoryId: nextCategoryId,
          paymentMethodId: nextPaymentMethodId,
        });
      }
    },
    [quickEntry.categoryId, quickEntry.paymentMethodId, setQuickEntry],
  );

  const loadData = useCallback(async () => {
    const [categoryRows, paymentMethodRows] = await Promise.all([
      listCategories(quickEntry.type),
      listPaymentMethods(),
    ]);

    setCategories(categoryRows);
    setPaymentMethods(paymentMethodRows);
    syncDefaults(categoryRows, paymentMethodRows);
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

    if (!quickEntry.categoryId || !amount || amount <= 0) {
      setMessage("请先补全分类和金额");
      return;
    }

    setSaving(true);
    setMessage("");

    try {
      await createTransaction({
        categoryId: quickEntry.categoryId,
        paymentMethodId: quickEntry.paymentMethodId,
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
    quickEntry.amountText,
    quickEntry.categoryId,
    quickEntry.description,
    quickEntry.paymentMethodId,
    quickEntry.transactionDate,
    quickEntry.type,
    resetQuickEntry,
  ]);

  return (
    <QuickEntrySheetForm
      visible={resolvedVisible}
      categories={categories}
      paymentMethods={paymentMethods}
      saving={saving}
      message={message}
      value={{
        type: quickEntry.type,
        amountText: quickEntry.amountText,
        categoryId: quickEntry.categoryId,
        paymentMethodId: quickEntry.paymentMethodId,
        transactionDate: quickEntry.transactionDate,
      }}
      onChange={handleChange}
      onDismiss={handleDismiss}
      onSubmit={handleSubmit}
    />
  );
}
