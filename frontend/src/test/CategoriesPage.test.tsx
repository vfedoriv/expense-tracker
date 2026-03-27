import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import CategoriesPage from '../pages/CategoriesPage'
import { AuthProvider } from '../contexts/AuthContext'

const mockCategories = [
  { id: 1, name: 'Groceries', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'Transport', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
]

function renderPage() {
  localStorage.setItem('fakeUserEmail', 'test@test.com')
  return render(
    <AuthProvider>
      <MemoryRouter>
        <CategoriesPage />
      </MemoryRouter>
    </AuthProvider>
  )
}

describe('CategoriesPage', () => {
  it('shows loading then category list', async () => {
    server.use(http.get('/api/categories', () => HttpResponse.json(mockCategories)))
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Groceries')).toBeTruthy()
      expect(screen.getByText('Transport')).toBeTruthy()
    })
  })

  it('shows empty state when no categories', async () => {
    server.use(http.get('/api/categories', () => HttpResponse.json([])))
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('No categories yet')).toBeTruthy()
    })
  })

  it('can create a new category', async () => {
    server.use(
      http.get('/api/categories', () => HttpResponse.json(mockCategories)),
      http.post('/api/categories', () => HttpResponse.json({ id: 3, name: 'Entertainment', createdAt: '', updatedAt: '' }))
    )
    renderPage()
    await waitFor(() => screen.getByText('Groceries'))
    await userEvent.click(screen.getByText('New category'))
    const input = screen.getByPlaceholderText('e.g. Groceries')
    await userEvent.type(input, 'Entertainment')
    server.use(http.get('/api/categories', () => HttpResponse.json([...mockCategories, { id: 3, name: 'Entertainment', createdAt: '', updatedAt: '' }])))
    await userEvent.click(screen.getByRole('button', { name: 'Create' }))
    await waitFor(() => {
      expect(screen.getByText('Entertainment')).toBeTruthy()
    })
  })

  it('shows validation error when name is empty', async () => {
    server.use(http.get('/api/categories', () => HttpResponse.json([])))
    renderPage()
    await waitFor(() => screen.getByText('New category'))
    await userEvent.click(screen.getByText('New category'))
    await userEvent.click(screen.getByRole('button', { name: 'Create' }))
    expect(screen.getByText('Name is required')).toBeTruthy()
  })

  it('can delete a category', async () => {
    server.use(
      http.get('/api/categories', () => HttpResponse.json(mockCategories)),
      http.delete('/api/categories/1', () => new HttpResponse(null, { status: 204 }))
    )
    renderPage()
    await waitFor(() => screen.getByText('Groceries'))
    // Click the Delete button on the first category row
    const deleteButtons = screen.getAllByRole('button', { name: 'Delete' })
    await userEvent.click(deleteButtons[0])
    // Confirm dialog should appear; modal Delete button is the last Delete button
    await waitFor(() => screen.getByText(/cannot be undone/))
    server.use(http.get('/api/categories', () => HttpResponse.json([mockCategories[1]])))
    const confirmButtons = screen.getAllByRole('button', { name: 'Delete' })
    await userEvent.click(confirmButtons[confirmButtons.length - 1])
    await waitFor(() => {
      expect(screen.queryByText('Groceries')).toBeNull()
    })
  })
})
