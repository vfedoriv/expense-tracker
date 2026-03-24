import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import { LoginPage } from '../pages/LoginPage';
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

function renderLoginPage(authenticated = false) {
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
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<div>Dashboard Page</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('LoginPage', () => {
  it('renders the app name', async () => {
    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByText('Expense Tracker')).toBeInTheDocument();
    });
  });

  it('renders sign-in subtitle', async () => {
    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByText('Sign in to manage your expenses')).toBeInTheDocument();
    });
  });

  it('renders Continue with Google button', async () => {
    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByTestId('google-login-btn')).toBeInTheDocument();
      expect(screen.getByText('Continue with Google')).toBeInTheDocument();
    });
  });

  it('renders Continue with GitHub button', async () => {
    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByTestId('github-login-btn')).toBeInTheDocument();
      expect(screen.getByText('Continue with GitHub')).toBeInTheDocument();
    });
  });

  it('Google button links to /oauth2/authorization/google', async () => {
    renderLoginPage();

    await waitFor(() => {
      const googleBtn = screen.getByTestId('google-login-btn');
      expect(googleBtn.tagName).toBe('A');
      expect(googleBtn).toHaveAttribute('href', '/oauth2/authorization/google');
    });
  });

  it('GitHub button links to /oauth2/authorization/github', async () => {
    renderLoginPage();

    await waitFor(() => {
      const githubBtn = screen.getByTestId('github-login-btn');
      expect(githubBtn.tagName).toBe('A');
      expect(githubBtn).toHaveAttribute('href', '/oauth2/authorization/github');
    });
  });

  it('redirects to dashboard when already authenticated', async () => {
    renderLoginPage(true);

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });
  });

  it('shows loading state while checking authentication', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise(() => {
        /* never resolves */
      }),
    );

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('renders the app logo icon', async () => {
    renderLoginPage();

    await waitFor(() => {
      // Check for the dollar sign icon SVG (app logo)
      const svgs = document.querySelectorAll('svg');
      expect(svgs.length).toBeGreaterThanOrEqual(1);
    });
  });
});
