import { initializeSchema, seedDefaults } from "@/src/db/schema";
import { createMockDb } from "@/src/test/mock-db";

describe("schema", () => {
  it("initializes all tables and indexes", async () => {
    const db = createMockDb();

    await initializeSchema(db as never);

    const sqlStatements = db.execAsync.mock.calls.map(([sql]) => sql);

    expect(sqlStatements[0]).toContain("CREATE TABLE IF NOT EXISTS accounts");
    expect(sqlStatements[0]).toContain("CREATE TABLE IF NOT EXISTS transactions");
    expect(sqlStatements[0]).toContain("CREATE INDEX IF NOT EXISTS idx_budgets_period");
    expect(sqlStatements.some((sql) => sql.includes("includeInSpending"))).toBe(true);
    expect(sqlStatements.some((sql) => sql.includes("idx_transactions_payment_method"))).toBe(true);
    expect(sqlStatements[0]).not.toContain("idx_transactions_payment_method");
  });

  it("creates the payment method index only after ensuring the column exists", async () => {
    const db = createMockDb();

    await initializeSchema(db as never);

    expect(db.execAsync).toHaveBeenNthCalledWith(
      db.execAsync.mock.calls.length,
      "CREATE INDEX IF NOT EXISTS idx_transactions_payment_method ON transactions(paymentMethodId);",
    );
  });

  it("seeds internal account, categories, and payment methods when database is empty", async () => {
    const db = createMockDb();
    db.getFirstAsync
      .mockResolvedValueOnce({ count: 0 })
      .mockResolvedValueOnce({ count: 0 })
      .mockResolvedValueOnce({ count: 0 });

    await seedDefaults(db as never);

    expect(db.runAsync).toHaveBeenCalledTimes(12);
    expect(db.runAsync.mock.calls[0][0]).toContain("INSERT INTO accounts");
    expect(
      db.runAsync.mock.calls.slice(1, 8).every(([sql]) => sql.includes("INSERT INTO categories")),
    ).toBe(true);
    expect(
      db.runAsync.mock.calls.slice(8).every(([sql]) => sql.includes("INSERT INTO payment_methods")),
    ).toBe(true);
  });

  it("does not reseed defaults when records already exist", async () => {
    const db = createMockDb();
    db.getFirstAsync
      .mockResolvedValueOnce({ count: 1 })
      .mockResolvedValueOnce({ count: 5 })
      .mockResolvedValueOnce({ count: 4 });

    await seedDefaults(db as never);

    expect(db.runAsync).not.toHaveBeenCalled();
  });
});
