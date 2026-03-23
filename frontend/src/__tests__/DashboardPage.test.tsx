import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import { DashboardPage } from '../pages/DashboardPage';
import type { User, DashboardData } from '../types';

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

const dashboardWithBudget: DashboardData = {
  totalSpent: 450.0,
  budgetAmount: 1000.0,
  remaining: 550.0,
  usagePercentage: 45.0,
  year: 2026,
  month: 3,
};

const dashboardNoBudget: DashboardData = {
  totalSpent: 150.0,
  budgetAmount: null,
  remaining: null,
  usagePercentage: null,
  year: 2026,
  month: 3,
};

const dashboardZeroSpent: DashboardData = {
  totalSpent: 0,
  budgetAmount: 500.0,
  remaining: 500.0,
  usagePercentage: 0,
  year: 2026,
  month: 3,
};

const dashboardOverBudget: DashboardData = {
  totalSpent: 1200.0,
  budgetAmount: 1000.0,
  remaining: -200.0,
  usagePercentage: 120.0,
  year: 2026,
  month: 3,
};

const dashboardHighUsage: DashboardData = {
  totalSpent: 850.0,
  budgetAmount: 1000.0,
  remaining: 150.0,
  usagePercentage: 85.0,
  year: 2026,
  month: 3,
};

const dashboardMediumUsage: DashboardData = {
  totalSpent: 600.0,
  budgetAmount: 1000.0,
  remaining: 400.0,
  usagePercentage: 60.0,
  year: 2026,
  month: 3,
};

function mockFetch(responses: Record<string, { ok: boolean; status: number; body: unknown }>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    for (const [pattern, response] of Object.entries(responses)) {
      if (url.includes(pattern)) {
        return Promise.resolve({
          ok: response.ok,
          status: response.status,
          json: () => Promise.resolve(response.body),
        } as Response);
      }
    }
    return Promise.resolve({
      ok: false,
      status: 404,
      json: () => Promise.resolve({ message: 'Not found' }),
    } as Response);
  });
}

function renderDashboardPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <AuthProvider>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
  // Mock Date to control "current month"
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date(2026, 2, 15)); // March 15, 2026
});

afterEach(() => {
  vi.useRealTimers();
});

