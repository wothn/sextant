import {
  createAccount,
  listAccounts,
  setAccountActive,
  updateAccount,
} from "@/src/features/transactions/account.service";
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

  it("lists all accounts when includeInactive is enabled", async () => {
    const db = createMockDb();
    const rows = [
      { id: "a1", name: "现金" },
      { id: "a2", name: "旧卡" },
    ];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listAccounts({ includeInactive: true });

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(
      "SELECT * FROM accounts ORDER BY isActive DESC, createdAt ASC",
    );
  });

  it("creates an account with trimmed name, initial balance, and default currency", async () => {
    const db = createMockDb();
    db.getFirstAsync.mockResolvedValueOnce(null);
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000000000);
    jest.spyOn(Math, "random").mockReturnValue(0.5);

    const accountId = await createAccount({
      name: "  招商银行卡  ",
      type: "debit",
      initialBalance: 2888.6,
    });

    expect(accountId).toBe("1700000000000-8");
    expect(db.getFirstAsync).toHaveBeenCalledWith(
      "SELECT id FROM accounts WHERE isActive = 1 AND LOWER(name) = LOWER(?) LIMIT 1",
      ["招商银行卡"],
    );
    expect(db.runAsync).toHaveBeenCalledWith(
      "INSERT INTO accounts (id, name, type, balance, currency, isActive, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
      ["1700000000000-8", "招商银行卡", "debit", 2888.6, "CNY", 1700000000000, 1700000000000],
    );
  });

  it("rejects empty account names before hitting the database", async () => {
    await expect(
      createAccount({
        name: "   ",
        type: "cash",
      }),
    ).rejects.toThrow("账户名称不能为空");
    expect(mockGetDb).not.toHaveBeenCalled();
  });

  it("rejects creating a duplicate active account name", async () => {
    const db = createMockDb();
    db.getFirstAsync.mockResolvedValueOnce({ id: "dup-1" });
    mockGetDb.mockResolvedValueOnce(db);

    await expect(
      createAccount({
        name: "支付宝",
        type: "ewallet",
      }),
    ).rejects.toThrow("已存在同名启用账户");

    expect(db.runAsync).not.toHaveBeenCalled();
  });

  it("updates account metadata after validating the name", async () => {
    const db = createMockDb();
    db.getFirstAsync.mockResolvedValueOnce(null).mockResolvedValueOnce({
      id: "acc-1",
      name: "旧名称",
    });
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000001234);

    await updateAccount("acc-1", {
      name: "  交通卡  ",
      type: "credit",
    });

    expect(db.getFirstAsync).toHaveBeenNthCalledWith(
      1,
      "SELECT id FROM accounts WHERE isActive = 1 AND LOWER(name) = LOWER(?) AND id != ? LIMIT 1",
      ["交通卡", "acc-1"],
    );
    expect(db.runAsync).toHaveBeenCalledWith(
      "UPDATE accounts SET name = ?, type = ?, updatedAt = ? WHERE id = ?",
      ["交通卡", "credit", 1700000001234, "acc-1"],
    );
  });

  it("archives an account by updating its active status", async () => {
    const db = createMockDb();
    db.getFirstAsync.mockResolvedValueOnce({
      id: "acc-1",
      name: "现金",
    });
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000004321);

    await setAccountActive("acc-1", false);

    expect(db.runAsync).toHaveBeenCalledWith(
      "UPDATE accounts SET isActive = ?, updatedAt = ? WHERE id = ?",
      [0, 1700000004321, "acc-1"],
    );
  });

  it("prevents restoring an account when the active name already exists", async () => {
    const db = createMockDb();
    db.getFirstAsync
      .mockResolvedValueOnce({
        id: "acc-1",
        name: "现金",
      })
      .mockResolvedValueOnce({ id: "acc-2" });
    mockGetDb.mockResolvedValueOnce(db);

    await expect(setAccountActive("acc-1", true)).rejects.toThrow(
      "存在同名启用账户，请先修改名称再恢复",
    );

    expect(db.runAsync).not.toHaveBeenCalled();
  });
});
