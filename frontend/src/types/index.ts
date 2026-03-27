export interface User {
  id: number
  provider: string
  providerUserId: string
  email: string | null
  displayName: string
  avatarUrl: string | null
  createdAt: string
}

export interface Category {
  id: number
  name: string
  createdAt: string
  updatedAt: string
}

export interface Transaction {
  id: number
  title: string
  amount: number
  currency: string
  transactionDate: string
  categoryId: number
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface BudgetSummary {
  year: number
  month: number
  budget: number | null
  totalSpent: number
  remaining: number | null
  usagePercent: number | null
  budgetSet: boolean
}

export interface BudgetAlertMessage {
  threshold: number
  spent: number
  budget: number
  message: string
}

export interface TransactionFilters {
  search?: string
  category?: number
  dateFrom?: string
  dateTo?: string
  amountMin?: number
  amountMax?: number
}
