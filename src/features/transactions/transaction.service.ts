import type { AccountType, Category, TransactionType } from "@/src/types/domain";

import { getDb } from "@/src/db/client";
import { getDateKey, getDayRange, getMonthKey, getMonthRange } from "@/src/lib/date";
import { generateId } from "@/src/lib/id";

export interface TransactionListItem {
  id: string;
  amount: number;
  type: TransactionType;
  description: string;
  transactionDate: number;
  categoryName: string | null;
  categoryColor?: string | null;
  categoryIcon?: string | null;
  accountName: string;
}

export interface TodaySummaryCategory {
  categoryId: string | null;
  categoryName: string;
  amount: number;
  share: number;
}

export interface TodaySummary {
  income: number;
  expense: number;
  net: number;
  topCategories: TodaySummaryCategory[];
}

export interface DailyTransactionGroup {
  dateKey: string;
  totalIncome: number;
  totalExpense: number;
  transactionCount: number;
  transactions: TransactionListItem[];
}

export interface MonthlyTrendPoint {
  monthKey: string;
  label: string;
  income: number;
  expense: number;
  net: number;
}

export interface CategoryBreakdownItem {
  categoryId: string | null;
  categoryName: string;
  categoryColor: string;
  amount: number;
  transactionCount: number;
  share: number;
}

export interface AccountMonthlySummary {
  accountId: string;
  accountName: string;
  accountType: AccountType;
  balance: number;
  income: number;
  expense: number;
  net: number;
  transactionCount: number;
}

interface RangeSummary {
  income: number;
  expense: number;
  net: number;
}

function assertNonEmpty(value: string, label: string): void {
  if (!value.trim()) {
    throw new Error(`${label}不能为空`);
  }
}

function assertPositiveAmount(amount: number, label: string): void {
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error(`${label}必须为正数`);
  }
}

async function getRangeSummary(
  start: number,
  end: number,
  dbArg?: Awaited<ReturnType<typeof getDb>>,
): Promise<RangeSummary> {
  const db = dbArg ?? (await getDb());

  const summary = await db.getFirstAsync<{ income: number; expense: number }>(
    `SELECT COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) as income,
            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) as expense
       FROM transactions
      WHERE transactionDate >= ?
        AND transactionDate < ?`,
    [start, end],
  );

  return {
    income: summary?.income ?? 0,
    expense: summary?.expense ?? 0,
    net: (summary?.income ?? 0) - (summary?.expense ?? 0),
  };
}

interface CreateTransactionInput {
  accountId: string;
  categoryId: string;
  amount: number;
  type: Exclude<TransactionType, "transfer">;
  description?: string;
  transactionDate?: number;
}

export async function listCategories(
  type: Exclude<TransactionType, "transfer">,
): Promise<Category[]> {
  const db = await getDb();
  return db.getAllAsync<Category>(
    "SELECT * FROM categories WHERE isActive = 1 AND type = ? ORDER BY isBuiltIn DESC, name ASC",
    [type],
  );
}

export async function createTransaction(input: CreateTransactionInput): Promise<string> {
  assertNonEmpty(input.accountId, "账户");
  assertNonEmpty(input.categoryId, "分类");
  assertPositiveAmount(input.amount, "金额");

  const db = await getDb();
  const now = Date.now();
  const txId = generateId();
  const signedAmount = input.type === "expense" ? -Math.abs(input.amount) : Math.abs(input.amount);

  await db.withTransactionAsync(async () => {
    await db.runAsync(
      "INSERT INTO transactions (id, accountId, categoryId, amount, type, description, transactionDate, createdAt, updatedAt, relatedAccountId, relatedTransactionId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
      [
        txId,
        input.accountId,
        input.categoryId,
        Math.abs(input.amount),
        input.type,
        input.description ?? "",
        input.transactionDate ?? now,
        now,
        now,
      ],
    );

    await db.runAsync("UPDATE accounts SET balance = balance + ?, updatedAt = ? WHERE id = ?", [
      signedAmount,
      now,
      input.accountId,
    ]);
  });

  return txId;
}

