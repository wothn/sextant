import { fireEvent, screen, waitFor } from "@testing-library/react-native";

import HomeScreen from "@/app/(tabs)/index";
import { renderWithProviders } from "@/src/test/render";

const mockGetTodaySummary = jest.fn();
const mockGetCurrentMonthSummary = jest.fn();
const mockListTransactionGroupsByDay = jest.fn();

jest.mock("expo-router", () => ({
  router: { push: jest.fn() },
}));

jest.mock("@/src/features/transactions/transaction.service", () => ({
  getTodaySummary: () => mockGetTodaySummary(),
  getCurrentMonthSummary: () => mockGetCurrentMonthSummary(),
  listTransactionGroupsByDay: () => mockListTransactionGroupsByDay(),
}));

describe("HomeScreen", () => {
  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T08:00:00.000Z"));
    mockGetTodaySummary.mockResolvedValue({
      income: 120,
      expense: 35,
      net: 85,
      topCategories: [{ categoryId: "cat-1", categoryName: "餐饮", amount: 35, share: 1 }],
    });
    mockGetCurrentMonthSummary.mockResolvedValue({ income: 3000, expense: 800, net: 2200 });
    mockListTransactionGroupsByDay.mockResolvedValue([
      {
        dateKey: "2026-03-08",
        totalIncome: 120,
        totalExpense: 63,
        transactionCount: 3,
        transactions: [
          {
            id: "tx-1",
            amount: 35,
            type: "expense",
            description: "午饭",
            transactionDate: new Date("2026-03-08T04:00:00.000Z").getTime(),
            categoryName: "餐饮",
            categoryColor: "#FF7043",
            categoryIcon: "food",
            paymentMethodName: "现金",
            includeInSpending: 1,
          },
          {
            id: "tx-2",
            amount: 28,
            type: "expense",
            description: "",
            transactionDate: new Date("2026-03-08T03:00:00.000Z").getTime(),
            categoryName: "交通",
            categoryColor: "#42A5F5",
            categoryIcon: "bus",
            paymentMethodName: "银行卡",
            includeInSpending: 1,
          },
          {
            id: "tx-3",
            amount: 120,
            type: "income",
            description: "报销",
            transactionDate: new Date("2026-03-08T01:00:00.000Z").getTime(),
            categoryName: "兼职",
            categoryColor: "#66BB6A",
            categoryIcon: "cash",
            paymentMethodName: "银行卡",
            includeInSpending: 1,
          },
        ],
      },
    ]);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders dashboard summaries and grouped daily transactions", async () => {
    renderWithProviders(<HomeScreen />);

    await waitFor(() => {
      expect(screen.getByText("今天已经花了")).toBeTruthy();
      expect(screen.getByText("¥35.00")).toBeTruthy();
      expect(screen.getByText("本月支出")).toBeTruthy();
      expect(screen.getByText("本月收入")).toBeTruthy();
      expect(screen.getByText("¥3000.00")).toBeTruthy();
      expect(screen.getAllByText(/3月8日 周日/).length).toBeGreaterThan(0);
      expect(screen.getByText("餐饮")).toBeTruthy();
      expect(screen.getByText("午饭")).toBeTruthy();
      expect(screen.getAllByText("银行卡").length).toBeGreaterThan(0);
      expect(screen.getByText("交通")).toBeTruthy();
      expect(screen.queryByText("本月收支")).toBeNull();

      expect(screen.queryByText(/今日收入/)).toBeNull();
      expect(screen.queryByText("本月概览")).toBeNull();
      expect(screen.queryByText("按日流水")).toBeNull();
      expect(screen.queryByText("无备注")).toBeNull();
    });
  });

  it("opens a bottom sheet with full transaction details", async () => {
    renderWithProviders(<HomeScreen />);

    await waitFor(() => {
      expect(screen.getByLabelText("交通 交易详情")).toBeTruthy();
    });

    fireEvent.press(screen.getByLabelText("交通 交易详情"));

    await waitFor(() => {
      expect(screen.getByText("交易详情")).toBeTruthy();
      expect(screen.getByText("备注")).toBeTruthy();
      expect(screen.getByText("未填写")).toBeTruthy();
      expect(screen.getByText("支付方式")).toBeTruthy();
      expect(screen.getAllByText("银行卡").length).toBeGreaterThan(0);
    });

    fireEvent.press(screen.getByText("关闭"));

    await waitFor(() => {
      expect(screen.queryByText("交易详情")).toBeNull();
    });
  });

  it("shows an error message and retries loading", async () => {
    mockGetTodaySummary.mockRejectedValueOnce(new Error("加载失败")).mockResolvedValueOnce({
      income: 120,
      expense: 35,
      net: 85,
      topCategories: [{ categoryId: "cat-1", categoryName: "餐饮", amount: 35, share: 1 }],
    });
    mockGetCurrentMonthSummary
      .mockRejectedValueOnce(new Error("加载失败"))
      .mockResolvedValueOnce({ income: 3000, expense: 800, net: 2200 });
    mockListTransactionGroupsByDay
      .mockRejectedValueOnce(new Error("加载失败"))
      .mockResolvedValueOnce([]);

    renderWithProviders(<HomeScreen />);

    await waitFor(() => {
      expect(screen.getByText("数据加载失败")).toBeTruthy();
      expect(screen.getByText("加载失败")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("重试"));

    await waitFor(() => {
      expect(screen.queryByText("数据加载失败")).toBeNull();
      expect(screen.getByText("今天已经花了")).toBeTruthy();
    });
  });

  it("shows the empty state when no grouped transactions are available", async () => {
    mockListTransactionGroupsByDay.mockResolvedValueOnce([]);

    renderWithProviders(<HomeScreen />);

    await waitFor(() => {
      expect(screen.getByText("还没有记录")).toBeTruthy();
      expect(screen.getByText("记一笔后，这里会按日期排好最近的明细。")).toBeTruthy();
    });
  });
});
