import * as FileSystem from "expo-file-system/legacy";

import { getDb } from "@/src/db/client";

export async function exportTransactionsCsv(): Promise<string> {
  const db = await getDb();
  const rows = await db.getAllAsync<{
    transactionDate: number;
    type: string;
    amount: number;
    paymentMethodName: string | null;
    categoryName: string | null;
    description: string;
  }>(
    `SELECT t.transactionDate,
            t.type,
            t.amount,
            pm.name as paymentMethodName,
            c.name as categoryName,
            t.description
       FROM transactions t
  LEFT JOIN payment_methods pm ON pm.id = t.paymentMethodId
  LEFT JOIN categories c ON c.id = t.categoryId
   ORDER BY t.transactionDate DESC`,
  );

  const lines = [
    "date,type,amount,payment_method,category,description",
    ...rows.map((row) => {
      const date = new Date(row.transactionDate).toISOString().slice(0, 10);
      const paymentMethod = row.paymentMethodName ?? "";
      const category = row.categoryName ?? "";
      const description = (row.description ?? "").replaceAll('"', '""');
      return `${date},${row.type},${row.amount},${paymentMethod},${category},"${description}"`;
    }),
  ];

  const dir = FileSystem.documentDirectory;
  if (!dir) {
    throw new Error("导出目录不可用");
  }

  const path = `${dir}sextant-transactions-${Date.now()}.csv`;
  await FileSystem.writeAsStringAsync(path, lines.join("\n"), {
    encoding: FileSystem.EncodingType.UTF8,
  });

  return path;
}
