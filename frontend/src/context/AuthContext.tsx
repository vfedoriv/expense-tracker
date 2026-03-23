import {
  useEffect,
  useState,
  useCallback,
  type ReactNode,
} from 'react';
import type { User } from '../types';
import { AuthContext } from './authContextDef';

const BASE_URL = '/api';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchUser = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(`${BASE_URL}/users/me`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      });
      if (!response.ok) {
        setUser(null);
        return;
      }
      const userData = (await response.json()) as User;
      setUser(userData);
    } catch {
      setError('Failed to authenticate');
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await fetch(`${BASE_URL}/logout`, {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // Even if the logout request fails, clear the local state
    }
    setUser(null);
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
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
