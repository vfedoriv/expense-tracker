import { useState, useEffect, useCallback } from 'react';
import { categoriesApi } from '../api/categories';
import type { Category } from '../types';
import { CategoryList } from '../features/categories/CategoryList';
import { CategoryForm } from '../features/categories/CategoryForm';
import { LoadingSpinner } from '../components/ui/LoadingSpinner';
import { ApiError } from '../api/client';

export function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCategories = useCallback(async () => {
    setLoading(true);
    try {
      const data = await categoriesApi.getAll();
      setCategories(data);
    } catch { /* handled */ }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchCategories(); }, [fetchCategories]);

  const handleCreate = async (name: string) => {
    setError(null);
    try {
      await categoriesApi.create(name);
      fetchCategories();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        const body = e.body as { message?: string } | null;
        setError(body?.message ?? 'Category already exists');
      } else {
        setError('Failed to create category');
      }
    }
  };

  const handleRename = async (id: number, name: string) => {
    setError(null);
    try {
      await categoriesApi.update(id, name);
      fetchCategories();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        const body = e.body as { message?: string } | null;
        setError(body?.message ?? 'Category name already exists');
      } else {
        setError('Failed to rename category');
      }
    }
  };

  const handleDelete = async (id: number) => {
    setError(null);
    try {
      await categoriesApi.delete(id);
      fetchCategories();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        const body = e.body as { message?: string } | null;
        setError(body?.message ?? 'Cannot delete category with transactions');
      } else {
        setError('Failed to delete category');
      }
    }
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Categories</h1>

      <div className="max-w-xl">
        <CategoryForm onSubmit={handleCreate} error={error} />
      </div>

      <div className="max-w-xl">
        <CategoryList
          categories={categories}
          onRename={handleRename}
          onDelete={handleDelete}
          error={error}
        />
      </div>
    </div>
  );
}
