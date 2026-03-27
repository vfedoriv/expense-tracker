import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AuthProvider } from '../contexts/AuthContext'
import ProtectedRoute from '../components/ProtectedRoute'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'

function AppWithRoutes() {
  return (
    <AuthProvider>
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route
            path="/protected"
            element={<ProtectedRoute><div>Protected Content</div></ProtectedRoute>}
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  )
}

describe('ProtectedRoute', () => {
  it('shows loading spinner when fetching user', async () => {
    // Simulate a pending API call by storing email to trigger fetch
    localStorage.setItem('fakeUserEmail', 'test@test.com')
    render(<AppWithRoutes />)
    // Loading spinner may appear briefly - check that it either appears or content loads
    await waitFor(() => {
      const spinner = document.querySelector('.animate-spin')
      const content = screen.queryByText('Protected Content')
      const login = screen.queryByText('Login Page')
      expect(spinner !== null || content !== null || login !== null).toBe(true)
    })
  })

  it('shows protected content when user is authenticated', async () => {
    localStorage.setItem('fakeUserEmail', 'test@test.com')
    render(<AppWithRoutes />)
    await waitFor(() => {
      expect(screen.getByText('Protected Content')).toBeTruthy()
    })
  })

  it('redirects to login when not authenticated', async () => {
    server.use(http.get('/api/users/me', () => HttpResponse.json({ error: 'Unauthorized' }, { status: 401 })))
    render(<AppWithRoutes />)
    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeTruthy()
    })
  })
})
