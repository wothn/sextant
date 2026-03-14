import {
  createTransaction,
  createTransfer,
  getCurrentMonthAccountSummaries,
  getCurrentMonthCategoryBreakdown,
  getCurrentMonthSummary,
  getMonthlyTrend,
  getTodaySummary,
  listCategories,
  listTransactionGroupsByDay,
  listRecentTransactions,
} from "@/src/features/transactions/transaction.service";
import { createMockDb } from "@/src/test/mock-db";
import { mockGetDb, resetMockDbClient } from "@/src/test/mock-db-client";

jest.mock("@/src/db/client", () => require("@/src/test/mock-db-client").mockDbClientModule);

describe("transaction.service", () => {
  beforeEach(() => {
    resetMockDbClient();
  });

  it("lists categories by transaction type", async () => {
    const db = createMockDb();
    const rows = [{ id: "c1", name: "餐饮", type: "expense" }];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listCategories("expense");

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(
      "SELECT * FROM categories WHERE isActive = 1 AND type = ? ORDER BY isBuiltIn DESC, name ASC",
      ["expense"],
    );
  });

  it("creates an expense transaction and updates account balance in one transaction", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000000000);
    jest.spyOn(Math, "random").mockReturnValue(0.25);

    const transactionId = await createTransaction({
      accountId: "acc-1",
      categoryId: "cat-1",
      amount: 35.5,
      type: "expense",
      description: "午饭",
    });

    expect(transactionId).toBe("1700000000000-4");
    expect(db.withTransactionAsync).toHaveBeenCalledTimes(1);
    expect(db.runAsync).toHaveBeenNthCalledWith(
      1,
      "INSERT INTO transactions (id, accountId, categoryId, amount, type, description, transactionDate, createdAt, updatedAt, relatedAccountId, relatedTransactionId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
      [
        "1700000000000-4",
        "acc-1",
        "cat-1",
        35.5,
        "expense",
        "午饭",
        1700000000000,
        1700000000000,
        1700000000000,
      ],
    );
    expect(db.runAsync).toHaveBeenNthCalledWith(
      2,
      "UPDATE accounts SET balance = balance + ?, updatedAt = ? WHERE id = ?",
      [-35.5, 1700000000000, "acc-1"],
    );
  });

  it("rejects invalid transaction input before hitting the database", async () => {
    await expect(
      createTransaction({
        accountId: "",
        categoryId: "cat-1",
        amount: 12,
        type: "expense",
      }),
    ).rejects.toThrow("账户不能为空");

    await expect(
      createTransaction({
        accountId: "acc-1",
        categoryId: "cat-1",
        amount: 0,
        type: "income",
      }),
    ).rejects.toThrow("金额必须为正数");

    expect(mockGetDb).not.toHaveBeenCalled();
  });

  it("creates paired transfer transactions and updates both account balances", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000001000);
    const randomSpy = jest.spyOn(Math, "random");
    randomSpy.mockReturnValueOnce(0.125).mockReturnValueOnce(0.875);

    await createTransfer("from-1", "to-1", 88, "调拨");

    expect(db.withTransactionAsync).toHaveBeenCalledTimes(1);
    expect(db.runAsync).toHaveBeenNthCalledWith(
      1,
      "INSERT INTO transactions (id, accountId, categoryId, amount, type, description, transactionDate, createdAt, updatedAt, relatedAccountId, relatedTransactionId) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)",
      [
        "1700000001000-2",
        "from-1",
        88,
        "transfer",
        "调拨",
        1700000001000,
        1700000001000,
        1700000001000,
        "to-1",
        "1700000001000-e",
      ],
    );
    expect(db.runAsync).toHaveBeenNthCalledWith(
      2,
      "INSERT INTO transactions (id, accountId, categoryId, amount, type, description, transactionDate, createdAt, updatedAt, relatedAccountId, relatedTransactionId) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)",
      [
        "1700000001000-e",
        "to-1",
        88,
        "transfer",
        "调拨",
        1700000001000,
        1700000001000,
        1700000001000,
        "from-1",
        "1700000001000-2",
      ],
    );
    expect(db.runAsync).toHaveBeenNthCalledWith(
      3,
      "UPDATE accounts SET balance = balance - ?, updatedAt = ? WHERE id = ?",
      [88, 1700000001000, "from-1"],
    );
    expect(db.runAsync).toHaveBeenNthCalledWith(
      4,
      "UPDATE accounts SET balance = balance + ?, updatedAt = ? WHERE id = ?",
      [88, 1700000001000, "to-1"],
    );
  });

  it("rejects invalid transfer input before hitting the database", async () => {
    await expect(createTransfer("acc-1", "acc-1", 50)).rejects.toThrow(
      "转入转出账户不能相同",
    );
    await expect(createTransfer("acc-1", "acc-2", -10)).rejects.toThrow("金额必须为正数");
    expect(mockGetDb).not.toHaveBeenCalled();
  });

  it("lists recent transactions with the provided limit", async () => {
    const db = createMockDb();
    const rows = [{ id: "tx-1", amount: 12 }];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listRecentTransactions(5);

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(
      expect.stringContaining("ORDER BY t.transactionDate DESC"),
      [5],
    );
  });

  it("computes the current month summary from income and expense totals", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.useFakeTimers().setSystemTime(new Date("2026-03-06T08:00:00.000Z"));
    db.getFirstAsync.mockResolvedValueOnce({ income: 3200, expense: 1250 });

    const result = await getCurrentMonthSummary();

    expect(result).toEqual({ income: 3200, expense: 1250, net: 1950 });
    expect(db.getFirstAsync).toHaveBeenCalledTimes(1);
  });

  it("computes today's summary with top categories", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T08:00:00.000Z"));
    db.getFirstAsync.mockResolvedValueOnce({ income: 500, expense: 180 });
    db.getAllAsync.mockResolvedValueOnce([
      { categoryId: "cat-1", categoryName: "餐饮", amount: 100 },
      { categoryId: "cat-2", categoryName: "交通", amount: 80 },
    ]);

    const result = await getTodaySummary();

    expect(result).toEqual({
      income: 500,
      expense: 180,
      net: 320,
      topCategories: [
        { categoryId: "cat-1", categoryName: "餐饮", amount: 100, share: 100 / 180 },
        { categoryId: "cat-2", categoryName: "交通", amount: 80, share: 80 / 180 },
      ],
    });
  });

  it("groups recent transactions by day", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T08:00:00.000Z"));
    db.getAllAsync.mockResolvedValueOnce([
      {
        id: "tx-1",
        amount: 35,
        type: "expense",
        description: "午饭",
        transactionDate: new Date("2026-03-08T03:00:00.000Z").getTime(),
        categoryName: "餐饮",
        categoryColor: "#FF7043",
        categoryIcon: "food",
        accountName: "现金",
      },
      {
        id: "tx-2",
        amount: 120,
        type: "income",
        description: "报销",
        transactionDate: new Date("2026-03-08T01:00:00.000Z").getTime(),
        categoryName: "兼职",
        categoryColor: "#66BB6A",
        categoryIcon: "cash",
        accountName: "银行卡",
      },
      {
        id: "tx-3",
        amount: 20,
        type: "expense",
        description: "早餐",
        transactionDate: new Date("2026-03-07T01:00:00.000Z").getTime(),
        categoryName: "餐饮",
        categoryColor: "#FF7043",
        categoryIcon: "food",
        accountName: "现金",
      },
    ]);

    const result = await listTransactionGroupsByDay(7, 20);

    expect(result).toHaveLength(2);
    expect(result[0]).toMatchObject({
      dateKey: "2026-03-08",
      totalExpense: 35,
      totalIncome: 120,
      transactionCount: 2,
    });
    expect(result[1]).toMatchObject({
      dateKey: "2026-03-07",
      totalExpense: 20,
      totalIncome: 0,
      transactionCount: 1,
    });
  });

  it("fills missing months when generating trend data", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T08:00:00.000Z"));
    db.getAllAsync.mockResolvedValueOnce([
      { monthKey: "2025-11", income: 1000, expense: 400 },
      { monthKey: "2026-01", income: 900, expense: 1200 },
      { monthKey: "2026-03", income: 1500, expense: 600 },
    ]);

    const result = await getMonthlyTrend(5);

    expect(result).toEqual([
      { monthKey: "2025-11", label: "11月", income: 1000, expense: 400, net: 600 },
      { monthKey: "2025-12", label: "12月", income: 0, expense: 0, net: 0 },
      { monthKey: "2026-01", label: "1月", income: 900, expense: 1200, net: -300 },
      { monthKey: "2026-02", label: "2月", income: 0, expense: 0, net: 0 },
      { monthKey: "2026-03", label: "3月", income: 1500, expense: 600, net: 900 },
    ]);
  });

  it("computes category expense breakdown with shares", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T08:00:00.000Z"));
    db.getAllAsync.mockResolvedValueOnce([
      {
        categoryId: "cat-1",
        categoryName: "餐饮",
        categoryColor: "#FF7043",
        amount: 300,
        transactionCount: 6,
      },
      {
        categoryId: "cat-2",
        categoryName: "交通",
        categoryColor: "#42A5F5",
        amount: 200,
        transactionCount: 4,
      },
    ]);

    const result = await getCurrentMonthCategoryBreakdown();

    expect(result).toEqual([
      {
        categoryId: "cat-1",
        categoryName: "餐饮",
        categoryColor: "#FF7043",
        amount: 300,
        transactionCount: 6,
        share: 0.6,
      },
      {
        categoryId: "cat-2",
        categoryName: "交通",
        categoryColor: "#42A5F5",
        amount: 200,
        transactionCount: 4,
        share: 0.4,
      },
    ]);
  });

  it("lists current month account summaries", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T08:00:00.000Z"));
    db.getAllAsync.mockResolvedValueOnce([
      {
        accountId: "acc-1",
        accountName: "现金",
        accountType: "cash",
        balance: 560,
        income: 800,
        expense: 240,
        net: 560,
        transactionCount: 8,
      },
    ]);

    const result = await getCurrentMonthAccountSummaries();

    expect(result).toEqual([
      {
        accountId: "acc-1",
        accountName: "现金",
        accountType: "cash",
        balance: 560,
        income: 800,
        expense: 240,
        net: 560,
        transactionCount: 8,
      },
    ]);
  });
});
