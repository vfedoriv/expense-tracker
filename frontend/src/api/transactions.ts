import { request } from './client';
import type { Transaction, Page } from '../types';

export interface TransactionFilters {
  q?: string;
  categoryId?: number;
  dateFrom?: string;
  dateTo?: string;
  amountMin?: number;
  amountMax?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TransactionInput {
  title: string;
  amount: number;
  currency: string;
  categoryId: number;
  transactionDate: string;
  notes?: string;
}

export const transactionsApi = {
  search: (filters: TransactionFilters = {}) => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params.append(key, String(value));
      }
    });
    return request<Page<Transaction>>(`/transactions?${params}`);
  },
  create: (data: TransactionInput) => request<Transaction>('/transactions', {
    method: 'POST',
    body: JSON.stringify(data),
  }),
  update: (id: number, data: TransactionInput) => request<Transaction>(`/transactions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),
  delete: (id: number) => request<void>(`/transactions/${id}`, { method: 'DELETE' }),
};
