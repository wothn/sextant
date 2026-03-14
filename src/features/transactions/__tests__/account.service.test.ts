import { createAccount, listAccounts } from "@/src/features/transactions/account.service";
import { createMockDb } from "@/src/test/mock-db";
import { mockGetDb, resetMockDbClient } from "@/src/test/mock-db-client";

jest.mock("@/src/db/client", () => require("@/src/test/mock-db-client").mockDbClientModule);

describe("account.service", () => {
  beforeEach(() => {
    resetMockDbClient();
  });

  it("lists active accounts ordered by creation time", async () => {
    const db = createMockDb();
    const rows = [{ id: "a1", name: "现金" }];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listAccounts();

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(
      "SELECT * FROM accounts WHERE isActive = 1 ORDER BY createdAt ASC",
    );
  });

  it("creates an account with trimmed name and default currency", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000000000);
    jest.spyOn(Math, "random").mockReturnValue(0.5);

    const accountId = await createAccount("  招商银行卡  ", "bank");

    expect(accountId).toBe("1700000000000-8");
    expect(db.runAsync).toHaveBeenCalledWith(
      "INSERT INTO accounts (id, name, type, balance, currency, isActive, createdAt, updatedAt) VALUES (?, ?, ?, 0, ?, 1, ?, ?)",
      ["1700000000000-8", "招商银行卡", "bank", "CNY", 1700000000000, 1700000000000],
    );
  });

  it("rejects empty account names before hitting the database", async () => {
    await expect(createAccount("   ", "cash")).rejects.toThrow("账户名称不能为空");
    expect(mockGetDb).not.toHaveBeenCalled();
  });
});
