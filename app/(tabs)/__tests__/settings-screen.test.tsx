import * as Sharing from "expo-sharing";
import { fireEvent, screen, waitFor } from "@testing-library/react-native";

import SettingsScreen from "@/app/(tabs)/settings";
import { renderWithProviders } from "@/src/test/render";

const mockListCategories = jest.fn();
const mockUpsertMonthlyBudget = jest.fn();
const mockExportTransactionsCsv = jest.fn();

jest.mock("expo-sharing", () => ({
  isAvailableAsync: jest.fn(async () => true),
  shareAsync: jest.fn(async () => undefined),
}));

jest.mock("@/src/features/transactions/transaction.service", () => ({
  listCategories: (...args: unknown[]) => mockListCategories(...args),
}));

jest.mock("@/src/features/budgets/budget.service", () => ({
  upsertMonthlyBudget: (...args: unknown[]) => mockUpsertMonthlyBudget(...args),
}));

jest.mock("@/src/lib/backup/backup.service", () => ({
  exportTransactionsCsv: () => mockExportTransactionsCsv(),
}));

describe("SettingsScreen", () => {
  beforeEach(() => {
    mockListCategories.mockResolvedValue([
      { id: "cat-1", name: "餐饮", type: "expense", includeInSpending: 1 },
      { id: "cat-2", name: "交通", type: "expense", includeInSpending: 1 },
    ]);
    mockUpsertMonthlyBudget.mockResolvedValue("budget-1");
    mockExportTransactionsCsv.mockResolvedValue("file:///documents/export.csv");
  });

  it("renders categories and validates empty budget input", async () => {
    renderWithProviders(<SettingsScreen />);

    await waitFor(() => {
      expect(screen.getByText("本月预算设置")).toBeTruthy();
      expect(screen.getByText(/当前分类：/)).toBeTruthy();
      expect(screen.getByText("餐饮")).toBeTruthy();
      expect(screen.getByText("交通")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("保存预算"));

    await waitFor(() => {
      expect(screen.getByText("请输入有效预算金额")).toBeTruthy();
    });
  }, 10000);

  it("exports csv and triggers native sharing when available", async () => {
    renderWithProviders(<SettingsScreen />);

    await waitFor(() => {
      expect(screen.getByText("导出交易 CSV")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("导出交易 CSV"));

    await waitFor(() => {
      expect(mockExportTransactionsCsv).toHaveBeenCalledTimes(1);
      expect(Sharing.isAvailableAsync).toHaveBeenCalledTimes(1);
      expect(Sharing.shareAsync).toHaveBeenCalledWith("file:///documents/export.csv");
      expect(screen.getByText("导出成功：file:///documents/export.csv")).toBeTruthy();
    });
  });

  it("saves a valid monthly budget", async () => {
    renderWithProviders(<SettingsScreen />);

    await waitFor(() => {
      expect(screen.getByText("餐饮")).toBeTruthy();
    });

    fireEvent.changeText(screen.getByLabelText("预算金额"), "256.8");
    fireEvent.press(screen.getByText("保存预算"));

    await waitFor(() => {
      expect(mockUpsertMonthlyBudget).toHaveBeenCalledWith("cat-1", 256.8, 0.8);
      expect(screen.getByText("预算已保存：餐饮 ¥256.80")).toBeTruthy();
    });
  });
});
