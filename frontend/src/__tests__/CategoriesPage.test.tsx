import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import { CategoriesPage } from '../pages/CategoriesPage';
import type { User, Category } from '../types';

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

function renderCategoriesPage() {
  return render(
    <MemoryRouter initialEntries={['/categories']}>
      <AuthProvider>
        <Routes>
          <Route path="/categories" element={<CategoriesPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('CategoriesPage', () => {
  describe('Loading state', () => {
    it('shows loading spinner while fetching categories', () => {
      vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        // Never resolve categories request
        return new Promise(() => {});
      });

      renderCategoriesPage();

      expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
    });
  });

  describe('Empty state', () => {
    it('shows empty state when no categories exist', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: [] },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('No categories yet')).toBeInTheDocument();
        expect(screen.getByText('Create one to get started.')).toBeInTheDocument();
      });
    });
  });

  describe('Category list', () => {
    it('renders list of categories', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
        expect(screen.getByText('Transport')).toBeInTheDocument();
        expect(screen.getByText('Entertainment')).toBeInTheDocument();
      });
    });

    it('shows edit and delete buttons for each category', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        const editButtons = screen.getAllByRole('button', { name: /edit/i });
        const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
        expect(editButtons).toHaveLength(3);
        expect(deleteButtons).toHaveLength(3);
      });
    });
  });

  describe('Create category', () => {
    it('opens create modal and creates a category', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: [] },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('No categories yet')).toBeInTheDocument();
      });

      // Click the first create button (header button)
      const createButtons = screen.getAllByRole('button', { name: /create category/i });
      await user.click(createButtons[0]);

      // Modal should be open
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      // Type category name
      const nameInput = screen.getByLabelText(/name/i);
      await user.type(nameInput, 'Food');

      // Update fetch to return the new category after creation
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories') && init?.method === 'POST') {
          return Promise.resolve({
            ok: true,
            status: 201,
            json: () => Promise.resolve({ id: 1, name: 'Food', createdAt: '2026-01-01T00:00:00Z' }),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () =>
              Promise.resolve([{ id: 1, name: 'Food', createdAt: '2026-01-01T00:00:00Z' }]),
          } as Response);
        }
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      // Submit the form
      await user.click(screen.getByRole('button', { name: /^create$/i }));

      // Category should appear in the list
      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });
    });

    it('shows validation error when name is empty', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: [] },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('No categories yet')).toBeInTheDocument();
      });

      const createButtons = screen.getAllByRole('button', { name: /create category/i });
      await user.click(createButtons[0]);

      // Click create without typing
      await user.click(screen.getByRole('button', { name: /^create$/i }));

      // Validation error should show
      expect(screen.getByText(/name is required/i)).toBeInTheDocument();
    });

    it('shows validation error for whitespace-only name', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: [] },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('No categories yet')).toBeInTheDocument();
      });

      const createButtons = screen.getAllByRole('button', { name: /create category/i });
      await user.click(createButtons[0]);

      const nameInput = screen.getByLabelText(/name/i);
      await user.type(nameInput, '   ');

      await user.click(screen.getByRole('button', { name: /^create$/i }));

      expect(screen.getByText(/name is required/i)).toBeInTheDocument();
    });

    it('shows error when duplicate category name (409)', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });

      await user.click(screen.getByRole('button', { name: /create category/i }));

      const nameInput = screen.getByLabelText(/name/i);
      await user.type(nameInput, 'Food');

      // Mock 409 response for POST
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories') && init?.method === 'POST') {
          return Promise.resolve({
            ok: false,
            status: 409,
            json: () => Promise.resolve({ message: 'Category with this name already exists' }),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      await user.click(screen.getByRole('button', { name: /^create$/i }));

      await waitFor(() => {
        expect(screen.getByText(/already exists/i)).toBeInTheDocument();
      });
    });
  });

  describe('Edit category', () => {
    it('opens edit modal pre-populated with current name and renames', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });

      // Click edit on the first category
      const editButtons = screen.getAllByRole('button', { name: /edit/i });
      await user.click(editButtons[0]);

      // Modal should be open with pre-populated name
      const dialog = screen.getByRole('dialog');
      const nameInput = within(dialog).getByLabelText(/name/i);
      expect(nameInput).toHaveValue('Food');

      // Clear and type new name
      await user.clear(nameInput);
      await user.type(nameInput, 'Groceries');

      // Mock successful rename
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories/1') && init?.method === 'PUT') {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () =>
              Promise.resolve({ id: 1, name: 'Groceries', createdAt: '2026-01-01T00:00:00Z' }),
          } as Response);
        }
        if (url.includes('/categories') && !init?.method) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () =>
              Promise.resolve([
                { id: 1, name: 'Groceries', createdAt: '2026-01-01T00:00:00Z' },
                ...mockCategories.slice(1),
              ]),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () =>
              Promise.resolve([
                { id: 1, name: 'Groceries', createdAt: '2026-01-01T00:00:00Z' },
                ...mockCategories.slice(1),
              ]),
          } as Response);
        }
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      await user.click(screen.getByRole('button', { name: /save/i }));

      await waitFor(() => {
        expect(screen.getByText('Groceries')).toBeInTheDocument();
      });
    });

    it('shows validation error when rename to empty', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });

      const editButtons = screen.getAllByRole('button', { name: /edit/i });
      await user.click(editButtons[0]);

      const dialog = screen.getByRole('dialog');
      const nameInput = within(dialog).getByLabelText(/name/i);
      await user.clear(nameInput);

      await user.click(screen.getByRole('button', { name: /save/i }));

      expect(screen.getByText(/name is required/i)).toBeInTheDocument();
    });
  });

  describe('Delete category', () => {
    it('shows confirmation dialog and deletes category', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });

      // Click delete on first category
      const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
      await user.click(deleteButtons[0]);

      // Confirmation dialog should appear
      expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
      expect(screen.getByText(/this cannot be undone/i)).toBeInTheDocument();

      // Mock successful delete
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories/1') && init?.method === 'DELETE') {
          return Promise.resolve({
            ok: true,
            status: 204,
            json: () => Promise.resolve(undefined),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockCategories.slice(1)),
          } as Response);
        }
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      // Confirm delete
      await user.click(screen.getByRole('button', { name: /confirm/i }));

      await waitFor(() => {
        expect(screen.queryByText('Food')).not.toBeInTheDocument();
        expect(screen.getByText('Transport')).toBeInTheDocument();
      });
    });

    it('cancels deletion when cancel is clicked', async () => {
      const user = userEvent.setup();
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });

      const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
      await user.click(deleteButtons[0]);

      expect(screen.getByText(/are you sure/i)).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: /cancel/i }));

      // Category should still be there
      expect(screen.getByText('Food')).toBeInTheDocument();
    });

    it('shows error when delete is blocked (409 - transactions exist)', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });

      const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
      await user.click(deleteButtons[0]);

      // Mock 409 response for DELETE
      fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url.includes('/users/me')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockUser),
          } as Response);
        }
        if (url.includes('/categories/1') && init?.method === 'DELETE') {
          return Promise.resolve({
            ok: false,
            status: 409,
            json: () =>
              Promise.resolve({ message: 'Cannot delete category with existing transactions' }),
          } as Response);
        }
        if (url.includes('/categories')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockCategories),
          } as Response);
        }
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      await user.click(screen.getByRole('button', { name: /confirm/i }));

      await waitFor(() => {
        expect(
          screen.getByText(/cannot delete category with existing transactions/i),
        ).toBeInTheDocument();
      });

      // Category should still exist in the list
      const categoryCards = screen.getAllByText('Food');
      expect(categoryCards.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('Error handling', () => {
    it('shows error message when fetching categories fails', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: false, status: 500, body: { message: 'Internal server error' } },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText(/failed to load categories/i)).toBeInTheDocument();
      });
    });

    it('provides retry button on fetch error', async () => {
      const user = userEvent.setup();
      const fetchSpy = mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: false, status: 500, body: { message: 'Internal server error' } },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByText(/failed to load categories/i)).toBeInTheDocument();
      });

      // Mock successful retry
      fetchSpy.mockImplementation((input: RequestInfo | URL) => {
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
        return Promise.resolve({
          ok: false,
          status: 404,
          json: () => Promise.resolve({ message: 'Not found' }),
        } as Response);
      });

      await user.click(screen.getByRole('button', { name: /retry/i }));

      await waitFor(() => {
        expect(screen.getByText('Food')).toBeInTheDocument();
      });
    });
  });

  describe('Create Category button', () => {
    it('shows Create Category button at the top', async () => {
      mockFetch({
        '/users/me': { ok: true, status: 200, body: mockUser },
        '/categories': { ok: true, status: 200, body: mockCategories },
      });

      renderCategoriesPage();

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /create category/i })).toBeInTheDocument();
      });
    });
  });
});
