import { request } from './client';
import type { Category } from '../types';

export const categoriesApi = {
  getAll: () => request<Category[]>('/categories'),
  create: (name: string) => request<Category>('/categories', {
    method: 'POST',
    body: JSON.stringify({ name }),
  }),
  update: (id: number, name: string) => request<Category>(`/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  }),
  delete: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }),
};