export async function createTransfer(
  fromAccountId: string,
  toAccountId: string,
  amount: number,
  description = "",
): Promise<void> {
  assertNonEmpty(fromAccountId, "转出账户");
  assertNonEmpty(toAccountId, "转入账户");
  if (fromAccountId.trim() === toAccountId.trim()) {
    throw new Error("转入转出账户不能相同");
  }
  assertPositiveAmount(amount, "金额");

  const db = await getDb();
  const now = Date.now();
  const outId = generateId();
  const inId = generateId();

  await db.withTransactionAsync(async () => {
    await db.runAsync(
      "INSERT INTO transactions (id, accountId, categoryId, amount, type, description, transactionDate, createdAt, updatedAt, relatedAccountId, relatedTransactionId) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)",
      [
        outId,
        fromAccountId,
        Math.abs(amount),
        "transfer",
        description,
        now,
        now,
        now,
        toAccountId,
        inId,
      ],
    );

    await db.runAsync(
      "INSERT INTO transactions (id, accountId, categoryId, amount, type, description, transactionDate, createdAt, updatedAt, relatedAccountId, relatedTransactionId) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)",
      [
        inId,
        toAccountId,
        Math.abs(amount),
        "transfer",
        description,
        now,
        now,
        now,
        fromAccountId,
        outId,
      ],
    );

    await db.runAsync("UPDATE accounts SET balance = balance - ?, updatedAt = ? WHERE id = ?", [
      Math.abs(amount),
      now,
      fromAccountId,
    ]);
    await db.runAsync("UPDATE accounts SET balance = balance + ?, updatedAt = ? WHERE id = ?", [
      Math.abs(amount),
      now,
      toAccountId,
    ]);
  });
}

export async function listRecentTransactions(limit = 20): Promise<TransactionListItem[]> {
  const db = await getDb();
  return db.getAllAsync<TransactionListItem>(
    `SELECT t.id,
            t.amount,
            t.type,
            t.description,
            t.transactionDate,
            c.name as categoryName,
            c.color as categoryColor,
            c.icon as categoryIcon,
            a.name as accountName
     FROM transactions t
     JOIN accounts a ON a.id = t.accountId
     LEFT JOIN categories c ON c.id = t.categoryId
     ORDER BY t.transactionDate DESC
     LIMIT ?`,
    [limit],
  );
}

export async function getCurrentMonthSummary(): Promise<RangeSummary> {
  const now = new Date();
  const monthRange = getMonthRange(now);

  return getRangeSummary(monthRange.start, monthRange.end);
}

export async function getTodaySummary(): Promise<TodaySummary> {
  const db = await getDb();
  const dayRange = getDayRange(new Date());
  const summary = await getRangeSummary(dayRange.start, dayRange.end, db);
  const categoryRows = await db.getAllAsync<{
    categoryId: string | null;
    categoryName: string | null;
    amount: number;
  }>(
    `SELECT t.categoryId,
            c.name as categoryName,
            COALESCE(SUM(t.amount), 0) as amount
       FROM transactions t
       LEFT JOIN categories c ON c.id = t.categoryId
      WHERE t.type = 'expense'
        AND t.transactionDate >= ?
        AND t.transactionDate < ?
   GROUP BY t.categoryId, c.name
   ORDER BY amount DESC
      LIMIT 3`,
    [dayRange.start, dayRange.end],
  );

  const topCategories = categoryRows.map((item) => ({
    categoryId: item.categoryId,
    categoryName: item.categoryName ?? "未分类",
    amount: item.amount,
    share: summary.expense > 0 ? item.amount / summary.expense : 0,
  }));

  return {
    ...summary,
    topCategories,
  };
}

export async function listTransactionGroupsByDay(
  days = 30,
  limit = 120,
): Promise<DailyTransactionGroup[]> {
  const db = await getDb();
  const rangeStart = new Date();
  rangeStart.setDate(rangeStart.getDate() - (days - 1));
  const dayRange = getDayRange(rangeStart);

  const rows = await db.getAllAsync<TransactionListItem>(
    `SELECT t.id,
            t.amount,
            t.type,
            t.description,
            t.transactionDate,
            c.name as categoryName,
            c.color as categoryColor,
            c.icon as categoryIcon,
            a.name as accountName
       FROM transactions t
       JOIN accounts a ON a.id = t.accountId
       LEFT JOIN categories c ON c.id = t.categoryId
      WHERE t.transactionDate >= ?
   ORDER BY t.transactionDate DESC
      LIMIT ?`,
    [dayRange.start, limit],
  );

  const groups = new Map<string, DailyTransactionGroup>();

  for (const row of rows) {
    const dateKey = getDateKey(row.transactionDate);
    const existing = groups.get(dateKey);

    if (existing) {
      existing.transactions.push(row);
      existing.transactionCount += 1;
      if (row.type === "income") {
        existing.totalIncome += row.amount;
      }
      if (row.type === "expense") {
        existing.totalExpense += row.amount;
      }
      continue;
    }

    groups.set(dateKey, {
      dateKey,
      totalIncome: row.type === "income" ? row.amount : 0,
      totalExpense: row.type === "expense" ? row.amount : 0,
      transactionCount: 1,
      transactions: [row],
    });
  }

  return Array.from(groups.values());
}

