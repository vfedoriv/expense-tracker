import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import { TransactionsPage } from '../pages/TransactionsPage';
import type { User, Transaction, Category } from '../types';

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

const mockCategories: Category[] = [
  { id: 1, name: 'Food', createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'Transport', createdAt: '2026-01-02T00:00:00Z' },
  { id: 3, name: 'Entertainment', createdAt: '2026-01-03T00:00:00Z' },
];

const mockTransactions: Transaction[] = [
  {
    id: 1,
    title: 'Grocery shopping',
    amount: 52.75,
    currency: 'USD',
    transactionDate: '2026-03-15',
    categoryId: 1,
    categoryName: 'Food',
    notes: 'Weekly groceries from the supermarket',
    createdAt: '2026-03-15T10:00:00Z',
  },
  {
    id: 2,
    title: 'Bus ticket',
    amount: 3.5,
    currency: 'USD',
    transactionDate: '2026-03-16',
    categoryId: 2,
    categoryName: 'Transport',
    notes: null,
    createdAt: '2026-03-16T08:30:00Z',
  },
  {
    id: 3,
    title: 'Restaurant dinner',
    amount: 89.0,
    currency: 'USD',
    transactionDate: '2026-02-17',
    categoryId: 1,
    categoryName: 'Food',
    notes: 'Birthday celebration dinner',
    createdAt: '2026-02-17T20:00:00Z',
  },
];

/**
 * Track fetch calls so we can verify that correct query params are sent to the API.
 */
function setupMockFetch(transactions: Transaction[] = mockTransactions) {
  const fetchCalls: { url: string; method: string }[] = [];

  const spy = vi
    .spyOn(globalThis, 'fetch')
    .mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      const method = init?.method ?? 'GET';
      fetchCalls.push({ url, method });

      if (url.includes('/users/me')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(mockUser),
        } as Response);
      }
      if (url.includes('/categories')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(mockCategories),
        } as Response);
      }
      if (url.includes('/transactions')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(transactions),
        } as Response);
      }
      return Promise.resolve({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ message: 'Not found' }),
      } as Response);
    });

  return { spy, fetchCalls };
}