describe('DashboardPage', () => {
  describe('Loading state', () => {
    it('shows loading spinner while fetching dashboard data', () => {
      vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        // Never resolve dashboard request
        return new Promise(() => {});
      });

      renderDashboardPage();

      expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
    });
  });

  describe('Error state', () => {
    it('shows error message when dashboard fetch fails', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: false, status: 500, body: { message: 'Server error' } },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('Server error')).toBeInTheDocument();
      });
    });

    it('shows retry button on error', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: false, status: 500, body: { message: 'Server error' } },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('Retry')).toBeInTheDocument();
      });
    });
  });

  describe('Dashboard with budget set', () => {
    it('displays total spent amount', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('$450.00')).toBeInTheDocument();
      });
    });

    it('displays budget amount', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('$1,000.00')).toBeInTheDocument();
      });
    });

    it('displays remaining budget', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('$550.00')).toBeInTheDocument();
      });
    });

    it('displays budget usage percentage', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('45.0%')).toBeInTheDocument();
      });
    });

    it('displays progress bar', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByRole('progressbar')).toBeInTheDocument();
      });
    });

    it('shows Edit Budget button', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('Edit Budget')).toBeInTheDocument();
      });
    });
  });

  describe('Progress bar color coding', () => {
    it('shows green progress bar when usage < 50%', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-green-500');
      });
    });

    it('shows yellow progress bar when usage is 50-80%', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardMediumUsage },
      });

      renderDashboardPage();

      await waitFor(() => {
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-yellow-500');
      });
    });

    it('shows red progress bar when usage > 80%', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardHighUsage },
      });

      renderDashboardPage();

      await waitFor(() => {
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-red-500');
      });
    });
  });

  describe('No budget state', () => {
    it('shows "No budget set" message when no budget exists', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set')).toBeInTheDocument();
      });
    });

    it('shows empty state with Set Budget button', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set for this month')).toBeInTheDocument();
        // Two "Set Budget" buttons: one in the card, one in the empty state
        const setButtons = screen.getAllByText('Set Budget');
        expect(setButtons.length).toBeGreaterThanOrEqual(1);
      });
    });

    it('does not show $0 budget or 0% when no budget', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set')).toBeInTheDocument();
      });

      // Should not show progress bar when no budget
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
      // Should not show "Remaining" label
      expect(screen.queryByText('Remaining')).not.toBeInTheDocument();
    });

    it('still shows total spent when no budget', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('$150.00')).toBeInTheDocument();
      });
    });
  });

  describe('Zero transactions with budget', () => {
    it('shows $0 spent with full remaining when budget set but no transactions', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardZeroSpent },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('$0.00')).toBeInTheDocument();
        // $500.00 appears in both Budget card and Remaining card
        const amounts = screen.getAllByText('$500.00');
        expect(amounts.length).toBe(2);
        expect(screen.getByText('0.0%')).toBeInTheDocument();
      });
    });
  });

  describe('Over budget', () => {
    it('shows negative remaining and over budget indicator', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardOverBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('-$200.00')).toBeInTheDocument();
        expect(screen.getByText('Over budget')).toBeInTheDocument();
      });
    });

    it('shows red progress bar when over budget', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardOverBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-red-500');
      });
    });
  });

  describe('Month selector', () => {
    it('defaults to current month', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      expect(screen.getByText('March 2026')).toBeInTheDocument();
    });

    it('navigates to previous month', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getAllByText('March 2026').length).toBeGreaterThanOrEqual(1);
      });

      await user.click(screen.getByLabelText('Previous month'));

      await waitFor(() => {
        expect(screen.getAllByText('February 2026').length).toBeGreaterThanOrEqual(1);
      });

      // Should have fetched with new month params
      const calls = fetchSpy.mock.calls.map((c) =>
        typeof c[0] === 'string' ? c[0] : c[0].toString()
      );
      expect(calls.some((url) => url.includes('month=2'))).toBe(true);
    });

    it('navigates to next month', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getAllByText('March 2026').length).toBeGreaterThanOrEqual(1);
      });

      await user.click(screen.getByLabelText('Next month'));

      await waitFor(() => {
        expect(screen.getAllByText('April 2026').length).toBeGreaterThanOrEqual(1);
      });
    });

    it('wraps from January to previous December', async () => {
      vi.setSystemTime(new Date(2026, 0, 15)); // January 2026
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getAllByText('January 2026').length).toBeGreaterThanOrEqual(1);
      });

      await user.click(screen.getByLabelText('Previous month'));

      await waitFor(() => {
        expect(screen.getAllByText('December 2025').length).toBeGreaterThanOrEqual(1);
      });
    });

    it('wraps from December to next January', async () => {
      vi.setSystemTime(new Date(2026, 11, 15)); // December 2026
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getAllByText('December 2026').length).toBeGreaterThanOrEqual(1);
      });

      await user.click(screen.getByLabelText('Next month'));

      await waitFor(() => {
        expect(screen.getAllByText('January 2027').length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe('Budget modal', () => {
    it('opens budget modal when Set Budget is clicked', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set for this month')).toBeInTheDocument();
      });

      // Click the Set Budget button in the empty state
      const setButtons = screen.getAllByText('Set Budget');
      await user.click(setButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });
    });

    it('opens Edit Budget modal with pre-filled amount', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('Edit Budget')).toBeInTheDocument();
      });

      await user.click(screen.getByText('Edit Budget'));

      await waitFor(() => {
        const dialog = screen.getByRole('dialog');
        const input = within(dialog).getByLabelText(/budget amount/i);
        expect(input).toHaveValue(1000);
      });
    });

    it('validates empty budget amount', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set for this month')).toBeInTheDocument();
      });

      const setButtons = screen.getAllByText('Set Budget');
      await user.click(setButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Click Save without entering amount
      await user.click(screen.getByText('Save'));

      await waitFor(() => {
        expect(screen.getByText('Budget amount is required')).toBeInTheDocument();
      });
    });

    it('validates budget amount must be > 0', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set for this month')).toBeInTheDocument();
      });

      const setButtons = screen.getAllByText('Set Budget');
      await user.click(setButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      const input = screen.getByLabelText(/budget amount/i);
      await user.type(input, '0');
      await user.click(screen.getByText('Save'));

      await waitFor(() => {
        expect(screen.getByText('Budget amount must be greater than 0')).toBeInTheDocument();
      });
    });

    it('closes modal on Cancel', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardNoBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set for this month')).toBeInTheDocument();
      });

      const setButtons = screen.getAllByText('Set Budget');
      await user.click(setButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      await user.click(screen.getByText('Cancel'));

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
    });

    it('saves budget and refreshes dashboard', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      let fetchCount = 0;
      vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/budgets') && init?.method === 'POST') {
          return Promise.resolve({
            ok: true,
            status: 201,
            json: () => Promise.resolve({
              id: 1,
              year: 2026,
              month: 3,
              amount: 2000,
              createdAt: '2026-03-15T00:00:00Z',
              updatedAt: '2026-03-15T00:00:00Z',
            }),
          } as Response);
        }
        if (url.includes('/dashboard')) {
          fetchCount++;
          if (fetchCount <= 1) {
            return Promise.resolve({
              ok: true,
              status: 200,
              json: () => Promise.resolve(dashboardNoBudget),
            } as Response);
          }
          // After saving budget, return dashboard with budget
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve({
              totalSpent: 150.0,
              budgetAmount: 2000.0,
              remaining: 1850.0,
              usagePercentage: 7.5,
              year: 2026,
              month: 3,
            }),
          } as Response);
        }
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('No budget set for this month')).toBeInTheDocument();
      });

      const setButtons = screen.getAllByText('Set Budget');
      await user.click(setButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      const input = screen.getByLabelText(/budget amount/i);
      await user.type(input, '2000');
      await user.click(screen.getByText('Save'));

      // Modal should close and dashboard should refresh
      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });

      await waitFor(() => {
        expect(screen.getByText('$2,000.00')).toBeInTheDocument();
      });
    });
  });

  describe('Stats labels', () => {
    it('shows Total Spent label', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('Total Spent')).toBeInTheDocument();
      });
    });

    it('shows Budget label', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('Budget')).toBeInTheDocument();
      });
    });

    it('shows Budget Usage label', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/dashboard': { ok: true, status: 200, body: dashboardWithBudget },
      });

      renderDashboardPage();

      await waitFor(() => {
        expect(screen.getByText('Budget Usage')).toBeInTheDocument();
      });
    });
  });
});
