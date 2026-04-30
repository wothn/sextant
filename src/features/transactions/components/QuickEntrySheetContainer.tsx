import { useCallback, useEffect, useRef, useState } from "react";

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
import { MOTION_DURATION_BASE } from "@/src/lib/motion";

interface QuickEntryMessageState {
  text: string;
  tone: "error" | "success";
}

const EMPTY_MESSAGE: QuickEntryMessageState = {
  text: "",
  tone: "error",
};

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
  const [message, setMessage] = useState<QuickEntryMessageState>(EMPTY_MESSAGE);
  const [controlledVisible, setControlledVisible] = useState(Boolean(visible));
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const shouldResetOnCloseRef = useRef(false);

  const quickEntry = useUIStore((state) => state.quickEntry);
  const quickEntrySheetVisible = useUIStore((state) => state.quickEntrySheetVisible);
  const setQuickEntry = useUIStore((state) => state.setQuickEntry);
  const resetQuickEntry = useUIStore((state) => state.resetQuickEntry);
  const bumpRefreshKey = useUIStore((state) => state.bumpRefreshKey);
  const openQuickEntrySheet = useUIStore((state) => state.openQuickEntrySheet);
  const closeQuickEntrySheet = useUIStore((state) => state.closeQuickEntrySheet);

  const resolvedVisible = visible === undefined ? quickEntrySheetVisible : controlledVisible;

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

  useEffect(() => {
    if (visible === undefined) {
      return;
    }

    setControlledVisible(visible);
  }, [visible]);

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

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) {
        clearTimeout(closeTimerRef.current);
      }
    };
  }, []);

  const clearCloseTimer = useCallback((): void => {
    if (!closeTimerRef.current) {
      return;
    }

    clearTimeout(closeTimerRef.current);
    closeTimerRef.current = null;
  }, []);

  const handleDismiss = useCallback(() => {
    clearCloseTimer();
    shouldResetOnCloseRef.current = false;
    if (visible === undefined) {
      closeQuickEntrySheet();
    } else {
      setControlledVisible(false);
    }
    setMessage(EMPTY_MESSAGE);
  }, [clearCloseTimer, closeQuickEntrySheet, visible]);

  const handleAfterDismiss = useCallback(() => {
    clearCloseTimer();

    if (shouldResetOnCloseRef.current) {
      shouldResetOnCloseRef.current = false;
      resetQuickEntry();
      setMessage(EMPTY_MESSAGE);
    }

    if (visible !== undefined) {
      setControlledVisible(true);
    }

    onDismiss?.();
  }, [clearCloseTimer, onDismiss, resetQuickEntry, visible]);

  const handleChange = useCallback(
    (partial: Partial<QuickEntryFormValue>) => {
      setMessage(EMPTY_MESSAGE);
      setQuickEntry(partial);
    },
    [setQuickEntry],
  );

  const handleSubmit = useCallback(async () => {
    const amount = Number(quickEntry.amountText);

    if (!quickEntry.categoryId || !amount || amount <= 0) {
      setMessage({
        text: "请先补全分类和金额",
        tone: "error",
      });
      return;
    }

    setSaving(true);
    setMessage(EMPTY_MESSAGE);

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
      shouldResetOnCloseRef.current = true;
      setMessage({
        text: "已保存这笔记账",
        tone: "success",
      });
      clearCloseTimer();
      closeTimerRef.current = setTimeout(() => {
        if (visible === undefined) {
          closeQuickEntrySheet();
          return;
        }

        setControlledVisible(false);
      }, MOTION_DURATION_BASE);
    } catch (error) {
      const errorMessage =
        error instanceof Error && error.message
          ? `保存失败：${error.message}`
          : "保存失败，请稍后重试";

      setMessage({
        text: errorMessage,
        tone: "error",
      });
    } finally {
      setSaving(false);
    }
  }, [
    bumpRefreshKey,
    clearCloseTimer,
    closeQuickEntrySheet,
    quickEntry.amountText,
    quickEntry.categoryId,
    quickEntry.description,
    quickEntry.paymentMethodId,
    quickEntry.transactionDate,
    quickEntry.type,
    visible,
  ]);

  return (
    <QuickEntrySheetForm
      visible={resolvedVisible}
      categories={categories}
      paymentMethods={paymentMethods}
      saving={saving}
      message={message.text}
      messageTone={message.tone}
      value={{
        type: quickEntry.type,
        amountText: quickEntry.amountText,
        description: quickEntry.description,
        categoryId: quickEntry.categoryId,
        paymentMethodId: quickEntry.paymentMethodId,
        transactionDate: quickEntry.transactionDate,
      }}
      onChange={handleChange}
      onDismiss={handleDismiss}
      onAfterDismiss={handleAfterDismiss}
      onSubmit={handleSubmit}
    />
  );
}
