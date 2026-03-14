import type { SQLiteDatabase } from "expo-sqlite";
import { generateId } from "@/src/lib/id";

const DEFAULT_PAYMENT_METHODS = [
  ["银行卡", "credit-card-outline", "#2563EB"],
  ["微信", "message-text-outline", "#1F9D66"],
  ["支付宝", "wallet-outline", "#0F9F9A"],
  ["现金", "cash-multiple", "#D97706"],
] as const;

const DEFAULT_CATEGORIES = [
  ["餐饮", "expense", "food-fork-drink", "#FF7043", 1],
  ["交通", "expense", "bus", "#42A5F5", 1],
  ["购物", "expense", "shopping", "#AB47BC", 1],
  ["工资", "income", "cash", "#66BB6A", 1],
  ["兼职", "income", "briefcase", "#26A69A", 1],
  ["基金定投", "expense", "chart-line", "#5C6BC0", 0],
  ["储蓄", "expense", "piggy-bank-outline", "#26A69A", 0],
] as const;

async function ensureTransactionColumns(db: SQLiteDatabase): Promise<void> {
  const columns = await db.getAllAsync<{ name: string }>("PRAGMA table_info(transactions)");
  const columnNames = new Set(columns.map((column) => column.name));

  if (!columnNames.has("paymentMethodId")) {
    await db.execAsync("ALTER TABLE transactions ADD COLUMN paymentMethodId TEXT;");
  }
}

async function ensureCategoryColumns(db: SQLiteDatabase): Promise<void> {
  const columns = await db.getAllAsync<{ name: string }>("PRAGMA table_info(categories)");
  const columnNames = new Set(columns.map((column) => column.name));

  if (!columnNames.has("includeInSpending")) {
    await db.execAsync(
      "ALTER TABLE categories ADD COLUMN includeInSpending INTEGER NOT NULL DEFAULT 1;",
    );
  }

  if (!columnNames.has("parentCategoryId")) {
    await db.execAsync("ALTER TABLE categories ADD COLUMN parentCategoryId TEXT;");
  }

  await db.execAsync(`
    UPDATE categories
       SET includeInSpending = 0
     WHERE type = 'transfer';

    UPDATE categories
       SET type = 'expense'
     WHERE type = 'transfer';
  `);
}

export async function initializeSchema(db: SQLiteDatabase): Promise<void> {
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
      includeInSpending INTEGER NOT NULL DEFAULT 1,
      parentCategoryId TEXT,
      createdAt INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS transactions (
      id TEXT PRIMARY KEY NOT NULL,
      accountId TEXT NOT NULL,
      categoryId TEXT,
      paymentMethodId TEXT,
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

    CREATE TABLE IF NOT EXISTS payment_methods (
      id TEXT PRIMARY KEY NOT NULL,
      name TEXT NOT NULL,
      icon TEXT NOT NULL,
      color TEXT NOT NULL,
      isBuiltIn INTEGER NOT NULL DEFAULT 0,
      isActive INTEGER NOT NULL DEFAULT 1,
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

    UPDATE accounts SET type = 'debit' WHERE type = 'bank';
    UPDATE accounts SET type = 'credit' WHERE type = 'card';
    UPDATE accounts SET type = 'ewallet' WHERE type = 'wallet';
  `);

  await ensureTransactionColumns(db);
  await ensureCategoryColumns(db);
  await db.execAsync(
    "CREATE INDEX IF NOT EXISTS idx_transactions_payment_method ON transactions(paymentMethodId);",
  );
}

export async function seedDefaults(db: SQLiteDatabase): Promise<void> {
  const now = Date.now();
  const accountCountRow = await db.getFirstAsync<{ count: number }>(
    "SELECT COUNT(*) as count FROM accounts",
  );

  if (!accountCountRow || accountCountRow.count === 0) {
    await db.runAsync(
      "INSERT INTO accounts (id, name, type, balance, currency, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
      [generateId(), "系统账户", "cash", 0, "CNY", now, now],
    );
  }

  const categoryCountRow = await db.getFirstAsync<{ count: number }>(
    "SELECT COUNT(*) as count FROM categories",
  );

  if (!categoryCountRow || categoryCountRow.count === 0) {
    for (const [name, type, icon, color, includeInSpending] of DEFAULT_CATEGORIES) {
      await db.runAsync(
        "INSERT INTO categories (id, name, type, icon, color, isBuiltIn, includeInSpending, createdAt) VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
        [generateId(), name, type, icon, color, includeInSpending, now],
      );
    }
  }

  const paymentMethodCountRow = await db.getFirstAsync<{ count: number }>(
    "SELECT COUNT(*) as count FROM payment_methods",
  );

  if (!paymentMethodCountRow || paymentMethodCountRow.count === 0) {
    for (const [name, icon, color] of DEFAULT_PAYMENT_METHODS) {
      await db.runAsync(
        "INSERT INTO payment_methods (id, name, icon, color, isBuiltIn, isActive, createdAt) VALUES (?, ?, ?, ?, 1, 1, ?)",
        [generateId(), name, icon, color, now],
      );
    }
  }
}
