import type { Account } from "@/src/types/domain";

import { getDb } from "@/src/db/client";
import { generateId } from "@/src/lib/id";

interface ListAccountsOptions {
  includeInactive?: boolean;
}

export interface CreateAccountInput {
  name: string;
  type: Account["type"];
  initialBalance?: number;
  currency?: string;
}

export interface UpdateAccountInput {
  name: string;
  type: Account["type"];
}

type DbClient = Awaited<ReturnType<typeof getDb>>;

function normalizeAccountName(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) {
    throw new Error("账户名称不能为空");
  }
  return trimmed;
}

function normalizeBalance(balance: number | undefined): number {
  const resolved = balance ?? 0;

  if (!Number.isFinite(resolved)) {
    throw new Error("账户初始余额格式不正确");
  }

  return Number(resolved.toFixed(2));
}

function normalizeCurrency(currency: string | undefined): string {
  const normalized = (currency ?? "CNY").trim().toUpperCase();

  if (!/^[A-Z]{3}$/.test(normalized)) {
    throw new Error("货币代码必须为 3 位字母");
  }

  return normalized;
}

async function findActiveAccountConflict(
  db: DbClient,
  name: string,
  ignoreId?: string,
): Promise<{ id: string } | null> {
  const query = ignoreId
    ? "SELECT id FROM accounts WHERE isActive = 1 AND LOWER(name) = LOWER(?) AND id != ? LIMIT 1"
    : "SELECT id FROM accounts WHERE isActive = 1 AND LOWER(name) = LOWER(?) LIMIT 1";
  const params = ignoreId ? [name, ignoreId] : [name];

  const existing = await db.getFirstAsync<{ id: string }>(query, params);
  return existing ?? null;
}

async function getAccountIdentity(
  db: DbClient,
  accountId: string,
): Promise<Pick<Account, "id" | "name">> {
  const account = await db.getFirstAsync<Pick<Account, "id" | "name">>(
    "SELECT id, name FROM accounts WHERE id = ? LIMIT 1",
    [accountId],
  );

  if (!account) {
    throw new Error("账户不存在");
  }

  return account;
}

export async function listAccounts(options: ListAccountsOptions = {}): Promise<Account[]> {
  const db = await getDb();

  if (options.includeInactive) {
    return db.getAllAsync<Account>("SELECT * FROM accounts ORDER BY isActive DESC, createdAt ASC");
  }

  return db.getAllAsync<Account>(
    "SELECT * FROM accounts WHERE isActive = 1 ORDER BY createdAt ASC",
  );
}

export async function createAccount(input: CreateAccountInput): Promise<string> {
  const normalizedName = normalizeAccountName(input.name);
  const initialBalance = normalizeBalance(input.initialBalance);
  const currency = normalizeCurrency(input.currency);
  const db = await getDb();
  const conflict = await findActiveAccountConflict(db, normalizedName);

  if (conflict) {
    throw new Error("已存在同名启用账户");
  }

  const now = Date.now();
  const id = generateId();

  await db.runAsync(
    "INSERT INTO accounts (id, name, type, balance, currency, isActive, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
    [id, normalizedName, input.type, initialBalance, currency, now, now],
  );

  return id;
}

export async function updateAccount(accountId: string, input: UpdateAccountInput): Promise<void> {
  const normalizedName = normalizeAccountName(input.name);
  const db = await getDb();
  const conflict = await findActiveAccountConflict(db, normalizedName, accountId);

  if (conflict) {
    throw new Error("已存在同名启用账户");
  }

  await getAccountIdentity(db, accountId);

  await db.runAsync("UPDATE accounts SET name = ?, type = ?, updatedAt = ? WHERE id = ?", [
    normalizedName,
    input.type,
    Date.now(),
    accountId,
  ]);
}

export async function setAccountActive(accountId: string, isActive: boolean): Promise<void> {
  const db = await getDb();

  if (isActive) {
    const account = await getAccountIdentity(db, accountId);
    const conflict = await findActiveAccountConflict(db, account.name, accountId);

    if (conflict) {
      throw new Error("存在同名启用账户，请先修改名称再恢复");
    }
  } else {
    await getAccountIdentity(db, accountId);
  }

  await db.runAsync("UPDATE accounts SET isActive = ?, updatedAt = ? WHERE id = ?", [
    isActive ? 1 : 0,
    Date.now(),
    accountId,
  ]);
}
