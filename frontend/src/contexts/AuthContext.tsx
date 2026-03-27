import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { User } from '../types'

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (email: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const storedEmail = localStorage.getItem('fakeUserEmail')
    if (storedEmail) {
      fetchUser(storedEmail)
    } else {
      setLoading(false)
    }
  }, [])

  const fetchUser = async (email: string) => {
    try {
      const data = await fetchWithEmail<User>('/users/me', email)
      setUser(data)
    } catch {
      localStorage.removeItem('fakeUserEmail')
    } finally {
      setLoading(false)
    }
  }

  const login = async (email: string) => {
    setLoading(true)
    localStorage.setItem('fakeUserEmail', email)
    await fetchUser(email)
  }

  const logout = () => {
    localStorage.removeItem('fakeUserEmail')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

export function getStoredEmail(): string {
  return localStorage.getItem('fakeUserEmail') ?? 'admin@test.com'
}

export async function fetchWithEmail<T>(path: string, email?: string): Promise<T> {
  const userEmail = email ?? getStoredEmail()
  const res = await fetch(`/api${path}`, {
    headers: { 'X-User-Email': userEmail },
    credentials: 'include',
  })

  if (res.status === 204) return undefined as T

  const data = await res.json()
  if (!res.ok) {
    throw { status: res.status, detail: data?.detail ?? res.statusText, data }
  }
  return data as T
}

export async function mutateWithEmail<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown
): Promise<T> {
  const userEmail = getStoredEmail()
  const headers: Record<string, string> = { 'X-User-Email': userEmail }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    credentials: 'include',
  })

  if (res.status === 204) return undefined as T
  const data = await res.json()
  if (!res.ok) {
    throw { status: res.status, detail: data?.detail ?? res.statusText, data }
  }
  return data as T
}
