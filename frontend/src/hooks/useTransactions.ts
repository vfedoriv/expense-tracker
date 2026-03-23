import { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../api/client';
import type { Transaction, TransactionRequest, ApiError } from '../types';

interface UseTransactionsResult {
  transactions: Transaction[];
  loading: boolean;
  error: string | null;
  fetchTransactions: () => Promise<void>;
  createTransaction: (request: TransactionRequest) => Promise<Transaction>;
  updateTransaction: (id: number, request: TransactionRequest) => Promise<Transaction>;
  deleteTransaction: (id: number) => Promise<void>;
}

export function useTransactions(): UseTransactionsResult {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTransactions = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiClient.get<Transaction[]>('/transactions');
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
  }, [fetchTransactions]);

  const createTransaction = useCallback(async (request: TransactionRequest): Promise<Transaction> => {
    const created = await apiClient.post<Transaction>('/transactions', request);
    await fetchTransactions();
    return created;
  }, [fetchTransactions]);

  const updateTransaction = useCallback(async (id: number, request: TransactionRequest): Promise<Transaction> => {
    const updated = await apiClient.put<Transaction>(`/transactions/${id}`, request);
    await fetchTransactions();
    return updated;
  }, [fetchTransactions]);

  const deleteTransaction = useCallback(async (id: number): Promise<void> => {
    await apiClient.delete(`/transactions/${id}`);
    await fetchTransactions();
  }, [fetchTransactions]);

  return {
    transactions,
    loading,
    error,
    fetchTransactions,
    createTransaction,
    updateTransaction,
    deleteTransaction,
  };
}
