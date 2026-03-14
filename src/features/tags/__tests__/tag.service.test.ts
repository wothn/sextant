import { bindTagsToTransaction, createTag, listTags } from "@/src/features/tags/tag.service";
import { createMockDb } from "@/src/test/mock-db";
import { mockGetDb, resetMockDbClient } from "@/src/test/mock-db-client";

jest.mock("@/src/db/client", () => require("@/src/test/mock-db-client").mockDbClientModule);

describe("tag.service", () => {
  beforeEach(() => {
    resetMockDbClient();
  });

  it("lists tags ordered by name", async () => {
    const db = createMockDb();
    const rows = [{ id: "t1", name: "通勤", color: "#fff" }];
    db.getAllAsync.mockResolvedValueOnce(rows);
    mockGetDb.mockResolvedValueOnce(db);

    const result = await listTags();

    expect(result).toEqual(rows);
    expect(db.getAllAsync).toHaveBeenCalledWith(
      "SELECT id, name, color FROM tags ORDER BY name ASC",
    );
  });

  it("creates a tag with trimmed name and default color", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000000000);
    jest.spyOn(Math, "random").mockReturnValue(0.5);

    const result = await createTag("  旅行  ");

    expect(result).toBe("1700000000000-8");
    expect(db.runAsync).toHaveBeenCalledWith(
      "INSERT INTO tags (id, name, color, createdAt) VALUES (?, ?, ?, ?)",
      ["1700000000000-8", "旅行", "#8E8E93", 1700000000000],
    );
  });

  it("rejects empty tag names before hitting the database", async () => {
    await expect(createTag("   ")).rejects.toThrow("标签名称不能为空");
    expect(mockGetDb).not.toHaveBeenCalled();
  });

  it("rejects empty transaction ids when binding tags", async () => {
    await expect(bindTagsToTransaction("   ", ["tag-1"])).rejects.toThrow("交易不能为空");
    expect(mockGetDb).not.toHaveBeenCalled();
  });

  it("rebinds tags in a transaction", async () => {
    const db = createMockDb();
    mockGetDb.mockResolvedValueOnce(db);

    await bindTagsToTransaction("tx-1", ["tag-1", "tag-2"]);

    expect(db.withTransactionAsync).toHaveBeenCalledTimes(1);
    expect(db.runAsync).toHaveBeenNthCalledWith(
      1,
      "DELETE FROM transaction_tags WHERE transactionId = ?",
      ["tx-1"],
    );
    expect(db.runAsync).toHaveBeenNthCalledWith(
      2,
      "INSERT INTO transaction_tags (transactionId, tagId) VALUES (?, ?)",
      ["tx-1", "tag-1"],
    );
    expect(db.runAsync).toHaveBeenNthCalledWith(
      3,
      "INSERT INTO transaction_tags (transactionId, tagId) VALUES (?, ?)",
      ["tx-1", "tag-2"],
    );
  });
});
