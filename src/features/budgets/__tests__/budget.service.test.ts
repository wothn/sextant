import {
  listCurrentMonthBudgetProgress,
  upsertMonthlyBudget,
} from "@/src/features/budgets/budget.service";
import { createMockDb } from "@/src/test/mock-db";
import { mockGetDb, resetMockDbClient } from "@/src/test/mock-db-client";

jest.mock("@/src/db/client", () => require("@/src/test/mock-db-client").mockDbClientModule);

describe("budget.service", () => {
  beforeEach(() => {
    resetMockDbClient();
    jest.useFakeTimers().setSystemTime(new Date("2026-03-06T08:00:00.000Z"));
  });

  it("updates an existing monthly budget for the current month", async () => {
    const db = createMockDb();
    db.getFirstAsync.mockResolvedValueOnce({ id: "budget-1" });
    mockGetDb.mockResolvedValueOnce(db);

    const result = await upsertMonthlyBudget("cat-1", 500, 0.9);

    expect(result).toBe("budget-1");
    expect(db.runAsync).toHaveBeenCalledWith(
      "UPDATE budgets SET amount = ?, alertThreshold = ?, isActive = 1 WHERE id = ?",
      [500, 0.9, "budget-1"],
    );
  });

  it("inserts a new monthly budget when none exists", async () => {
    const db = createMockDb();
    const monthStart = new Date(2026, 2, 1).getTime();
    db.getFirstAsync.mockResolvedValueOnce(null);
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000000000);
    jest.spyOn(Math, "random").mockReturnValue(0.5);

    const result = await upsertMonthlyBudget("cat-1", 888, 0.8);

    expect(result).toBe("1700000000000-8");
    expect(db.runAsync).toHaveBeenCalledWith(
      "INSERT INTO budgets (id, categoryId, amount, period, startDate, alertThreshold, isActive) VALUES (?, ?, ?, ?, ?, ?, 1)",
      ["1700000000000-8", "cat-1", 888, "monthly", monthStart, 0.8],
    );
  });

  it("rejects invalid budget inputs before hitting the database", async () => {
    await expect(upsertMonthlyBudget("cat-1", 0, 0.8)).rejects.toThrow("预算金额必须为正数");
    await expect(upsertMonthlyBudget("", 100, 0.8)).rejects.toThrow("分类不能为空");
    await expect(upsertMonthlyBudget("cat-1", 100, 1.2)).rejects.toThrow(
      "预警阈值需要在 0 到 1 之间",
    );
    expect(mockGetDb).not.toHaveBeenCalled();
  });

  it("lists current month budget progress with date boundaries", async () => {
    const db = createMockDb();
    const monthStart = new Date(2026, 2, 1).getTime();
    const nextMonthStart = new Date(2026, 3, 1).getTime();
    const rows = [{ categoryId: "cat-1", expenseAmount: 50 }];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listCurrentMonthBudgetProgress();

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(
      expect.stringContaining("WHERE b.period = 'monthly'"),
      [monthStart, nextMonthStart, monthStart],
    );
  });
});
