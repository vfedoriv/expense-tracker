import { useState, useEffect, useCallback, useRef } from 'react';
import { apiClient } from '../api/client';
import type { Transaction, TransactionRequest, TransactionFilters, ApiError } from '../types';

interface UseTransactionsResult {
  transactions: Transaction[];
  loading: boolean;
  error: string | null;
  filters: TransactionFilters;
  setFilters: (filters: TransactionFilters) => void;
  fetchTransactions: () => Promise<void>;
  createTransaction: (request: TransactionRequest) => Promise<Transaction>;
  updateTransaction: (id: number, request: TransactionRequest) => Promise<Transaction>;
  deleteTransaction: (id: number) => Promise<void>;
}

function buildQueryString(filters: TransactionFilters): string {
  const params = new URLSearchParams();
  if (filters.search) params.set('search', filters.search);
  if (filters.categoryId !== undefined) params.set('categoryId', String(filters.categoryId));
  if (filters.dateFrom) params.set('dateFrom', filters.dateFrom);
  if (filters.dateTo) params.set('dateTo', filters.dateTo);
  if (filters.amountMin !== undefined) params.set('amountMin', String(filters.amountMin));
  if (filters.amountMax !== undefined) params.set('amountMax', String(filters.amountMax));
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

export function useTransactions(): UseTransactionsResult {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<TransactionFilters>({});
  const filtersRef = useRef<TransactionFilters>(filters);

  // Keep ref in sync for use in callbacks
  filtersRef.current = filters;

  const fetchTransactions = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const qs = buildQueryString(filtersRef.current);
      const data = await apiClient.get<Transaction[]>(`/transactions${qs}`);
      setTransactions(data);
    } catch (err: unknown) {
      const apiError = err as ApiError;
      setError(apiError.message || 'Failed to load transactions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchTransactions();
  }, [fetchTransactions, filters]);

  const createTransaction = useCallback(
    async (request: TransactionRequest): Promise<Transaction> => {
      const created = await apiClient.post<Transaction>('/transactions', request);
      await fetchTransactions();
      return created;
    },
    [fetchTransactions],
  );

  const updateTransaction = useCallback(
    async (id: number, request: TransactionRequest): Promise<Transaction> => {
      const updated = await apiClient.put<Transaction>(`/transactions/${id}`, request);
      await fetchTransactions();
      return updated;
    },
    [fetchTransactions],
  );

  const deleteTransaction = useCallback(
    async (id: number): Promise<void> => {
      await apiClient.delete(`/transactions/${id}`);
      await fetchTransactions();
    },
    [fetchTransactions],
  );

  return {
    transactions,
    loading,
    error,
    filters,
    setFilters,
    fetchTransactions,
    createTransaction,
    updateTransaction,
    deleteTransaction,
  };
}
