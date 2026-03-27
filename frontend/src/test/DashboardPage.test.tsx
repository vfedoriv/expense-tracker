import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import DashboardPage from '../pages/DashboardPage'
import { AuthProvider } from '../contexts/AuthContext'

const mockBudgetSet = {
  year: 2026, month: 3,
  budget: 1000, totalSpent: 800, remaining: 200, usagePercent: 80, budgetSet: true,
}
const mockBudgetUnset = {
  year: 2026, month: 3,
  budget: null, totalSpent: 250, remaining: null, usagePercent: null, budgetSet: false,
}
const mockTransactions = [
  { id: 1, title: 'Coffee', amount: 4.50, currency: 'USD', transactionDate: '2026-03-01', categoryId: 1, notes: null, createdAt: '', updatedAt: '' },
]

function renderPage() {
  localStorage.setItem('fakeUserEmail', 'test@test.com')
  return render(
    <AuthProvider>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </AuthProvider>
  )
}

describe('DashboardPage', () => {
  it('shows budget usage when budget is set', async () => {
    server.use(
      http.get('/api/budgets/:year/:month', () => HttpResponse.json(mockBudgetSet)),
      http.get('/api/transactions', () => HttpResponse.json(mockTransactions))
    )
    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText('Edit budget').length).toBeGreaterThan(0)
      expect(screen.getAllByText(/800\.00/).length).toBeGreaterThan(0)
    })
  })

  it('shows set budget prompt when no budget', async () => {
    server.use(
      http.get('/api/budgets/:year/:month', () => HttpResponse.json(mockBudgetUnset)),
      http.get('/api/transactions', () => HttpResponse.json([]))
    )
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('No budget set')).toBeTruthy()
      expect(screen.getByText('Set budget')).toBeTruthy()
    })
  })

  it('shows recent transactions', async () => {
    server.use(
      http.get('/api/budgets/:year/:month', () => HttpResponse.json(mockBudgetUnset)),
      http.get('/api/transactions', () => HttpResponse.json(mockTransactions))
    )
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Coffee')).toBeTruthy()
    })
  })

  it('shows budget form with validation', async () => {
    server.use(
      http.get('/api/budgets/:year/:month', () => HttpResponse.json(mockBudgetUnset)),
      http.get('/api/transactions', () => HttpResponse.json([]))
    )
    renderPage()
    await waitFor(() => screen.getByText('Set budget'))
    await userEvent.click(screen.getByText('Set budget'))
    // Submit empty form
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByText('Must be a positive number')).toBeTruthy()
  })
})