export async function getMonthlyTrend(months = 6): Promise<MonthlyTrendPoint[]> {
  const db = await getDb();
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth() - (months - 1), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 1);

  const rows = await db.getAllAsync<{
    monthKey: string;
    income: number;
    expense: number;
  }>(
    `SELECT strftime('%Y-%m', t.transactionDate / 1000, 'unixepoch', 'localtime') as monthKey,
            COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount ELSE 0 END), 0) as income,
            COALESCE(SUM(CASE WHEN t.type = 'expense' THEN t.amount ELSE 0 END), 0) as expense
       FROM transactions t
      WHERE t.transactionDate >= ?
        AND t.transactionDate < ?
        AND t.type IN ('income', 'expense')
   GROUP BY monthKey
   ORDER BY monthKey ASC`,
    [start.getTime(), end.getTime()],
  );

  const byMonth = new Map(rows.map((item) => [item.monthKey, item]));
  const points: MonthlyTrendPoint[] = [];

  for (let index = 0; index < months; index += 1) {
    const cursor = new Date(start.getFullYear(), start.getMonth() + index, 1);
    const monthKey = getMonthKey(cursor);
    const row = byMonth.get(monthKey);
    const income = row?.income ?? 0;
    const expense = row?.expense ?? 0;

    points.push({
      monthKey,
      label: `${cursor.getMonth() + 1}月`,
      income,
      expense,
      net: income - expense,
    });
  }

  return points;
}

export async function getCurrentMonthCategoryBreakdown(): Promise<CategoryBreakdownItem[]> {
  const db = await getDb();
  const monthRange = getMonthRange(new Date());
  const rows = await db.getAllAsync<{
    categoryId: string | null;
    categoryName: string | null;
    categoryColor: string | null;
    amount: number;
    transactionCount: number;
  }>(
    `SELECT t.categoryId,
            c.name as categoryName,
            c.color as categoryColor,
            COALESCE(SUM(t.amount), 0) as amount,
            COUNT(t.id) as transactionCount
       FROM transactions t
       LEFT JOIN categories c ON c.id = t.categoryId
      WHERE t.type = 'expense'
        AND t.transactionDate >= ?
        AND t.transactionDate < ?
   GROUP BY t.categoryId, c.name, c.color
   ORDER BY amount DESC`,
    [monthRange.start, monthRange.end],
  );
  const total = rows.reduce((sum, item) => sum + item.amount, 0);

  return rows.map((item) => ({
    categoryId: item.categoryId,
    categoryName: item.categoryName ?? "未分类",
    categoryColor: item.categoryColor ?? "#90A4AE",
    amount: item.amount,
    transactionCount: item.transactionCount,
    share: total > 0 ? item.amount / total : 0,
  }));
}

export async function getCurrentMonthAccountSummaries(): Promise<AccountMonthlySummary[]> {
  const db = await getDb();
  const monthRange = getMonthRange(new Date());

  const rows = await db.getAllAsync<AccountMonthlySummary>(
    `SELECT a.id as accountId,
            a.name as accountName,
            a.type as accountType,
            a.balance as balance,
            COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount ELSE 0 END), 0) as income,
            COALESCE(SUM(CASE WHEN t.type = 'expense' THEN t.amount ELSE 0 END), 0) as expense,
            COUNT(t.id) as transactionCount,
            COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount ELSE 0 END), 0) - COALESCE(SUM(CASE WHEN t.type = 'expense' THEN t.amount ELSE 0 END), 0) as net
       FROM accounts a
       LEFT JOIN transactions t
         ON t.accountId = a.id
        AND t.transactionDate >= ?
        AND t.transactionDate < ?
        AND t.type IN ('income', 'expense')
      WHERE a.isActive = 1
   GROUP BY a.id, a.name, a.type, a.balance
   ORDER BY net DESC, a.createdAt ASC`,
    [monthRange.start, monthRange.end],
  );

  return rows;
}
