import * as SQLite from "expo-sqlite";

import { initializeSchema, seedDefaults } from "./schema";

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

async function initializeDatabase(): Promise<SQLite.SQLiteDatabase> {
  const db = await SQLite.openDatabaseAsync("sextant.db");
  await initializeSchema(db);
  await seedDefaults(db);
  return db;
}

export async function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = initializeDatabase();
  }

  try {
    return await dbPromise;
  } catch (error) {
    dbPromise = null;
    throw error;
  }
}
