import { request } from './client';
import type { User } from '../types';

export const authApi = {
  me: () => request<User>('/auth/me'),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
};
