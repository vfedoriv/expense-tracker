import { useState, useCallback } from 'react';
import { apiClient } from '../api/client';
import type { MonthlyBudget, BudgetRequest, ApiError } from '../types';

interface UseBudgetResult {
  saving: boolean;
  error: string | null;
  saveBudget: (request: BudgetRequest) => Promise<MonthlyBudget>;
  clearError: () => void;
}

export function useBudget(): UseBudgetResult {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const saveBudget = useCallback(async (request: BudgetRequest): Promise<MonthlyBudget> => {
    try {
      setSaving(true);
      setError(null);
      const result = await apiClient.post<MonthlyBudget>('/budgets', request);
      return result;
    } catch (err: unknown) {
      const apiError = err as ApiError;
      const message = apiError.message || 'Failed to save budget';
      setError(message);
      throw err;
    } finally {
      setSaving(false);
    }
  }, []);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  return {
    saving,
    error,
    saveBudget,
    clearError,
  };
}
