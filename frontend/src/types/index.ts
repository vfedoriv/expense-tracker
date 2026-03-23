export interface User {
  id: number;
  provider: string;
  providerUserId: string;
  email: string | null;
  displayName: string;
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Category {
  id: number;
  name: string;
  createdAt: string;
}

export interface CategoryRequest {
  name: string;
}

export interface Transaction {
  id: number;
  title: string;
  amount: number;
  currency: string;
  transactionDate: string;
  categoryId: number;
  categoryName: string;
  notes: string | null;
  createdAt: string;
}

export interface TransactionRequest {
  title: string;
  amount: number;
  transactionDate: string;
  categoryId: number;
  notes?: string;
}

export interface MonthlyBudget {
  id: number;
  year: number;
  month: number;
  amount: number;
  createdAt: string;
  updatedAt: string;
}

export interface BudgetRequest {
  year: number;
  month: number;
  amount: number;
}

export interface DashboardData {
  totalSpent: number;
  budgetAmount: number | null;
  remaining: number | null;
  usagePercentage: number | null;
  year: number;
  month: number;
}

export interface ApiError {
  message: string;
  status: number;
}

export interface BudgetAlert {
  threshold: number;
  message: string;
  year: number;
  month: number;
}
