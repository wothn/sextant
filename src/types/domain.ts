export type TransactionType = "expense" | "income" | "transfer";

export type AccountType = "cash" | "debit" | "credit" | "ewallet" | "investment" | "loan";

export const ACCOUNT_TYPE_OPTIONS: AccountType[] = [
  "cash",
  "debit",
  "credit",
  "ewallet",
  "investment",
  "loan",
];

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  cash: "现金",
  debit: "储蓄账户",
  credit: "信用卡",
  ewallet: "电子钱包",
  investment: "投资账户",
  loan: "贷款/负债",
};

const ACCOUNT_TYPE_ALIASES: Record<string, AccountType> = {
  bank: "debit",
  card: "credit",
  wallet: "ewallet",
  cash: "cash",
  debit: "debit",
  credit: "credit",
  ewallet: "ewallet",
  investment: "investment",
  loan: "loan",
};

export function normalizeAccountType(type: string): AccountType | null {
  const key = type.trim().toLowerCase();
  return ACCOUNT_TYPE_ALIASES[key] ?? null;
}

export function getAccountTypeLabel(type: string): string {
  const normalized = normalizeAccountType(type);
  return normalized ? ACCOUNT_TYPE_LABELS[normalized] : "未知类型";
}

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  balance: number;
  currency: string;
  isActive: number;
  createdAt: number;
  updatedAt: number;
}

export interface Category {
  id: string;
  name: string;
  type: Exclude<TransactionType, "transfer">;
  icon: string;
  color: string;
  isBuiltIn: number;
  isActive: number;
  createdAt: number;
}

export interface Transaction {
  id: string;
  accountId: string;
  categoryId: string | null;
  amount: number;
  type: TransactionType;
  description: string;
  transactionDate: number;
  createdAt: number;
  updatedAt: number;
  relatedAccountId: string | null;
  relatedTransactionId: string | null;
}
