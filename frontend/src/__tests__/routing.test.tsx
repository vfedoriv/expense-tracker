import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route, Navigate } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../components/layout/ProtectedRoute';
import { Layout } from '../components/layout/Layout';
import type { User } from '../types';

const mockUser: User = {
  id: 1,
  provider: 'fake',
  providerUserId: 'fake-user-1',
  email: 'admin@test.com',
  displayName: 'Test User',
  avatarUrl: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function renderWithAuth(initialPath: string, authenticated = true) {
  if (authenticated) {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockUser),
    } as Response);
  } else {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ message: 'Unauthorized' }),
    } as Response);
  }

  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route path="/dashboard" element={<div>Dashboard Page</div>} />
            <Route path="/transactions" element={<div>Transactions Page</div>} />
            <Route path="/categories" element={<div>Categories Page</div>} />
          </Route>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('Routing', () => {
  it('redirects / to /dashboard', async () => {
    renderWithAuth('/');

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });
  });

  it('renders dashboard page at /dashboard', async () => {
    renderWithAuth('/dashboard');

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });
  });

  it('renders transactions page at /transactions', async () => {
    renderWithAuth('/transactions');

    await waitFor(() => {
      expect(screen.getByText('Transactions Page')).toBeInTheDocument();
    });
  });

  it('renders categories page at /categories', async () => {
    renderWithAuth('/categories');

    await waitFor(() => {
      expect(screen.getByText('Categories Page')).toBeInTheDocument();
    });
  });

  it('redirects to login when not authenticated', async () => {
    renderWithAuth('/dashboard', false);

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
  });

  it('shows loading state while auth is pending', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise(() => {
        /* never resolves */
      })
    );

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              element={
                <ProtectedRoute>
                  <Layout />
                </ProtectedRoute>
              }
            >
              <Route path="/dashboard" element={<div>Dashboard Page</div>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    expect(screen.getByText('Authenticating...')).toBeInTheDocument();
  });
});
