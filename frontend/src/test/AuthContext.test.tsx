import { describe, it, expect } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from '../contexts/AuthContext'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'

function TestComponent() {
  const { user, loading, login, logout } = useAuth()
  if (loading) return <div>Loading...</div>
  if (!user) return (
    <div>
      <span>Not logged in</span>
      <button onClick={() => login('test@test.com')}>Login</button>
    </div>
  )
  return (
    <div>
      <span>Hello {user.displayName}</span>
      <button onClick={logout}>Logout</button>
    </div>
  )
}

function renderWithAuth() {
  return render(<AuthProvider><TestComponent /></AuthProvider>)
}

describe('AuthContext', () => {
  it('shows not logged in state after initial load', async () => {
    renderWithAuth()
    await waitFor(() => {
      expect(screen.getByText('Not logged in')).toBeTruthy()
    })
  })

  it('logs in and shows user name', async () => {
    renderWithAuth()
    await waitFor(() => screen.getByText('Not logged in'))
    await act(async () => {
      await userEvent.click(screen.getByText('Login'))
    })
    await waitFor(() => {
      expect(screen.getByText('Hello test')).toBeTruthy()
    })
  })

  it('persists login across re-render if localStorage has email', async () => {
    localStorage.setItem('fakeUserEmail', 'test@test.com')
    renderWithAuth()
    await waitFor(() => {
      expect(screen.getByText('Hello test')).toBeTruthy()
    })
  })

  it('logs out and clears user', async () => {
    localStorage.setItem('fakeUserEmail', 'test@test.com')
    renderWithAuth()
    await waitFor(() => screen.getByText('Hello test'))
    await act(async () => {
      await userEvent.click(screen.getByText('Logout'))
    })
    expect(screen.getByText('Not logged in')).toBeTruthy()
    expect(localStorage.getItem('fakeUserEmail')).toBeNull()
  })

  it('shows not logged in if API returns error', async () => {
    server.use(http.get('/api/users/me', () => HttpResponse.error()))
    localStorage.setItem('fakeUserEmail', 'test@test.com')
    renderWithAuth()
    await waitFor(() => {
      expect(screen.getByText('Not logged in')).toBeTruthy()
    })
  })
})
