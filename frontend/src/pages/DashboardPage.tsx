import { useCallback, useEffect, useState } from 'react'
import { fetchWithEmail, mutateWithEmail } from '../contexts/AuthContext'
import Modal from '../components/Modal'
import ToastContainer, { type Toast } from '../components/ToastContainer'
import { useBudgetAlerts } from '../hooks/useBudgetAlerts'
import type { BudgetAlertMessage, BudgetSummary, Transaction } from '../types'

function ProgressBar({ percent }: { percent: number }) {
  const clamped = Math.min(percent, 100)
  const color =
    percent >= 100 ? 'bg-red-500' :
    percent >= 80 ? 'bg-orange-400' :
    percent >= 50 ? 'bg-yellow-400' : 'bg-emerald-500'
  return (
    <div className="w-full bg-gray-100 rounded-full h-3 overflow-hidden">
      <div
        className={`h-3 rounded-full transition-all duration-500 ${color}`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  )
}

function pad(n: number) { return n.toString().padStart(2, '0') }
function yearMonth(d: Date) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}` }

export default function DashboardPage() {
  const now = new Date()
  const [currentYM, setCurrentYM] = useState(yearMonth(now))
  const [budget, setBudget] = useState<BudgetSummary | null>(null)
  const [recentTx, setRecentTx] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [showBudgetModal, setShowBudgetModal] = useState(false)
  const [budgetInput, setBudgetInput] = useState('')
  const [budgetError, setBudgetError] = useState<string | null>(null)
  const [toasts, setToasts] = useState<Toast[]>([])
  let toastId = 0

  const addToast = useCallback((alert: BudgetAlertMessage) => {
    const type = alert.threshold >= 100 ? 'error' : 'warning'
    setToasts(prev => [...prev, { id: ++toastId, message: alert.message, type }])
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== toastId))
    }, 7000)
  }, [])

  useBudgetAlerts(currentYM, addToast)

  const [year, month] = currentYM.split('-').map(Number)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [b, txs] = await Promise.all([
        fetchWithEmail<BudgetSummary>(`/budgets/${year}/${month}`),
        fetchWithEmail<Transaction[]>(`/transactions?dateFrom=${currentYM}-01&dateTo=${currentYM}-31`),
      ])
      setBudget(b)
      setRecentTx(txs.slice(0, 5))
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }, [currentYM, year, month])

  useEffect(() => { load() }, [load])

  const openBudgetModal = () => {
    setBudgetInput(budget?.budget?.toString() ?? '')
    setBudgetError(null)
    setShowBudgetModal(true)
  }

  const handleSetBudget = async (e: React.FormEvent) => {
    e.preventDefault()
    const amount = parseFloat(budgetInput)
    if (isNaN(amount) || amount <= 0) { setBudgetError('Must be a positive number'); return }
    try {
      await mutateWithEmail('PUT', `/budgets/${year}/${month}`, { amount })
      setShowBudgetModal(false)
      load()
    } catch {
      setBudgetError('Failed to set budget')
    }
  }

  const prevMonth = () => {
    const d = new Date(year, month - 2)
    setCurrentYM(yearMonth(d))
  }
  const nextMonth = () => {
    const d = new Date(year, month)
    setCurrentYM(yearMonth(d))
  }

  const monthLabel = new Date(year, month - 1).toLocaleString('default', { month: 'long', year: 'numeric' })

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500 mt-1">Monthly budget overview</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={prevMonth} className="p-1.5 text-gray-500 hover:text-gray-700 rounded hover:bg-gray-100">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <span className="font-semibold text-gray-700 min-w-36 text-center">{monthLabel}</span>
          <button onClick={nextMonth} className="p-1.5 text-gray-500 hover:text-gray-700 rounded hover:bg-gray-100">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-indigo-600 border-t-transparent" />
        </div>
      ) : (
        <>
          {/* Budget card */}
          <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
            <div className="flex justify-between items-start mb-4">
              <div>
                <p className="text-sm text-gray-500">Monthly Budget</p>
                {budget?.budgetSet ? (
                  <p className="text-3xl font-bold text-gray-900 mt-1">{budget.totalSpent.toFixed(2)}</p>
                ) : (
                  <p className="text-2xl font-semibold text-gray-400 mt-1">No budget set</p>
                )}
              </div>
              <button
                onClick={openBudgetModal}
                className="px-3 py-1.5 text-sm text-indigo-600 border border-indigo-200 rounded-lg hover:bg-indigo-50 transition-colors"
              >
                {budget?.budgetSet ? 'Edit budget' : 'Set budget'}
              </button>
            </div>

            {budget?.budgetSet && (
              <>
                <div className="mb-2">
                  <ProgressBar percent={budget.usagePercent ?? 0} />
                </div>
                <div className="flex justify-between text-sm mt-2">
                  <span className="text-gray-500">Spent: <strong>{budget.totalSpent.toFixed(2)}</strong></span>
                  <span className="text-gray-500">Budget: <strong>{budget.budget?.toFixed(2)}</strong></span>
                  <span className={`font-medium ${(budget.remaining ?? 0) < 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                    {(budget.remaining ?? 0) >= 0 ? 'Remaining' : 'Over by'}: {Math.abs(budget.remaining ?? 0).toFixed(2)}
                  </span>
                </div>
                {(budget.usagePercent ?? 0) >= 80 && (
                  <div className={`mt-3 p-2 rounded-lg text-sm font-medium ${(budget.usagePercent ?? 0) >= 100 ? 'bg-red-50 text-red-700' : 'bg-orange-50 text-orange-700'}`}>
                    {(budget.usagePercent ?? 0) >= 100
                      ? 'Budget exceeded!'
                      : `${budget.usagePercent?.toFixed(0)}% of budget used`}
                  </div>
                )}
              </>
            )}

            {!budget?.budgetSet && budget && (
              <p className="text-gray-600">
                Total spent this month: <strong>{budget.totalSpent.toFixed(2)}</strong>
              </p>
            )}
          </div>

          {/* Recent transactions */}
          <div className="bg-white rounded-xl border border-gray-200 p-6">
            <h2 className="text-base font-semibold text-gray-800 mb-4">Recent Transactions</h2>
            {recentTx.length === 0 ? (
              <p className="text-gray-400 text-sm">No transactions this month</p>
            ) : (
              <div className="space-y-2">
                {recentTx.map(tx => (
                  <div key={tx.id} className="flex justify-between items-center py-2 border-b border-gray-50 last:border-0">
                    <div>
                      <span className="font-medium text-gray-800 text-sm">{tx.title}</span>
                      <span className="ml-2 text-xs text-gray-400">{tx.transactionDate}</span>
                    </div>
                    <span className="text-sm font-semibold text-gray-700">{tx.amount.toFixed(2)} {tx.currency}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      {showBudgetModal && (
        <Modal title="Set monthly budget" onClose={() => setShowBudgetModal(false)}>
          <form onSubmit={handleSetBudget} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Budget amount</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={budgetInput}
                onChange={e => { setBudgetInput(e.target.value); setBudgetError(null) }}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="e.g. 1000.00"
                autoFocus
              />
              {budgetError && <p className="mt-1 text-sm text-red-600">{budgetError}</p>}
            </div>
            <div className="flex gap-2 justify-end">
              <button
                type="button"
                onClick={() => setShowBudgetModal(false)}
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 text-sm text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors"
              >
                Save
              </button>
            </div>
          </form>
        </Modal>
      )}

      <ToastContainer
        toasts={toasts}
        onDismiss={id => setToasts(prev => prev.filter(t => t.id !== id))}
      />
    </div>
  )
}
