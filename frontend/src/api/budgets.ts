import { request } from './client';
import type { BudgetSummary } from '../types';

export const budgetsApi = {
  getSummary: (year: number, month: number) =>
    request<BudgetSummary>(`/budgets/${year}/${month}`),
  setBudget: (year: number, month: number, amount: number) =>
    request<BudgetSummary>(`/budgets/${year}/${month}`, {
      method: 'PUT',
      body: JSON.stringify({ amount }),
    }),
  deleteBudget: (year: number, month: number) =>
    request<void>(`/budgets/${year}/${month}`, { method: 'DELETE' }),
};
