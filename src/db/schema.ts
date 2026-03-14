import type { SQLiteDatabase } from "expo-sqlite";
import { generateId } from "@/src/lib/id";

export async function initializeSchema(db: SQLiteDatabase) {
  await db.execAsync(`
    PRAGMA journal_mode = WAL;

    CREATE TABLE IF NOT EXISTS accounts (
      id TEXT PRIMARY KEY NOT NULL,
      name TEXT NOT NULL,
      type TEXT NOT NULL,
      balance REAL NOT NULL DEFAULT 0,
      currency TEXT NOT NULL DEFAULT 'CNY',
      isActive INTEGER NOT NULL DEFAULT 1,
      createdAt INTEGER NOT NULL,
      updatedAt INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS categories (
      id TEXT PRIMARY KEY NOT NULL,
      name TEXT NOT NULL,
      type TEXT NOT NULL,
      icon TEXT NOT NULL,
      color TEXT NOT NULL,
      isBuiltIn INTEGER NOT NULL DEFAULT 0,
      isActive INTEGER NOT NULL DEFAULT 1,
      createdAt INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS transactions (
      id TEXT PRIMARY KEY NOT NULL,
      accountId TEXT NOT NULL,
      categoryId TEXT,
      amount REAL NOT NULL,
      type TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      transactionDate INTEGER NOT NULL,
      createdAt INTEGER NOT NULL,
      updatedAt INTEGER NOT NULL,
      relatedAccountId TEXT,
      relatedTransactionId TEXT
    );

    CREATE TABLE IF NOT EXISTS tags (
      id TEXT PRIMARY KEY NOT NULL,
      name TEXT NOT NULL UNIQUE,
      color TEXT NOT NULL DEFAULT '#8E8E93',
      createdAt INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS transaction_tags (
      transactionId TEXT NOT NULL,
      tagId TEXT NOT NULL,
      PRIMARY KEY (transactionId, tagId)
    );

    CREATE TABLE IF NOT EXISTS budgets (
      id TEXT PRIMARY KEY NOT NULL,
      categoryId TEXT NOT NULL,
      amount REAL NOT NULL,
      period TEXT NOT NULL,
      startDate INTEGER NOT NULL,
      alertThreshold REAL NOT NULL DEFAULT 0.8,
      isActive INTEGER NOT NULL DEFAULT 1
    );

    CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transactionDate);
    CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(accountId);
    CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(categoryId);
    CREATE INDEX IF NOT EXISTS idx_budgets_period ON budgets(period, startDate);
  `);
}

export async function seedDefaults(db: SQLiteDatabase) {
  const now = Date.now();
  const accountCountRow = await db.getFirstAsync<{ count: number }>(
    "SELECT COUNT(*) as count FROM accounts",
  );

  if (!accountCountRow || accountCountRow.count === 0) {
    await db.runAsync(
      "INSERT INTO accounts (id, name, type, balance, currency, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
      [generateId(), "现金", "cash", 0, "CNY", now, now],
    );
  }

  const categoryCountRow = await db.getFirstAsync<{ count: number }>(
    "SELECT COUNT(*) as count FROM categories",
  );

  if (!categoryCountRow || categoryCountRow.count === 0) {
    const defaults = [
      ["餐饮", "expense", "food-fork-drink", "#FF7043"],
      ["交通", "expense", "bus", "#42A5F5"],
      ["购物", "expense", "shopping", "#AB47BC"],
      ["工资", "income", "cash", "#66BB6A"],
      ["兼职", "income", "briefcase", "#26A69A"],
    ] as const;

    for (const [name, type, icon, color] of defaults) {
      await db.runAsync(
        "INSERT INTO categories (id, name, type, icon, color, isBuiltIn, createdAt) VALUES (?, ?, ?, ?, ?, 1, ?)",
        [generateId(), name, type, icon, color, now],
      );
    }
  }
}
