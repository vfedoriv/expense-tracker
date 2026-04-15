export interface User {
  id: number;
  provider: string;
  email: string;
  displayName: string;
}

export interface Category {
  id: number;
  name: string;
  createdAt: string;
}

export interface Transaction {
  id: number;
  title: string;
  amount: number;
  currency: string;
  categoryId: number;
  categoryName: string;
  transactionDate: string;
  notes: string | null;
  createdAt: string;
}

export interface BudgetSummary {
  budgetSet: boolean;
  budgetAmount: number | null;
  totalSpent: number;
  remaining: number | null;
  percentage: number | null;
  year: number;
  month: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface BudgetAlert {
  type: string;
  threshold: number;
  budgetAmount: number;
  spent: number;
  percentage: number;
  year: number;
  month: number;
  message: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}
