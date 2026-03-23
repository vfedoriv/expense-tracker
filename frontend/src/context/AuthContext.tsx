import {
  useEffect,
  useState,
  useCallback,
  type ReactNode,
} from 'react';
import { apiClient } from '../api/client';
import type { User } from '../types';
import { AuthContext } from './authContextDef';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchUser = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const userData = await apiClient.get<User>('/users/me');
      setUser(userData);
    } catch {
      setError('Failed to authenticate');
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchUser();
  }, [fetchUser]);

  const value = {
    user,
    loading,
    error,
    isAuthenticated: user !== null,
    refreshUser: fetchUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
