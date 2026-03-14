import { getDb } from "@/src/db/client";
import { generateId } from "@/src/lib/id";

export interface TagListItem {
  id: string;
  name: string;
  color: string;
}

function assertNonEmpty(value: string, label: string): void {
  if (!value.trim()) {
    throw new Error(`${label}不能为空`);
  }
}

export async function listTags(): Promise<TagListItem[]> {
  const db = await getDb();
  return db.getAllAsync<TagListItem>(
    "SELECT id, name, color FROM tags ORDER BY name ASC",
  );
}

export async function createTag(name: string, color = "#8E8E93"): Promise<string> {
  assertNonEmpty(name, "标签名称");

  const db = await getDb();
  const id = generateId();
  const now = Date.now();

  await db.runAsync("INSERT INTO tags (id, name, color, createdAt) VALUES (?, ?, ?, ?)", [
    id,
    name.trim(),
    color,
    now,
  ]);

  return id;
}

export async function bindTagsToTransaction(
  transactionId: string,
  tagIds: string[],
): Promise<void> {
  assertNonEmpty(transactionId, "交易");

  const db = await getDb();

  await db.withTransactionAsync(async () => {
    await db.runAsync("DELETE FROM transaction_tags WHERE transactionId = ?", [transactionId]);

    for (const tagId of tagIds) {
      await db.runAsync("INSERT INTO transaction_tags (transactionId, tagId) VALUES (?, ?)", [
        transactionId,
        tagId,
      ]);
    }
  });
}
