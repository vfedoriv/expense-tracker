import { useCallback, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { useAuth } from '../contexts/AuthContext'
import { useBudgetAlerts } from '../hooks/useBudgetAlerts'
import ToastContainer, { type Toast } from './ToastContainer'
import type { BudgetAlertMessage } from '../types'

interface LayoutProps {
  children: React.ReactNode
}

function currentYearMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

const navItems = [
  { path: '/', label: 'Dashboard' },
  { path: '/transactions', label: 'Transactions' },
  { path: '/categories', label: 'Categories' },
]

export default function Layout({ children }: LayoutProps) {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [toasts, setToasts] = useState<Toast[]>([])
  let toastId = 0

  const handleAlert = useCallback((alert: BudgetAlertMessage) => {
    const id = ++toastId
    const type = alert.threshold >= 100 ? 'error' : 'warning'
    setToasts(prev => [...prev, { id, message: alert.message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 7000)
  }, [])

  useBudgetAlerts(currentYearMonth(), handleAlert)

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-8">
              <span className="text-xl font-bold text-indigo-600">ExpenseTracker</span>
              <div className="flex gap-1">
                {navItems.map(item => (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={`px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                      location.pathname === item.path
                        ? 'bg-indigo-50 text-indigo-700'
                        : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'
                    }`}
                  >
                    {item.label}
                  </Link>
                ))}
              </div>
            </div>
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                {user?.avatarUrl && (
                  <img
                    src={user.avatarUrl}
                    alt={user.displayName}
                    className="w-8 h-8 rounded-full"
                  />
                )}
                <span className="text-sm text-gray-700">{user?.displayName}</span>
              </div>
              <button
                onClick={logout}
                className="text-sm text-gray-500 hover:text-gray-700 transition-colors"
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
      </nav>
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>
      <ToastContainer toasts={toasts} onDismiss={id => setToasts(prev => prev.filter(t => t.id !== id))} />
    </div>
  )
}
