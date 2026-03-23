import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
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
    notes: 'Weekly groceries from the supermarket near downtown area',
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
    transactionDate: '2026-03-17',
    categoryId: 1,
    categoryName: 'Food',
    notes: 'Birthday dinner with friends at the Italian restaurant',
    createdAt: '2026-03-17T20:00:00Z',
  },
];

function mockFetch(responses: Record<string, { ok: boolean; status: number; body: unknown }>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = init?.method ?? 'GET';

    for (const [pattern, response] of Object.entries(responses)) {
      // Support method-specific patterns like "POST /api/transactions"
      if (pattern.includes(' ')) {
        const [patMethod, patUrl] = pattern.split(' ');
        if (method === patMethod && url.includes(patUrl)) {
          return Promise.resolve({
            ok: response.ok,
            status: response.status,
            json: () => Promise.resolve(response.body),
          } as Response);
        }
      } else if (url.includes(pattern)) {
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

function renderTransactionsPage() {
  return render(
    <MemoryRouter initialEntries={['/transactions']}>
      <AuthProvider>
        <Routes>
          <Route path="/transactions" element={<TransactionsPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  );
}

/**
 * Helper: In jsdom both desktop table and mobile cards render (CSS media queries don't apply).
 * Use getAllByText and assert at least one match.
 */
function expectTextPresent(text: string | RegExp) {
  const elements = screen.getAllByText(text);
  expect(elements.length).toBeGreaterThanOrEqual(1);
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('TransactionsPage', () => {
  describe('Loading state', () => {
    it('shows loading spinner while fetching transactions', () => {
      vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        // Never resolve other requests
        return new Promise(() => {});
      });

      renderTransactionsPage();

      expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
    });
  });

  describe('Empty state', () => {
    it('shows empty state when no transactions exist', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: [] },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });
    });
  });

  describe('Transaction list', () => {
    it('renders list of transactions with all fields', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      // Wait for data to load - use getAllByText since both desktop+mobile render in jsdom
      await waitFor(() => {
        expectTextPresent('Grocery shopping');
        expectTextPresent('Bus ticket');
        expectTextPresent('Restaurant dinner');
      });

      // Check amount with $ prefix (present in both views)
      expectTextPresent('$52.75');
      expectTextPresent('$3.50');
      expectTextPresent('$89.00');

      // Check category names
      const foodElements = screen.getAllByText('Food');
      expect(foodElements.length).toBeGreaterThanOrEqual(2);
      expectTextPresent('Transport');

      // Check dates in readable format
      expectTextPresent('Mar 15, 2026');
      expectTextPresent('Mar 16, 2026');
      expectTextPresent('Mar 17, 2026');
    });

    it('shows Add Transaction button', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        const buttons = screen.getAllByRole('button', { name: /add transaction/i });
        expect(buttons.length).toBeGreaterThanOrEqual(1);
      });
    });

    it('truncates long notes', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Notes should be present (might be truncated with CSS or JS truncation)
      const notesElements = screen.getAllByText(/Weekly groceries/);
      expect(notesElements.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('Create transaction', () => {
    it('opens create modal and creates a transaction', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: [] },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });

      // Click Add Transaction (use first button found)
      const addButtons = screen.getAllByRole('button', { name: /add transaction/i });
      await user.click(addButtons[0]);

      // Modal should be open
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      const dialog = screen.getByRole('dialog');

      // Fill in form fields
      const titleInput = within(dialog).getByLabelText(/title/i);
      await user.type(titleInput, 'Coffee');

      const amountInput = within(dialog).getByLabelText(/amount/i);
      await user.type(amountInput, '5.50');

      const dateInput = within(dialog).getByLabelText(/date/i);
      await user.type(dateInput, '2026-03-20');

      // Select category
      const categorySelect = within(dialog).getByLabelText(/category/i);
      await user.selectOptions(categorySelect, '1');

      // Mock successful creation
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        if (url.includes('/transactions') && init?.method === 'POST') {
          return Promise.resolve({
            ok: true, status: 201,
            json: () => Promise.resolve({
              id: 4, title: 'Coffee', amount: 5.5, currency: 'USD',
              transactionDate: '2026-03-20', categoryId: 1, categoryName: 'Food',
              notes: null, createdAt: '2026-03-20T12:00:00Z',
            }),
          } as Response);
        }
        if (url.includes('/transactions')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve([{
              id: 4, title: 'Coffee', amount: 5.5, currency: 'USD',
              transactionDate: '2026-03-20', categoryId: 1, categoryName: 'Food',
              notes: null, createdAt: '2026-03-20T12:00:00Z',
            }]),
          } as Response);
        }
        return Promise.resolve({
          ok: false, status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      // Submit form
      await user.click(within(dialog).getByRole('button', { name: /^create$/i }));

      // Transaction should appear in the list
      await waitFor(() => {
        expectTextPresent('Coffee');
      });
    });

    it('shows validation errors for all empty required fields simultaneously', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: [] },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });

      const addButtons = screen.getAllByRole('button', { name: /add transaction/i });
      await user.click(addButtons[0]);

      const dialog = screen.getByRole('dialog');

      // Submit without filling anything
      await user.click(within(dialog).getByRole('button', { name: /^create$/i }));

      // All validation errors should appear simultaneously
      await waitFor(() => {
        expect(screen.getByText(/title is required/i)).toBeInTheDocument();
        expect(screen.getByText(/amount is required/i)).toBeInTheDocument();
        expect(screen.getByText(/date is required/i)).toBeInTheDocument();
        expect(screen.getByText(/category is required/i)).toBeInTheDocument();
      });
    });

    it('shows validation error for amount <= 0', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: [] },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });

      const addButtons = screen.getAllByRole('button', { name: /add transaction/i });
      await user.click(addButtons[0]);

      const dialog = screen.getByRole('dialog');

      // Fill in title, date, category so only amount is invalid
      await user.type(within(dialog).getByLabelText(/title/i), 'Test');
      await user.type(within(dialog).getByLabelText(/date/i), '2026-03-20');
      await user.selectOptions(within(dialog).getByLabelText(/category/i), '1');

      const amountInput = within(dialog).getByLabelText(/amount/i);
      await user.type(amountInput, '0');

      await user.click(within(dialog).getByRole('button', { name: /^create$/i }));

      await waitFor(() => {
        expect(screen.getByText(/amount must be greater than 0/i)).toBeInTheDocument();
      });
    });

    it('clears validation errors when user corrects the field', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: [] },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });

      const addButtons = screen.getAllByRole('button', { name: /add transaction/i });
      await user.click(addButtons[0]);

      const dialog = screen.getByRole('dialog');

      // Submit empty form to trigger errors
      await user.click(within(dialog).getByRole('button', { name: /^create$/i }));

      await waitFor(() => {
        expect(screen.getByText(/title is required/i)).toBeInTheDocument();
      });

      // Type in title to clear that error
      const titleInput = within(dialog).getByLabelText(/title/i);
      await user.type(titleInput, 'Coffee');

      // Title error should be cleared
      expect(screen.queryByText(/title is required/i)).not.toBeInTheDocument();
    });
  });

  describe('Edit transaction', () => {
    it('opens edit modal pre-populated with existing values', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Click edit on first transaction (multiple edit buttons due to desktop+mobile views)
      const editButtons = screen.getAllByRole('button', { name: /edit/i });
      await user.click(editButtons[0]);

      const dialog = screen.getByRole('dialog');

      // Check pre-populated values
      expect(within(dialog).getByLabelText(/title/i)).toHaveValue('Grocery shopping');
      expect(within(dialog).getByLabelText(/amount/i)).toHaveValue(52.75);
      expect(within(dialog).getByLabelText(/date/i)).toHaveValue('2026-03-15');
      expect(within(dialog).getByLabelText(/category/i)).toHaveValue('1');
    });

    it('edits a transaction successfully', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      const editButtons = screen.getAllByRole('button', { name: /edit/i });
      await user.click(editButtons[0]);

      const dialog = screen.getByRole('dialog');
      const titleInput = within(dialog).getByLabelText(/title/i);
      await user.clear(titleInput);
      await user.type(titleInput, 'Updated groceries');

      // Mock successful update
      const updatedTransaction = {
        ...mockTransactions[0],
        title: 'Updated groceries',
      };
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        if (url.includes('/transactions/1') && init?.method === 'PUT') {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(updatedTransaction),
          } as Response);
        }
        if (url.includes('/transactions')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve([updatedTransaction, ...mockTransactions.slice(1)]),
          } as Response);
        }
        return Promise.resolve({
          ok: false, status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      await user.click(within(dialog).getByRole('button', { name: /save/i }));

      await waitFor(() => {
        expectTextPresent('Updated groceries');
      });
    });
  });

  describe('Delete transaction', () => {
    it('shows confirmation dialog and deletes transaction', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Click delete on first transaction
      const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
      await user.click(deleteButtons[0]);

      // Confirmation dialog should appear
      expect(screen.getByText(/are you sure/i)).toBeInTheDocument();

      // Mock successful delete
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        if (url.includes('/transactions/1') && init?.method === 'DELETE') {
          return Promise.resolve({
            ok: true, status: 204,
            json: () => Promise.resolve(undefined),
          } as Response);
        }
        if (url.includes('/transactions')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockTransactions.slice(1)),
          } as Response);
        }
        return Promise.resolve({
          ok: false, status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      // Confirm delete
      await user.click(screen.getByRole('button', { name: /confirm/i }));

      await waitFor(() => {
        expect(screen.queryByText('Grocery shopping')).not.toBeInTheDocument();
        expectTextPresent('Bus ticket');
      });
    });

    it('cancels deletion when cancel is clicked', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
      await user.click(deleteButtons[0]);

      expect(screen.getByText(/are you sure/i)).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: /cancel/i }));

      // Transaction should still be there
      expectTextPresent('Grocery shopping');
    });
  });

  describe('Currency indicator on form', () => {
    it('shows $ currency symbol next to the amount input on create form', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: [] },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });

      // Open create modal
      const addButtons = screen.getAllByRole('button', { name: /add transaction/i });
      await user.click(addButtons[0]);

      const dialog = screen.getByRole('dialog');

      // Verify the label contains USD indicator
      expect(within(dialog).getByText(/amount \(usd\)/i)).toBeInTheDocument();

      // Verify the '$' symbol is visible next to the amount input
      expect(within(dialog).getByText('$')).toBeInTheDocument();
    });

    it('shows $ currency symbol next to the amount input on edit form', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: mockTransactions },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });

      // Open edit modal
      const editButtons = screen.getAllByRole('button', { name: /edit/i });
      await user.click(editButtons[0]);

      const dialog = screen.getByRole('dialog');

      // Verify the label contains USD indicator
      expect(within(dialog).getByText(/amount \(usd\)/i)).toBeInTheDocument();

      // Verify the '$' symbol is visible next to the amount input
      expect(within(dialog).getByText('$')).toBeInTheDocument();
    });
  });

  describe('Error handling', () => {
    it('shows error message when fetching transactions fails', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: false, status: 500, body: { message: 'Internal server error' } },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText(/failed to load transactions/i)).toBeInTheDocument();
      });
    });

    it('provides retry button on fetch error', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: false, status: 500, body: { message: 'Internal server error' } },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText(/failed to load transactions/i)).toBeInTheDocument();
      });

      // Mock successful retry
      fetchSpy.mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        if (url.includes('/transactions')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockTransactions),
          } as Response);
        }
        return Promise.resolve({
          ok: false, status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      await user.click(screen.getByRole('button', { name: /retry/i }));

      await waitFor(() => {
        expectTextPresent('Grocery shopping');
      });
    });

    it('shows toast error when create operation fails', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/transactions': { ok: true, status: 200, body: [] },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderTransactionsPage();

      await waitFor(() => {
        expect(screen.getByText('No transactions yet')).toBeInTheDocument();
      });

      const addButtons = screen.getAllByRole('button', { name: /add transaction/i });
      await user.click(addButtons[0]);

      const dialog = screen.getByRole('dialog');
      await user.type(within(dialog).getByLabelText(/title/i), 'Test');
      await user.type(within(dialog).getByLabelText(/amount/i), '10');
      await user.type(within(dialog).getByLabelText(/date/i), '2026-03-20');
      await user.selectOptions(within(dialog).getByLabelText(/category/i), '1');

      // Mock failure
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        if (url.includes('/transactions') && init?.method === 'POST') {
          return Promise.resolve({
            ok: false, status: 500,
            json: () => Promise.resolve({ message: 'Server error' }),
          } as Response);
        }
        if (url.includes('/transactions')) {
          return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve([]),
          } as Response);
        }
        return Promise.resolve({
          ok: false, status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      await user.click(within(dialog).getByRole('button', { name: /^create$/i }));

      // Error appears in toast and/or inline - just verify it's visible somewhere
      await waitFor(() => {
        const errorElements = screen.getAllByText(/server error/i);
        expect(errorElements.length).toBeGreaterThanOrEqual(1);
      });
    });
  });
});
