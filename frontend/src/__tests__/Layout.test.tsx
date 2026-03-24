import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import { Layout } from '../components/layout/Layout';
import type { User } from '../types';

const mockUser: User = {
  id: 1,
  provider: 'google',
  providerUserId: 'google-user-1',
  email: 'user@gmail.com',
  displayName: 'Test User',
  avatarUrl: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockUserWithAvatar: User = {
  ...mockUser,
  avatarUrl: 'https://example.com/avatar.jpg',
};

function renderLayout(initialPath = '/dashboard', user: User = mockUser) {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    json: () => Promise.resolve(user),
  } as Response);

  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route element={<Layout />}>
            <Route path="/dashboard" element={<div>Dashboard Content</div>} />
            <Route path="/transactions" element={<div>Transactions Content</div>} />
            <Route path="/categories" element={<div>Categories Content</div>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
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

  it('renders logout button', async () => {
    renderLayout();

    await waitFor(() => {
      expect(screen.getByTestId('logout-btn')).toBeInTheDocument();
    });
  });

  it('redirects to login page after clicking logout', async () => {
    const user = userEvent.setup();
    renderLayout();

    await waitFor(() => {
      expect(screen.getByTestId('logout-btn')).toBeInTheDocument();
    });

    await user.click(screen.getByTestId('logout-btn'));

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
  });

  it('shows avatar placeholder when no avatar URL', async () => {
    renderLayout('/dashboard', mockUser);

    await waitFor(() => {
      expect(screen.getByTestId('user-avatar-placeholder')).toBeInTheDocument();
      expect(screen.getByTestId('user-avatar-placeholder').textContent).toBe('T');
    });
  });

  it('shows avatar image when avatar URL is provided', async () => {
    renderLayout('/dashboard', mockUserWithAvatar);

    await waitFor(() => {
      expect(screen.getByTestId('user-avatar')).toBeInTheDocument();
      expect(screen.getByTestId('user-avatar')).toHaveAttribute(
        'src',
        'https://example.com/avatar.jpg',
      );
    });
  });
});
