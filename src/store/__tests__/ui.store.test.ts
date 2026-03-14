import { useUIStore } from "@/src/store/ui.store";

describe("ui.store", () => {
  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T08:00:00.000Z"));
    useUIStore.getState().resetQuickEntry();
    useUIStore.setState({ refreshKey: 0, quickEntrySheetVisible: false });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("merges quick entry updates", () => {
    useUIStore.getState().setQuickEntry({ amountText: "88", type: "income" });

    expect(useUIStore.getState().quickEntry).toMatchObject({
      amountText: "88",
      type: "income",
      description: "",
    });
  });

  it("resets the quick entry draft", () => {
    useUIStore.getState().setQuickEntry({
      amountText: "12",
      description: "午餐",
      paymentMethodId: "pm-1",
    });

    useUIStore.getState().resetQuickEntry();

    expect(useUIStore.getState().quickEntry).toEqual({
      amountText: "",
      description: "",
      categoryId: null,
      paymentMethodId: null,
      type: "expense",
      transactionDate: new Date("2026-03-08T08:00:00.000Z").getTime(),
    });
  });

  it("initializes quick entry draft with current timestamp", () => {
    expect(useUIStore.getState().quickEntry.transactionDate).toBe(
      new Date("2026-03-08T08:00:00.000Z").getTime(),
    );
  });

  it("opens the quick entry sheet", () => {
    useUIStore.getState().openQuickEntrySheet();

    expect(useUIStore.getState().quickEntrySheetVisible).toBe(true);
  });

  it("closes the quick entry sheet", () => {
    useUIStore.setState({ quickEntrySheetVisible: true });

    useUIStore.getState().closeQuickEntrySheet();

    expect(useUIStore.getState().quickEntrySheetVisible).toBe(false);
  });

  it("bumps refresh key for dashboard reloads", () => {
    useUIStore.getState().bumpRefreshKey();
    useUIStore.getState().bumpRefreshKey();

    expect(useUIStore.getState().refreshKey).toBe(2);
  });
});
