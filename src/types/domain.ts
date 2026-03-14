export type TransactionType = "expense" | "income" | "transfer";

export interface Category {
  id: string;
  name: string;
  type: Exclude<TransactionType, "transfer">;
  icon: string;
  color: string;
  isBuiltIn: number;
  isActive: number;
  includeInSpending: number;
  parentCategoryId: string | null;
  createdAt: number;
}

export interface PaymentMethod {
  id: string;
  name: string;
  icon: string;
  color: string;
  isBuiltIn: number;
  isActive: number;
  createdAt: number;
}

export interface Transaction {
  id: string;
  categoryId: string | null;
  paymentMethodId: string | null;
  amount: number;
  type: TransactionType;
  description: string;
  transactionDate: number;
  createdAt: number;
  updatedAt: number;
  relatedTransactionId: string | null;
}
