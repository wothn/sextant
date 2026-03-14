import {
  createTransaction,
  getCurrentMonthCategoryBreakdown,
  getCurrentMonthSummary,
  getMonthlyTrend,
  getTodaySummary,
  listCategories,
  listPaymentMethods,
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

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  it("lists categories by transaction type with spending categories first", async () => {
    const db = createMockDb();
    const rows = [{ id: "c1", name: "餐饮", type: "expense", includeInSpending: 1 }];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listCategories("expense");

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(
      expect.stringContaining("ORDER BY includeInSpending DESC, isBuiltIn DESC, name ASC"),
      ["expense"],
    );
  });

  it("lists active payment methods", async () => {
    const db = createMockDb();
    const rows = [{ id: "pm-1", name: "现金" }];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listPaymentMethods();

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(expect.stringContaining("FROM payment_methods"));
  });

  it("creates a transaction with the resolved system account and optional payment method", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    db.getFirstAsync.mockResolvedValueOnce({ id: "system-account" });
    jest.spyOn(Date, "now").mockReturnValue(1700000000000);
    jest.spyOn(Math, "random").mockReturnValue(0.25);

    const transactionId = await createTransaction({
      categoryId: "cat-1",
      paymentMethodId: "pm-1",
      amount: 35.5,
      type: "expense",
      description: "午饭",
    });

    expect(transactionId).toBe("1700000000000-4");
    expect(db.getFirstAsync).toHaveBeenCalledWith(
      "SELECT id FROM accounts ORDER BY createdAt ASC LIMIT 1",
    );
    expect(db.runAsync).toHaveBeenCalledWith(expect.stringContaining("paymentMethodId"), [
      "1700000000000-4",
      "system-account",
      "cat-1",
      "pm-1",
      35.5,
      "expense",
      "午饭",
      1700000000000,
      1700000000000,
      1700000000000,
    ]);
  });

  it("creates the internal system account when none exists", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    db.getFirstAsync.mockResolvedValueOnce(null);
    jest.spyOn(Date, "now").mockReturnValue(1700000001000);
    const randomSpy = jest.spyOn(Math, "random");
    randomSpy.mockReturnValueOnce(0.125).mockReturnValueOnce(0.875);

    await createTransaction({
      categoryId: "cat-1",
      amount: 20,
      type: "expense",
    });

    expect(db.runAsync).toHaveBeenNthCalledWith(
      1,
      "INSERT INTO accounts (id, name, type, balance, currency, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ["1700000001000-2", "系统账户", "cash", 0, "CNY", 1700000001000, 1700000001000],
    );
    expect(db.runAsync).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining("INSERT INTO transactions"),
      [
        "1700000001000-e",
        "1700000001000-2",
        "cat-1",
        null,
        20,
        "expense",
        "",
        1700000001000,
        1700000001000,
        1700000001000,
      ],
    );
  });

  it("rejects invalid transaction input before hitting the database", async () => {
    await expect(
      createTransaction({
        categoryId: "",
        amount: 12,
        type: "expense",
      }),
    ).rejects.toThrow("分类不能为空");

    await expect(
      createTransaction({
        categoryId: "cat-1",
        amount: 0,
        type: "income",
      }),
    ).rejects.toThrow("金额必须为正数");

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
      expect.stringContaining("pm.name as paymentMethodName"),
      [5],
    );
  });

  it("computes the current month summary using only spending categories", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.useFakeTimers().setSystemTime(new Date("2026-03-06T08:00:00.000Z"));
    db.getFirstAsync.mockResolvedValueOnce({ income: 3200, expense: 1250 });

    const result = await getCurrentMonthSummary();

    expect(result).toEqual({ income: 3200, expense: 1250, net: 1950 });
    expect(db.getFirstAsync).toHaveBeenCalledWith(
      expect.stringContaining("COALESCE(c.includeInSpending, 1) = 1"),
      expect.any(Array),
    );
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

  it("groups recent transactions by day and skips excluded expenses in headers", async () => {
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
        paymentMethodName: "现金",
        includeInSpending: 1,
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
        paymentMethodName: "银行卡",
        includeInSpending: 1,
      },
      {
        id: "tx-3",
        amount: 88,
        type: "expense",
        description: "定投",
        transactionDate: new Date("2026-03-07T01:00:00.000Z").getTime(),
        categoryName: "基金定投",
        categoryColor: "#5C6BC0",
        categoryIcon: "chart-line",
        paymentMethodName: "支付宝",
        includeInSpending: 0,
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
      totalExpense: 0,
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
});
