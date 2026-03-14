import { fireEvent, screen, waitFor } from "@testing-library/react-native";

import AnalyticsScreen from "@/app/(tabs)/analytics";
import { renderWithProviders } from "@/src/test/render";

const mockGetCurrentMonthSummary = jest.fn();
const mockGetMonthlyTrend = jest.fn();
const mockGetCurrentMonthCategoryBreakdown = jest.fn();
const mockGetCurrentMonthAccountSummaries = jest.fn();
const mockListCurrentMonthBudgetProgress = jest.fn();
const mockTrendLineChart = jest.fn(() => null);

interface TrendLineChartProps {
  points: Array<{ label: string; value: number }>;
  width?: number;
  height?: number;
  color?: string;
  fillColor?: string;
}

jest.mock("@/src/components/charts/DonutChart", () => ({
  DonutChart: () => null,
}));

jest.mock("@/src/components/charts/TrendLineChart", () => ({
  TrendLineChart: (props: TrendLineChartProps) => {
    mockTrendLineChart(props);
    return null;
  },
}));

jest.mock("@/src/features/transactions/transaction.service", () => ({
  getCurrentMonthSummary: () => mockGetCurrentMonthSummary(),
  getMonthlyTrend: () => mockGetMonthlyTrend(),
  getCurrentMonthCategoryBreakdown: () => mockGetCurrentMonthCategoryBreakdown(),
  getCurrentMonthAccountSummaries: () => mockGetCurrentMonthAccountSummaries(),
}));

jest.mock("@/src/features/budgets/budget.service", () => ({
  listCurrentMonthBudgetProgress: () => mockListCurrentMonthBudgetProgress(),
}));

describe("AnalyticsScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("renders report sections and empty budget hint", async () => {
    mockGetCurrentMonthSummary.mockResolvedValueOnce({ income: 3000, expense: 800, net: 2200 });
    mockGetMonthlyTrend.mockResolvedValueOnce([
      { monthKey: "2026-01", label: "1月", income: 2500, expense: 1200, net: 1300 },
      { monthKey: "2026-02", label: "2月", income: 2600, expense: 1600, net: 1000 },
      { monthKey: "2026-03", label: "3月", income: 3000, expense: 800, net: 2200 },
    ]);
    mockGetCurrentMonthCategoryBreakdown.mockResolvedValueOnce([
      {
        categoryId: "cat-1",
        categoryName: "餐饮",
        categoryColor: "#FF7043",
        amount: 500,
        transactionCount: 4,
        share: 0.625,
      },
    ]);
    mockGetCurrentMonthAccountSummaries.mockResolvedValueOnce([
      {
        accountId: "acc-1",
        accountName: "现金",
        accountType: "cash",
        balance: 1800,
        income: 2000,
        expense: 200,
        net: 1800,
        transactionCount: 5,
      },
    ]);
    mockListCurrentMonthBudgetProgress.mockResolvedValueOnce([]);

    renderWithProviders(<AnalyticsScreen />);

    await waitFor(() => {
      expect(screen.getByText("Analytics")).toBeTruthy();
      expect(screen.getByText("本月资金轮廓")).toBeTruthy();
      expect(screen.getByText("本月收入")).toBeTruthy();
      expect(screen.getByText("¥3000.00")).toBeTruthy();
      expect(screen.getByText("近 6 月趋势")).toBeTruthy();
      const topSpending = screen.getByTestId("top-spending-category");
      expect(topSpending.props.numberOfLines).toBe(1);
      expect(topSpending.props.ellipsizeMode).toBe("tail");
      expect(screen.getByText("支出结构")).toBeTruthy();
      expect(screen.getByText("账户视图")).toBeTruthy();
      expect(screen.getByText("现金")).toBeTruthy();
      expect(screen.getByText("当前还没有预算，先去设置页添加。")).toBeTruthy();
    });
  });

  it("renders budget progress cards when budgets exist", async () => {
    mockGetCurrentMonthSummary.mockResolvedValueOnce({ income: 3000, expense: 1200, net: 1800 });
    mockGetMonthlyTrend.mockResolvedValueOnce([]);
    mockGetCurrentMonthCategoryBreakdown.mockResolvedValueOnce([]);
    mockGetCurrentMonthAccountSummaries.mockResolvedValueOnce([]);
    mockListCurrentMonthBudgetProgress.mockResolvedValueOnce([
      {
        categoryId: "cat-1",
        categoryName: "餐饮",
        budgetAmount: 1500,
        alertThreshold: 0.8,
        expenseAmount: 900,
      },
    ]);

    renderWithProviders(<AnalyticsScreen />);

    await waitFor(() => {
      expect(screen.getByText("餐饮")).toBeTruthy();
      expect(screen.getByText("¥900.00 / ¥1500.00")).toBeTruthy();
      expect(screen.getByText("剩余 ¥600.00")).toBeTruthy();
    });
  });

  it("sizes trend chart to container width", async () => {
    mockGetCurrentMonthSummary.mockResolvedValueOnce({ income: 3000, expense: 800, net: 2200 });
    mockGetMonthlyTrend.mockResolvedValueOnce([
      { monthKey: "2026-01", label: "1月", income: 2500, expense: 1200, net: 1300 },
      { monthKey: "2026-02", label: "2月", income: 2600, expense: 1600, net: 1000 },
    ]);
    mockGetCurrentMonthCategoryBreakdown.mockResolvedValueOnce([]);
    mockGetCurrentMonthAccountSummaries.mockResolvedValueOnce([]);
    mockListCurrentMonthBudgetProgress.mockResolvedValueOnce([]);

    renderWithProviders(<AnalyticsScreen />);

    const container = await waitFor(() => screen.getByTestId("trend-chart-container"));
    fireEvent(container, "layout", {
      nativeEvent: { layout: { width: 280, height: 200, x: 0, y: 0 } },
    });

    await waitFor(() => {
      expect(mockTrendLineChart).toHaveBeenCalled();
    });

    const lastCallIndex = mockTrendLineChart.mock.calls.length - 1;
    const lastProps = mockTrendLineChart.mock.calls[lastCallIndex]?.[0];
    expect(lastProps?.width).toBe(280);
  });
});
