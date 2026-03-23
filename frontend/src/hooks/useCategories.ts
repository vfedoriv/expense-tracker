import { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../api/client';
import type { Category, CategoryRequest, ApiError } from '../types';

interface UseCategoriesResult {
  categories: Category[];
  loading: boolean;
  error: string | null;
  fetchCategories: () => Promise<void>;
  createCategory: (request: CategoryRequest) => Promise<Category>;
  updateCategory: (id: number, request: CategoryRequest) => Promise<Category>;
  deleteCategory: (id: number) => Promise<void>;
}

export function useCategories(): UseCategoriesResult {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCategories = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiClient.get<Category[]>('/categories');
      setCategories(data);
    } catch (err: unknown) {
      const apiError = err as ApiError;
      setError(apiError.message || 'Failed to load categories');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchCategories();
  }, [fetchCategories]);

  const createCategory = useCallback(async (request: CategoryRequest): Promise<Category> => {
    const created = await apiClient.post<Category>('/categories', request);
    await fetchCategories();
    return created;
  }, [fetchCategories]);

  const updateCategory = useCallback(async (id: number, request: CategoryRequest): Promise<Category> => {
    const updated = await apiClient.put<Category>(`/categories/${id}`, request);
    await fetchCategories();
    return updated;
  }, [fetchCategories]);

  const deleteCategory = useCallback(async (id: number): Promise<void> => {
    await apiClient.delete(`/categories/${id}`);
    await fetchCategories();
  }, [fetchCategories]);

  return {
    categories,
    loading,
    error,
    fetchCategories,
    createCategory,
    updateCategory,
    deleteCategory,
  };
}
