import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
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

function renderLayout(initialPath = '/dashboard') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/dashboard" element={<div>Dashboard Content</div>} />
            <Route path="/transactions" element={<div>Transactions Content</div>} />
            <Route path="/categories" element={<div>Categories Content</div>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    json: () => Promise.resolve(mockUser),
  } as Response);
});

describe('Layout', () => {
  it('renders app name in header', async () => {
    renderLayout();

    await waitFor(() => {
      expect(screen.getByText('Expense Tracker')).toBeInTheDocument();
    });
  });

  it('renders navigation links', async () => {
    renderLayout();

    await waitFor(() => {
      const dashboardLinks = screen.getAllByText('Dashboard');
      expect(dashboardLinks.length).toBeGreaterThan(0);
      expect(screen.getAllByText('Transactions').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Categories').length).toBeGreaterThan(0);
    });
  });

  it('renders the user display name', async () => {
    renderLayout();

    await waitFor(() => {
      expect(screen.getByText('Test User')).toBeInTheDocument();
    });
  });

  it('renders page content via Outlet', async () => {
    renderLayout('/dashboard');

    await waitFor(() => {
      expect(screen.getByText('Dashboard Content')).toBeInTheDocument();
    });
  });

  it('navigates between pages', async () => {
    const user = userEvent.setup();
    renderLayout('/dashboard');

    await waitFor(() => {
      expect(screen.getByText('Dashboard Content')).toBeInTheDocument();
    });

    // Click Transactions link (use first occurrence, which is the desktop nav)
    const transactionsLinks = screen.getAllByText('Transactions');
    await user.click(transactionsLinks[0]);

    await waitFor(() => {
      expect(screen.getByText('Transactions Content')).toBeInTheDocument();
    });
  });
});
