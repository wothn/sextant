import { fireEvent, screen, waitFor } from "@testing-library/react-native";

import QuickEntryScreen from "@/app/(tabs)/quick-entry";
import { renderWithProviders } from "@/src/test/render";
import { useUIStore } from "@/src/store/ui.store";

const mockReplace = jest.fn();
const mockListAccounts = jest.fn();
const mockListCategories = jest.fn();
const mockCreateTransaction = jest.fn();

jest.mock("expo-router", () => ({
  router: {
    replace: (...args: unknown[]) => mockReplace(...args),
  },
}));

jest.mock("@/src/features/transactions/account.service", () => ({
  listAccounts: () => mockListAccounts(),
}));

jest.mock("@/src/features/transactions/transaction.service", () => ({
  listCategories: (...args: unknown[]) => mockListCategories(...args),
  createTransaction: (...args: unknown[]) => mockCreateTransaction(...args),
}));

describe("QuickEntryScreen", () => {
  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T10:15:30.000Z"));
    useUIStore.getState().resetQuickEntry();
    useUIStore.setState({
      refreshKey: 0,
      quickEntrySheetVisible: true,
    });

    mockReplace.mockReset();
    mockListAccounts.mockReset();
    mockListCategories.mockReset();
    mockCreateTransaction.mockReset();

    mockListAccounts.mockResolvedValue([
      {
        id: "acc-1",
        name: "现金",
        balance: 12,
        type: "cash",
        currency: "CNY",
        isActive: 1,
        createdAt: 1,
        updatedAt: 1,
      },
    ]);

    mockListCategories.mockImplementation((type: unknown) =>
      Promise.resolve(
        type === "income"
          ? [
              {
                id: "cat-2",
                name: "工资",
                type: "income",
                icon: "cash",
                color: "#66BB6A",
                isBuiltIn: 1,
                isActive: 1,
                createdAt: 1,
              },
            ]
          : [
              {
                id: "cat-1",
                name: "餐饮",
                type: "expense",
                icon: "food",
                color: "#FF7043",
                isBuiltIn: 1,
                isActive: 1,
                createdAt: 1,
              },
            ],
      ),
    );

    mockCreateTransaction.mockResolvedValue("tx-1");
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders default expense mode with today date and current time", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getByText("支出")).toBeTruthy();
      expect(screen.getByText("收入")).toBeTruthy();
      expect(screen.getByText("分类")).toBeTruthy();
      expect(screen.getByText("餐饮")).toBeTruthy();
    });

    expect(useUIStore.getState().quickEntry.type).toBe("expense");
    expect(screen.getByText(/今天/)).toBeTruthy();
    expect(screen.getByText(/:15$/)).toBeTruthy();
  });

  it("supports keypad input, type switching, popup time selection, and saving", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getByText("餐饮")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("收入"));

    await waitFor(() => {
      expect(mockListCategories).toHaveBeenLastCalledWith("income");
      expect(screen.getByText("工资")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("工资"));
    fireEvent.press(screen.getByText("1"));
    fireEvent.press(screen.getByText("2"));
    fireEvent.press(screen.getByText("."));
    fireEvent.press(screen.getByText("5"));

    fireEvent.press(screen.getByLabelText("时间"));

    await waitFor(() => {
      expect(screen.getByLabelText("时间弹窗标题", { includeHiddenElements: true })).toBeTruthy();
    });

    fireEvent.press(screen.getAllByText("09", { includeHiddenElements: true })[0]);
    fireEvent.press(screen.getAllByText("30", { includeHiddenElements: true })[0]);
    fireEvent.press(screen.getByText("完成", { includeHiddenElements: true }));

    fireEvent.press(screen.getByLabelText("保存本次记账"));

    await waitFor(() => {
      expect(mockCreateTransaction).toHaveBeenCalledTimes(1);
    });

    const payload = mockCreateTransaction.mock.calls[0]?.[0] as {
      accountId: string;
      categoryId: string;
      amount: number;
      type: "expense" | "income";
      description?: string;
      transactionDate: number;
    };

    expect(payload).toMatchObject({
      accountId: "acc-1",
      categoryId: "cat-2",
      amount: 12.5,
      type: "income",
      description: "",
    });

    const savedDate = new Date(payload.transactionDate);
    expect(savedDate.getHours()).toBe(9);
    expect(savedDate.getMinutes()).toBe(30);
    expect(mockReplace).toHaveBeenCalledWith("/(tabs)");
    expect(useUIStore.getState().refreshKey).toBe(1);
  });

  it("deletes the last keypad digit", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getByText("餐饮")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("5"));
    fireEvent.press(screen.getByText("0"));
    fireEvent.press(screen.getByLabelText("删除金额"));

    expect(screen.getByText("¥5")).toBeTruthy();
    expect(useUIStore.getState().quickEntry.amountText).toBe("5");
  });

  it("shows validation message when required fields are incomplete", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getByText("餐饮")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("保存本次记账"));

    await waitFor(() => {
      expect(screen.getByText("请先补全账户、分类和金额")).toBeTruthy();
    });

    expect(mockCreateTransaction).not.toHaveBeenCalled();
  });
});