function renderTransactionsPage() {
  return render(
    <MemoryRouter initialEntries={['/transactions']}>
      <AuthProvider>
        <Routes>
          <Route path="/transactions" element={<TransactionsPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function expectTextPresent(text: string | RegExp) {
  const elements = screen.getAllByText(text);
  expect(elements.length).toBeGreaterThanOrEqual(1);
}

beforeEach(() => {
  vi.restoreAllMocks();
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('Transaction Search & Filters', () => {
  describe('Search input', () => {
    it('renders search input with correct placeholder', async () => {
      vi.useRealTimers();
      setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      const searchInput = screen.getByPlaceholderText('Search transactions...');
      expect(searchInput).toBeInTheDocument();
    });

    it('debounces search input and sends search param to API', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      const { fetchCalls } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Clear tracked calls so far
      fetchCalls.length = 0;

      const searchInput = screen.getByPlaceholderText('Search transactions...');
      await user.type(searchInput, 'grocery');

      // Before debounce fires, no search request should be made
      const searchCallsBefore = fetchCalls.filter(
        (c) => c.url.includes('/transactions') && c.url.includes('search='),
      );
      expect(searchCallsBefore.length).toBe(0);

      // Advance timers past debounce
      vi.advanceTimersByTime(350);

      await waitFor(() => {
        const searchCalls = fetchCalls.filter(
          (c) => c.url.includes('/transactions') && c.url.includes('search=grocery'),
        );
        expect(searchCalls.length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe('Filter controls', () => {
    it('shows Filters toggle button', async () => {
      vi.useRealTimers();
      setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      expect(screen.getByRole('button', { name: /filters/i })).toBeInTheDocument();
    });

    it('toggles filter panel visibility', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Filter panel should not be visible yet
      expect(screen.queryByLabelText(/category/i)).not.toBeInTheDocument();

      // Click Filters button
      await user.click(screen.getByRole('button', { name: /filters/i }));

      // Filter panel should now be visible
      expect(screen.getByLabelText(/category/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/date range/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/minimum amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/maximum amount/i)).toBeInTheDocument();
    });

    it('sends categoryId query param when category filter is selected', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { fetchCalls } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open filters
      await user.click(screen.getByRole('button', { name: /filters/i }));

      fetchCalls.length = 0;

      // Select Food category
      const categorySelect = screen.getByLabelText(/category/i);
      await user.selectOptions(categorySelect, '1');

      await waitFor(() => {
        const catCalls = fetchCalls.filter(
          (c) => c.url.includes('/transactions') && c.url.includes('categoryId=1'),
        );
        expect(catCalls.length).toBeGreaterThanOrEqual(1);
      });
    });

    it('sends date range params for This Month preset', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { fetchCalls } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open filters
      await user.click(screen.getByRole('button', { name: /filters/i }));

      fetchCalls.length = 0;

      // Select "This Month" preset
      const datePreset = screen.getByLabelText(/date range/i);
      await user.selectOptions(datePreset, 'this-month');

      await waitFor(() => {
        const dateCalls = fetchCalls.filter(
          (c) =>
            c.url.includes('/transactions') &&
            c.url.includes('dateFrom=') &&
            c.url.includes('dateTo='),
        );
        expect(dateCalls.length).toBeGreaterThanOrEqual(1);
      });
    });

    it('sends date range params for Last Month preset', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { fetchCalls } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open filters
      await user.click(screen.getByRole('button', { name: /filters/i }));

      fetchCalls.length = 0;

      const datePreset = screen.getByLabelText(/date range/i);
      await user.selectOptions(datePreset, 'last-month');

      await waitFor(() => {
        const dateCalls = fetchCalls.filter(
          (c) =>
            c.url.includes('/transactions') &&
            c.url.includes('dateFrom=') &&
            c.url.includes('dateTo='),
        );
        expect(dateCalls.length).toBeGreaterThanOrEqual(1);
      });
    });

    it('shows custom date pickers when Custom is selected', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open filters
      await user.click(screen.getByRole('button', { name: /filters/i }));

      // Select Custom preset
      const datePreset = screen.getByLabelText(/date range/i);
      await user.selectOptions(datePreset, 'custom');

      // Date pickers should appear
      expect(screen.getByLabelText(/from date/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/to date/i)).toBeInTheDocument();
    });

    it('sends amount range params when min/max amount are set', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { fetchCalls } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open filters
      await user.click(screen.getByRole('button', { name: /filters/i }));

      fetchCalls.length = 0;

      const minInput = screen.getByLabelText(/minimum amount/i);
      await user.type(minInput, '10');

      await waitFor(() => {
        const amountCalls = fetchCalls.filter(
          (c) => c.url.includes('/transactions') && c.url.includes('amountMin=10'),
        );
        expect(amountCalls.length).toBeGreaterThanOrEqual(1);
      });

      fetchCalls.length = 0;

      const maxInput = screen.getByLabelText(/maximum amount/i);
      await user.type(maxInput, '100');

      await waitFor(() => {
        const amountCalls = fetchCalls.filter(
          (c) => c.url.includes('/transactions') && c.url.includes('amountMax=100'),
        );
        expect(amountCalls.length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe('No results empty state', () => {
    it('shows "No transactions found" when filters return empty results', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      const { spy } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Now mock fetch to return empty transactions for search
      spy.mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        if (url.includes('/transactions')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve([]),
          } as Response);
        }
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      // Type in search to trigger filtered empty state
      const searchInput = screen.getByPlaceholderText('Search transactions...');
      await user.type(searchInput, 'nonexistent');

      vi.advanceTimersByTime(350);

      await waitFor(() => {
        expect(screen.getByText('No transactions found')).toBeInTheDocument();
      });

      // Should have a "Try adjusting" description
      expect(screen.getByText(/try adjusting your search or filters/i)).toBeInTheDocument();
    });

    it('shows "No transactions yet" when no filters are active and list is empty', async () => {
      vi.useRealTimers();
      setupMockFetch([]);
      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });
    });
  });

  describe('Clear Filters', () => {
    it('shows Clear Filters button when filters are active', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // No Clear Filters button initially
      expect(screen.queryByRole('button', { name: /clear filters/i })).not.toBeInTheDocument();

      // Open filters and select a category
      await user.click(screen.getByRole('button', { name: /filters/i }));
      const categorySelect = screen.getByLabelText(/category/i);
      await user.selectOptions(categorySelect, '1');

      // Now Clear Filters button should appear
      await waitFor(() => {
        expect(screen.getByRole('button', { name: /clear filters/i })).toBeInTheDocument();
      });
    });

    it('resets all filters when Clear Filters is clicked', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { fetchCalls } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open filters and apply a category filter
      await user.click(screen.getByRole('button', { name: /filters/i }));
      await user.selectOptions(screen.getByLabelText(/category/i), '1');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /clear filters/i })).toBeInTheDocument();
      });

      fetchCalls.length = 0;

      // Click Clear Filters
      await user.click(screen.getByRole('button', { name: /clear filters/i }));

      // Should fetch without any filter params
      await waitFor(() => {
        const unfiltered = fetchCalls.filter(
          (c) =>
            c.url.includes('/transactions') &&
            !c.url.includes('categoryId=') &&
            !c.url.includes('search='),
        );
        expect(unfiltered.length).toBeGreaterThanOrEqual(1);
      });

      // Category select should be reset (re-query after state change)
      await waitFor(() => {
        const categorySelect = screen.getByLabelText(/category/i);
        expect(categorySelect).toHaveValue('');
      });
    });

    it('clears search input when Clear Filters is clicked', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Type in search
      const searchInput = screen.getByPlaceholderText('Search transactions...');
      await user.type(searchInput, 'test search');
      vi.advanceTimersByTime(350);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /clear filters/i })).toBeInTheDocument();
      });

      // Click Clear Filters
      await user.click(screen.getByRole('button', { name: /clear filters/i }));

      // Search input should be empty after re-render
      await waitFor(() => {
        expect(screen.getByPlaceholderText('Search transactions...')).toHaveValue('');
      });
    });
  });

  describe('Combined filters (AND logic)', () => {
    it('sends multiple filter params simultaneously', async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { fetchCalls } = setupMockFetch();
      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open filter panel
      await user.click(screen.getByRole('button', { name: /filters/i }));

      // Select category
      await user.selectOptions(screen.getByLabelText(/category/i), '1');

      // Wait for category filter to be applied
      await waitFor(() => {
        const catCalls = fetchCalls.filter(
          (c) => c.url.includes('/transactions') && c.url.includes('categoryId=1'),
        );
        expect(catCalls.length).toBeGreaterThanOrEqual(1);
      });

      // Select date preset (filter panel stays open due to filtersExpanded state)
      await user.selectOptions(screen.getByLabelText(/date range/i), 'this-month');

      await waitFor(() => {
        const dateCalls = fetchCalls.filter(
          (c) => c.url.includes('/transactions') && c.url.includes('dateFrom='),
        );
        expect(dateCalls.length).toBeGreaterThanOrEqual(1);
      });

      fetchCalls.length = 0;

      // Set min amount
      const minInput = screen.getByLabelText(/minimum amount/i);
      await user.type(minInput, '10');

      // Verify combined params
      await waitFor(() => {
        const combinedCalls = fetchCalls.filter(
          (c) =>
            c.url.includes('/transactions') &&
            c.url.includes('categoryId=1') &&
            c.url.includes('dateFrom=') &&
            c.url.includes('amountMin=10'),
        );
        expect(combinedCalls.length).toBeGreaterThanOrEqual(1);
      });
    });
  });
});
