import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";

import QuickEntryScreen from "@/app/(tabs)/quick-entry";
import { renderWithProviders } from "@/src/test/render";
import { useUIStore } from "@/src/store/ui.store";

interface CreatedTransactionPayload {
  categoryId: string;
  paymentMethodId: string | null;
  amount: number;
  type: "expense" | "income";
  description: string;
  transactionDate: number;
}

function isCreatedTransactionPayload(value: unknown): value is CreatedTransactionPayload {
  if (!value || typeof value !== "object") {
    return false;
  }

  return "transactionDate" in value && typeof value.transactionDate === "number";
}

const mockReplace = jest.fn();
const mockListCategories = jest.fn();
const mockListPaymentMethods = jest.fn();
const mockCreateTransaction = jest.fn();

jest.mock("expo-router", () => ({
  router: {
    replace: (...args: unknown[]) => mockReplace(...args),
  },
}));

jest.mock("@/src/features/transactions/transaction.service", () => ({
  listCategories: (...args: unknown[]) => mockListCategories(...args),
  listPaymentMethods: () => mockListPaymentMethods(),
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
    mockListCategories.mockReset();
    mockListPaymentMethods.mockReset();
    mockCreateTransaction.mockReset();

    mockListPaymentMethods.mockResolvedValue([
      {
        id: "pm-1",
        name: "现金",
        icon: "cash",
        color: "#D97706",
        isActive: 1,
        isBuiltIn: 1,
        createdAt: 1,
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
                includeInSpending: 1,
                parentCategoryId: null,
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
                includeInSpending: 1,
                parentCategoryId: null,
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

  it("renders default expense mode with explicit date and current time", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getByText("快速记账")).toBeTruthy();
      expect(screen.getByText("支出")).toBeTruthy();
      expect(screen.getByText("收入")).toBeTruthy();
      expect(screen.getByText("支付方式")).toBeTruthy();
      expect(screen.getAllByText("分类").length).toBeGreaterThan(0);
      expect(screen.getByLabelText("备注")).toBeTruthy();
      expect(screen.getAllByText("餐饮").length).toBeGreaterThan(0);
    });

    expect(useUIStore.getState().quickEntry.type).toBe("expense");
    expect(screen.getByText("3月8日")).toBeTruthy();
    expect(screen.getByText(/:15$/)).toBeTruthy();
    expect(screen.getAllByText("不设置").length).toBeGreaterThan(0);
    expect(screen.queryByText(/^已选 /)).toBeNull();
  });

  it("supports keypad input, type switching, popup time selection, and saving", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getAllByText("餐饮").length).toBeGreaterThan(0);
    });

    fireEvent.press(screen.getByText("收入"));

    await waitFor(() => {
      expect(mockListCategories).toHaveBeenLastCalledWith("income");
      expect(screen.getAllByText("工资").length).toBeGreaterThan(0);
    });

    fireEvent.press(screen.getByLabelText("选择分类工资"));
    fireEvent.press(screen.getByText("1"));
    fireEvent.press(screen.getByText("2"));
    fireEvent.press(screen.getByText("."));
    fireEvent.press(screen.getByText("5"));
    fireEvent.press(screen.getByLabelText("备注"));

    await waitFor(() => {
      expect(screen.getByPlaceholderText("在此输入备注...")).toBeTruthy();
    });

    fireEvent.changeText(screen.getByPlaceholderText("在此输入备注..."), "工资到账");
    fireEvent.press(screen.getByText("确定"));

    fireEvent.press(screen.getByLabelText("时间"));

    await waitFor(() => {
      expect(screen.getByLabelText("时间弹窗标题", { includeHiddenElements: true })).toBeTruthy();
    });

    fireEvent.press(screen.getByLabelText("选择小时09", { includeHiddenElements: true }));
    fireEvent.press(screen.getByLabelText("选择分钟30", { includeHiddenElements: true }));
    fireEvent.press(screen.getByText("完成", { includeHiddenElements: true }));

    fireEvent.press(screen.getByLabelText("保存本次记账"));

    await waitFor(() => {
      expect(mockCreateTransaction).toHaveBeenCalledTimes(1);
    });

    act(() => {
      jest.runAllTimers();
    });

    const payload: unknown = mockCreateTransaction.mock.calls[0]?.[0];

    expect(payload).toEqual(
      expect.objectContaining({
        categoryId: "cat-2",
        paymentMethodId: null,
        amount: 12.5,
        type: "income",
        description: "工资到账",
      }),
    );

    expect(payload).toBeDefined();
    expect(isCreatedTransactionPayload(payload)).toBe(true);

    if (!isCreatedTransactionPayload(payload)) {
      throw new Error("missing transaction payload");
    }

    const savedDate = new Date(payload.transactionDate);
    expect(savedDate.getHours()).toBe(9);
    expect(savedDate.getMinutes()).toBe(30);
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/(tabs)");
    });
    expect(useUIStore.getState().refreshKey).toBe(1);
  });

  it("deletes the last keypad digit", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getAllByText("餐饮").length).toBeGreaterThan(0);
    });

    fireEvent.press(screen.getByText("5"));
    fireEvent.press(screen.getByText("0"));
    fireEvent.press(screen.getByLabelText("删除金额"));

    expect(screen.getByLabelText("金额").props.children).toBe("5");
    expect(screen.getAllByText("¥").length).toBeGreaterThan(0);
    expect(useUIStore.getState().quickEntry.amountText).toBe("5");
  });

  it("shows validation message when required fields are incomplete", async () => {
    renderWithProviders(<QuickEntryScreen />);

    await waitFor(() => {
      expect(screen.getAllByText("餐饮").length).toBeGreaterThan(0);
    });

    fireEvent.press(screen.getByLabelText("清空金额"));
    fireEvent.press(screen.getByLabelText("保存本次记账"));

    await waitFor(() => {
      expect(screen.getByText("请先补全分类和金额")).toBeTruthy();
    });

    expect(mockCreateTransaction).not.toHaveBeenCalled();
  });
});
