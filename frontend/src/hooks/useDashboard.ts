import { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../api/client';
import type { DashboardData, ApiError } from '../types';

interface UseDashboardResult {
  dashboard: DashboardData | null;
  loading: boolean;
  error: string | null;
  fetchDashboard: (year: number, month: number) => Promise<void>;
}

export function useDashboard(year: number, month: number): UseDashboardResult {
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchDashboard = useCallback(async (y: number, m: number) => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiClient.get<DashboardData>(`/dashboard?year=${y}&month=${m}`);
      setDashboard(data);
    } catch (err: unknown) {
      const apiError = err as ApiError;
      setError(apiError.message || 'Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchDashboard(year, month);
  }, [year, month, fetchDashboard]);

  return {
    dashboard,
    loading,
    error,
    fetchDashboard,
  };
}
