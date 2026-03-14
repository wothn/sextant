import type { Account } from "@/src/types/domain";

import { getDb } from "@/src/db/client";
import { generateId } from "@/src/lib/id";

function normalizeAccountName(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) {
    throw new Error("账户名称不能为空");
  }
  return trimmed;
}

export async function listAccounts(): Promise<Account[]> {
  const db = await getDb();
  return db.getAllAsync<Account>(
    "SELECT * FROM accounts WHERE isActive = 1 ORDER BY createdAt ASC",
  );
}

export async function createAccount(name: string, type: Account["type"]): Promise<string> {
  const normalizedName = normalizeAccountName(name);
  const db = await getDb();
  const now = Date.now();
  const id = generateId();

  await db.runAsync(
    "INSERT INTO accounts (id, name, type, balance, currency, isActive, createdAt, updatedAt) VALUES (?, ?, ?, 0, ?, 1, ?, ?)",
    [id, normalizedName, type, "CNY", now, now],
  );

  return id;
}
