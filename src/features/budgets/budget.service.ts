import { getDb } from "@/src/db/client";
import { getMonthRange } from "@/src/lib/date";
import { generateId } from "@/src/lib/id";

export interface BudgetProgressItem {
  categoryId: string;
  categoryName: string;
  budgetAmount: number;
  alertThreshold: number;
  expenseAmount: number;
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

function assertAlertThreshold(value: number): void {
  if (!Number.isFinite(value) || value < 0 || value > 1) {
    throw new Error("预警阈值需要在 0 到 1 之间");
  }
}

export async function upsertMonthlyBudget(
  categoryId: string,
  amount: number,
  alertThreshold = 0.8,
): Promise<string> {
  assertNonEmpty(categoryId, "分类");
  assertPositiveAmount(amount, "预算金额");
  assertAlertThreshold(alertThreshold);

  const db = await getDb();
  const { start: startDate } = getMonthRange();

  const existing = await db.getFirstAsync<{ id: string }>(
    "SELECT id FROM budgets WHERE categoryId = ? AND period = ? AND startDate = ? LIMIT 1",
    [categoryId, "monthly", startDate],
  );

  if (existing?.id) {
    await db.runAsync(
      "UPDATE budgets SET amount = ?, alertThreshold = ?, isActive = 1 WHERE id = ?",
      [amount, alertThreshold, existing.id],
    );
    return existing.id;
  }

  const id = generateId();
  await db.runAsync(
    "INSERT INTO budgets (id, categoryId, amount, period, startDate, alertThreshold, isActive) VALUES (?, ?, ?, ?, ?, ?, 1)",
    [id, categoryId, amount, "monthly", startDate, alertThreshold],
  );
  return id;
}

export async function listCurrentMonthBudgetProgress(): Promise<BudgetProgressItem[]> {
  const db = await getDb();
  const { start, end } = getMonthRange();

  return db.getAllAsync<BudgetProgressItem>(
    `SELECT b.categoryId,
            c.name as categoryName,
            b.amount as budgetAmount,
            b.alertThreshold as alertThreshold,
            COALESCE(SUM(t.amount), 0) as expenseAmount
       FROM budgets b
       JOIN categories c ON c.id = b.categoryId
  LEFT JOIN transactions t
         ON t.categoryId = b.categoryId
        AND t.type = 'expense'
        AND t.transactionDate >= ?
        AND t.transactionDate < ?
      WHERE b.period = 'monthly'
        AND b.startDate = ?
        AND b.isActive = 1
   GROUP BY b.categoryId, c.name, b.amount, b.alertThreshold
   ORDER BY expenseAmount DESC`,
    [start, end, start],
  );
}
