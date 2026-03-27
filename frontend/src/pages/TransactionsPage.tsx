import { useCallback, useEffect, useState } from 'react'
import { fetchWithEmail, mutateWithEmail } from '../contexts/AuthContext'
import Modal from '../components/Modal'
import type { Category, Transaction } from '../types'

const CURRENCIES = ['USD', 'EUR', 'GBP', 'UAH', 'PLN']

interface TransactionFilters {
  search: string
  categoryId: string
  dateFrom: string
  dateTo: string
  amountMin: string
  amountMax: string
}

const emptyFilters: TransactionFilters = {
  search: '',
  categoryId: '',
  dateFrom: '',
  dateTo: '',
  amountMin: '',
  amountMax: '',
}

interface TransactionFormData {
  title: string
  amount: string
  currency: string
  transactionDate: string
  categoryId: string
  notes: string
}

const emptyForm: TransactionFormData = {
  title: '',
  amount: '',
  currency: 'USD',
  transactionDate: new Date().toISOString().split('T')[0],
  categoryId: '',
  notes: '',
}

function buildQuery(filters: TransactionFilters): string {
  const params = new URLSearchParams()
  if (filters.search) params.set('search', filters.search)
  if (filters.categoryId) params.set('categoryId', filters.categoryId)
  if (filters.dateFrom) params.set('dateFrom', filters.dateFrom)
  if (filters.dateTo) params.set('dateTo', filters.dateTo)
  if (filters.amountMin) params.set('amountMin', filters.amountMin)
  if (filters.amountMax) params.set('amountMax', filters.amountMax)
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

export default function TransactionsPage() {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filters, setFilters] = useState<TransactionFilters>(emptyFilters)
  const [showModal, setShowModal] = useState(false)
  const [editingTx, setEditingTx] = useState<Transaction | null>(null)
  const [form, setForm] = useState<TransactionFormData>(emptyForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<Transaction | null>(null)
  const [showFilters, setShowFilters] = useState(false)

  const loadTransactions = useCallback(async (f: TransactionFilters) => {
    try {
      setLoading(true)
      const data = await fetchWithEmail<Transaction[]>(`/transactions${buildQuery(f)}`)
      setTransactions(data)
    } catch {
      setError('Failed to load transactions')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchWithEmail<Category[]>('/categories').then(setCategories).catch(() => {})
    loadTransactions(filters)
  }, [])

  useEffect(() => {
    const t = setTimeout(() => loadTransactions(filters), 400)
    return () => clearTimeout(t)
  }, [filters, loadTransactions])

  const openCreate = () => {
    setEditingTx(null)
    setForm(emptyForm)
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (tx: Transaction) => {
    setEditingTx(tx)
    setForm({
      title: tx.title,
      amount: tx.amount.toString(),
      currency: tx.currency,
      transactionDate: tx.transactionDate,
      categoryId: tx.categoryId?.toString() ?? '',
      notes: tx.notes ?? '',
    })
    setFormError(null)
    setShowModal(true)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.title.trim()) { setFormError('Title is required'); return }
    const amount = parseFloat(form.amount)
    if (isNaN(amount) || amount <= 0) { setFormError('Amount must be positive'); return }
    if (!form.transactionDate) { setFormError('Date is required'); return }

    const body = {
      title: form.title.trim(),
      amount,
      currency: form.currency,
      transactionDate: form.transactionDate,
      categoryId: form.categoryId ? parseInt(form.categoryId) : null,
      notes: form.notes.trim() || null,
    }

    try {
      if (editingTx) {
        await mutateWithEmail('PUT', `/transactions/${editingTx.id}`, body)
      } else {
        await mutateWithEmail('POST', '/transactions', body)
      }
      setShowModal(false)
      loadTransactions(filters)
    } catch (err: unknown) {
      setFormError((err as { detail?: string })?.detail ?? 'Something went wrong')
    }
  }

  const handleDelete = async (tx: Transaction) => {
    try {
      await mutateWithEmail('DELETE', `/transactions/${tx.id}`)
      setDeleteConfirm(null)
      loadTransactions(filters)
    } catch {
      setError('Failed to delete transaction')
      setDeleteConfirm(null)
    }
  }

  const setFilter = (key: keyof TransactionFilters, value: string) =>
    setFilters(f => ({ ...f, [key]: value }))

  const categoryName = (id: number) => categories.find(c => c.id === id)?.name ?? ''

  const hasActiveFilters = Object.values(filters).some(v => v !== '')

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Transactions</h1>
          <p className="text-gray-500 mt-1">{transactions.length} records</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setShowFilters(f => !f)}
            className={`px-4 py-2 border rounded-lg text-sm font-medium transition-colors ${showFilters || hasActiveFilters ? 'border-indigo-300 bg-indigo-50 text-indigo-700' : 'border-gray-300 text-gray-700 hover:bg-gray-50'}`}
          >
            {hasActiveFilters ? 'Filters (active)' : 'Filters'}
          </button>
          <button
            onClick={openCreate}
            className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition-colors"
          >
            Add transaction
          </button>
        </div>
      </div>

      {/* Search bar */}
      <div className="mb-4">
        <input
          type="text"
          placeholder="Search by title or notes..."
          value={filters.search}
          onChange={e => setFilter('search', e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      {/* Advanced filters */}
      {showFilters && (
        <div className="bg-gray-50 rounded-lg border border-gray-200 p-4 mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Category</label>
            <select
              value={filters.categoryId}
              onChange={e => setFilter('categoryId', e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">All</option>
              {categories.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">From</label>
            <input
              type="date"
              value={filters.dateFrom}
              onChange={e => setFilter('dateFrom', e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">To</label>
            <input
              type="date"
              value={filters.dateTo}
              onChange={e => setFilter('dateTo', e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Min amount</label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={filters.amountMin}
              onChange={e => setFilter('amountMin', e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Max amount</label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={filters.amountMax}
              onChange={e => setFilter('amountMax', e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div className="flex items-end">
            <button
              onClick={() => setFilters(emptyFilters)}
              className="text-sm text-gray-500 hover:text-gray-700 underline"
            >
              Clear all
            </button>
          </div>
        </div>
      )}

      {error && (
        <div role="alert" className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
          {error}
          <button className="ml-2 underline" onClick={() => setError(null)}>Dismiss</button>
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-indigo-600 border-t-transparent" />
        </div>
      ) : transactions.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          <p className="font-medium">No transactions found</p>
          {hasActiveFilters && <p className="text-sm mt-1">Try adjusting your filters</p>}
        </div>
      ) : (
        <>
          {/* Desktop table */}
          <div className="hidden sm:block bg-white rounded-xl border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Date</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Title</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Category</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">Amount</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {transactions.map(tx => (
                  <tr key={tx.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap">{tx.transactionDate}</td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-800">{tx.title}</div>
                      {tx.notes && <div className="text-gray-400 text-xs mt-0.5 truncate max-w-xs">{tx.notes}</div>}
                    </td>
                    <td className="px-4 py-3 text-gray-500">{categoryName(tx.categoryId)}</td>
                    <td className="px-4 py-3 text-right font-semibold text-gray-800 whitespace-nowrap">
                      {tx.amount.toFixed(2)} {tx.currency}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-1 justify-end">
                        <button
                          onClick={() => openEdit(tx)}
                          className="text-gray-400 hover:text-indigo-600 px-2 py-1 text-xs rounded transition-colors"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => setDeleteConfirm(tx)}
                          className="text-gray-400 hover:text-red-600 px-2 py-1 text-xs rounded transition-colors"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="sm:hidden space-y-2">
            {transactions.map(tx => (
              <div key={tx.id} className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex justify-between items-start">
                  <div>
                    <p className="font-medium text-gray-800">{tx.title}</p>
                    {tx.notes && <p className="text-xs text-gray-400 mt-0.5">{tx.notes}</p>}
                    <p className="text-xs text-gray-400 mt-1">{tx.transactionDate} · {categoryName(tx.categoryId)}</p>
                  </div>
                  <span className="font-semibold text-gray-800">{tx.amount.toFixed(2)} {tx.currency}</span>
                </div>
                <div className="flex gap-2 mt-3 justify-end">
                  <button onClick={() => openEdit(tx)} className="text-xs text-gray-500 hover:text-indigo-600">Edit</button>
                  <button onClick={() => setDeleteConfirm(tx)} className="text-xs text-gray-500 hover:text-red-600">Delete</button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {showModal && (
        <Modal
          title={editingTx ? 'Edit transaction' : 'New transaction'}
          onClose={() => setShowModal(false)}
        >
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Title</label>
              <input
                type="text"
                value={form.title}
                onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="e.g. Coffee"
                autoFocus
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Amount</label>
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={form.amount}
                  onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Currency</label>
                <select
                  value={form.currency}
                  onChange={e => setForm(f => ({ ...f, currency: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  {CURRENCIES.map(c => <option key={c}>{c}</option>)}
                </select>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Date</label>
              <input
                type="date"
                value={form.transactionDate}
                onChange={e => setForm(f => ({ ...f, transactionDate: e.target.value }))}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
              <select
                value={form.categoryId}
                onChange={e => setForm(f => ({ ...f, categoryId: e.target.value }))}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="">No category</option>
                {categories.map(c => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
              <textarea
                value={form.notes}
                onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                rows={2}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
              />
            </div>
            {formError && <p className="text-sm text-red-600">{formError}</p>}
            <div className="flex gap-2 justify-end">
              <button
                type="button"
                onClick={() => setShowModal(false)}
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 text-sm text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors"
              >
                {editingTx ? 'Save' : 'Add'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {deleteConfirm && (
        <Modal title="Delete transaction" onClose={() => setDeleteConfirm(null)}>
          <p className="text-gray-600 mb-4">
            Delete <strong>{deleteConfirm.title}</strong>? This cannot be undone.
          </p>
          <div className="flex gap-2 justify-end">
            <button
              onClick={() => setDeleteConfirm(null)}
              className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={() => handleDelete(deleteConfirm)}
              className="px-4 py-2 text-sm text-white bg-red-600 rounded-lg hover:bg-red-700 transition-colors"
            >
              Delete
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
