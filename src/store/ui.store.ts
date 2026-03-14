import { create } from "zustand";

interface QuickEntryDraft {
  amountText: string;
  description: string;
  categoryId: string | null;
  accountId: string | null;
  type: "expense" | "income";
  transactionDate: number;
}

interface UIState {
  quickEntry: QuickEntryDraft;
  refreshKey: number;
  quickEntrySheetVisible: boolean;
  setQuickEntry: (partial: Partial<QuickEntryDraft>) => void;
  resetQuickEntry: () => void;
  bumpRefreshKey: () => void;
  openQuickEntrySheet: () => void;
  closeQuickEntrySheet: () => void;
}

const initialState: QuickEntryDraft = {
  amountText: "",
  description: "",
  categoryId: null,
  accountId: null,
  type: "expense",
  transactionDate: Date.now(),
};

export const useUIStore = create<UIState>((set) => ({
  quickEntry: initialState,
  refreshKey: 0,
  quickEntrySheetVisible: false,
  setQuickEntry: (partial) => set((state) => ({ quickEntry: { ...state.quickEntry, ...partial } })),
  resetQuickEntry: () =>
    set({
      quickEntry: {
        ...initialState,
        transactionDate: Date.now(),
      },
    }),
  bumpRefreshKey: () => set((state) => ({ refreshKey: state.refreshKey + 1 })),
  openQuickEntrySheet: () => set({ quickEntrySheetVisible: true }),
  closeQuickEntrySheet: () => set({ quickEntrySheetVisible: false }),
}));
