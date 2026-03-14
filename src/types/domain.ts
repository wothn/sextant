export type TransactionType = "expense" | "income" | "transfer";

export interface Account {
  id: string;
  name: string;
  type: "bank" | "card" | "cash" | "wallet";
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
