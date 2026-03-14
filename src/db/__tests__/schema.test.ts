import { initializeSchema, seedDefaults } from "@/src/db/schema";
import { createMockDb } from "@/src/test/mock-db";

describe("schema", () => {
  it("initializes all tables and indexes", async () => {
    const db = createMockDb();

    await initializeSchema(db as never);

    expect(db.execAsync).toHaveBeenCalledTimes(1);
    expect(db.execAsync.mock.calls[0][0]).toContain("CREATE TABLE IF NOT EXISTS accounts");
    expect(db.execAsync.mock.calls[0][0]).toContain("CREATE TABLE IF NOT EXISTS transactions");
    expect(db.execAsync.mock.calls[0][0]).toContain(
      "CREATE INDEX IF NOT EXISTS idx_budgets_period",
    );
  });

  it("seeds default account and categories when database is empty", async () => {
    const db = createMockDb();
    db.getFirstAsync.mockResolvedValueOnce({ count: 0 }).mockResolvedValueOnce({ count: 0 });

    await seedDefaults(db as never);

    expect(db.runAsync).toHaveBeenCalledTimes(6);
    expect(db.runAsync.mock.calls[0][0]).toContain("INSERT INTO accounts");
    expect(
      db.runAsync.mock.calls.slice(1).every(([sql]) => sql.includes("INSERT INTO categories")),
    ).toBe(true);
  });

  it("does not reseed defaults when records already exist", async () => {
    const db = createMockDb();
    db.getFirstAsync.mockResolvedValueOnce({ count: 1 }).mockResolvedValueOnce({ count: 5 });

    await seedDefaults(db as never);

    expect(db.runAsync).not.toHaveBeenCalled();
  });
});
