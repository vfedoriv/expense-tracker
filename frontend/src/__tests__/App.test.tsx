import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import App from '../App';
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

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('App', () => {
  it('renders and redirects to dashboard when authenticated', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockUser),
    } as Response);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Expense Tracker')).toBeInTheDocument();
      expect(screen.getAllByText('Dashboard').length).toBeGreaterThan(0);
    });
  });

  it('shows loading state while authenticating', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise(() => {
        /* never resolves */
      })
    );

    render(<App />);

    expect(screen.getByText('Authenticating...')).toBeInTheDocument();
  });

  it('redirects to login when authentication fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ message: 'Unauthorized' }),
    } as Response);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Expense Tracker')).toBeInTheDocument();
      expect(screen.getByText('Sign in to manage your expenses')).toBeInTheDocument();
    });
  });
});
