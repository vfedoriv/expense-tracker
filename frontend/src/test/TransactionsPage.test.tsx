import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import TransactionsPage from '../pages/TransactionsPage'
import { AuthProvider } from '../contexts/AuthContext'

const mockCategories = [
  { id: 1, name: 'Groceries', createdAt: '', updatedAt: '' },
]

const mockTransactions = [
  { id: 1, title: 'Coffee', amount: 4.50, currency: 'USD', transactionDate: '2026-03-01', categoryId: 1, notes: null, createdAt: '', updatedAt: '' },
  { id: 2, title: 'Bus ticket', amount: 2.00, currency: 'USD', transactionDate: '2026-03-02', categoryId: null, notes: 'Monthly pass', createdAt: '', updatedAt: '' },
]

function renderPage() {
  localStorage.setItem('fakeUserEmail', 'test@test.com')
  return render(
    <AuthProvider>
      <MemoryRouter>
        <TransactionsPage />
      </MemoryRouter>
    </AuthProvider>
  )
}

describe('TransactionsPage', () => {
  it('shows transaction list', async () => {
    server.use(
      http.get('/api/categories', () => HttpResponse.json(mockCategories)),
      http.get('/api/transactions', () => HttpResponse.json(mockTransactions))
    )
    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText('Coffee').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Bus ticket').length).toBeGreaterThan(0)
    })
  })

  it('shows empty state when no transactions', async () => {
    server.use(
      http.get('/api/categories', () => HttpResponse.json([])),
      http.get('/api/transactions', () => HttpResponse.json([]))
    )
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('No transactions found')).toBeTruthy()
    })
  })

  it('shows validation error when title is empty', async () => {
    server.use(
      http.get('/api/categories', () => HttpResponse.json(mockCategories)),
      http.get('/api/transactions', () => HttpResponse.json([]))
    )
    renderPage()
    await waitFor(() => screen.getByText('Add transaction'))
    await userEvent.click(screen.getByText('Add transaction'))
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))
    expect(screen.getByText('Title is required')).toBeTruthy()
  })

  it('can create a transaction', async () => {
    const created = { id: 3, title: 'Lunch', amount: 12.50, currency: 'USD', transactionDate: '2026-03-27', categoryId: 1, notes: null, createdAt: '', updatedAt: '' }
    server.use(
      http.get('/api/categories', () => HttpResponse.json(mockCategories)),
      http.get('/api/transactions', () => HttpResponse.json([])),
      http.post('/api/transactions', () => HttpResponse.json(created))
    )
    renderPage()
    await waitFor(() => screen.getByText('Add transaction'))
    await userEvent.click(screen.getByText('Add transaction'))
    await userEvent.type(screen.getByPlaceholderText('e.g. Coffee'), 'Lunch')
    const inputs = screen.getAllByRole('spinbutton')
    await userEvent.clear(inputs[0])
    await userEvent.type(inputs[0], '12.50')
    server.use(http.get('/api/transactions', () => HttpResponse.json([created])))
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))
    await waitFor(() => {
      expect(screen.getAllByText('Lunch').length).toBeGreaterThan(0)
    })
  })

  it('shows notes in transaction list', async () => {
    server.use(
      http.get('/api/categories', () => HttpResponse.json(mockCategories)),
      http.get('/api/transactions', () => HttpResponse.json(mockTransactions))
    )
    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText('Monthly pass').length).toBeGreaterThan(0)
    })
  })
})
