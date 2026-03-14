import * as FileSystem from "expo-file-system/legacy";

import { getDb } from "@/src/db/client";
import { exportTransactionsCsv } from "@/src/lib/backup/backup.service";
import { createMockDb } from "@/src/test/mock-db";

jest.mock("expo-file-system/legacy", () => ({
  documentDirectory: "file:///documents/",
  EncodingType: {
    UTF8: "utf8",
  },
  writeAsStringAsync: jest.fn(async () => undefined),
}));

jest.mock("@/src/db/client", () => ({
  getDb: jest.fn(),
}));

describe("backup.service", () => {
  beforeEach(() => {
    jest.restoreAllMocks();
  });

  it("exports transactions to a CSV file with escaped descriptions", async () => {
    const db = createMockDb();
    db.getAllAsync.mockResolvedValueOnce([
      {
        transactionDate: new Date("2026-03-05T00:00:00.000Z").getTime(),
        type: "expense",
        amount: 25.5,
        paymentMethodName: "现金",
        categoryName: "餐饮",
        description: '午饭 "双拼"',
      },
    ]);
    (getDb as jest.Mock).mockResolvedValueOnce(db);
    jest.spyOn(Date, "now").mockReturnValue(1700000000000);

    const path = await exportTransactionsCsv();

    expect(path).toBe("file:///documents/sextant-transactions-1700000000000.csv");
    expect(FileSystem.writeAsStringAsync).toHaveBeenCalledWith(
      path,
      'date,type,amount,payment_method,category,description\n2026-03-05,expense,25.5,现金,餐饮,"午饭 ""双拼"""',
      { encoding: FileSystem.EncodingType.UTF8 },
    );
  });

  it("throws when the export directory is unavailable", async () => {
    const db = createMockDb();
    db.getAllAsync.mockResolvedValueOnce([]);
    (getDb as jest.Mock).mockResolvedValueOnce(db);
    const originalDirectory = FileSystem.documentDirectory;

    Object.defineProperty(FileSystem, "documentDirectory", {
      configurable: true,
      value: null,
    });

    await expect(exportTransactionsCsv()).rejects.toThrow("导出目录不可用");

    Object.defineProperty(FileSystem, "documentDirectory", {
      configurable: true,
      value: originalDirectory,
    });
  });
});
