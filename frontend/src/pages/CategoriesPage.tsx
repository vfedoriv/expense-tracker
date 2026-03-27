import { useEffect, useState } from 'react'
import { fetchWithEmail, mutateWithEmail } from '../contexts/AuthContext'
import Modal from '../components/Modal'
import type { Category } from '../types'

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [editingCategory, setEditingCategory] = useState<Category | null>(null)
  const [formName, setFormName] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<Category | null>(null)

  const load = async () => {
    try {
      const data = await fetchWithEmail<Category[]>('/categories')
      setCategories(data)
    } catch {
      setError('Failed to load categories')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const openCreate = () => {
    setEditingCategory(null)
    setFormName('')
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (cat: Category) => {
    setEditingCategory(cat)
    setFormName(cat.name)
    setFormError(null)
    setShowModal(true)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!formName.trim()) {
      setFormError('Name is required')
      return
    }
    try {
      if (editingCategory) {
        await mutateWithEmail('PUT', `/categories/${editingCategory.id}`, { name: formName.trim() })
      } else {
        await mutateWithEmail('POST', '/categories', { name: formName.trim() })
      }
      setShowModal(false)
      load()
    } catch (err: unknown) {
      const detail = (err as { detail?: string })?.detail
      setFormError(detail ?? 'Something went wrong')
    }
  }

  const handleDelete = async (cat: Category) => {
    try {
      await mutateWithEmail('DELETE', `/categories/${cat.id}`)
      setDeleteConfirm(null)
      load()
    } catch (err: unknown) {
      const detail = (err as { detail?: string })?.detail
      setError(detail ?? 'Failed to delete category')
      setDeleteConfirm(null)
    }
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Categories</h1>
          <p className="text-gray-500 mt-1">Organise your expenses</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition-colors"
        >
          New category
        </button>
      </div>

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
      ) : categories.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A2 2 0 013 12V7a4 4 0 014-4z" />
          </svg>
          <p className="font-medium">No categories yet</p>
          <p className="text-sm mt-1">Create a category to get started</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 divide-y divide-gray-100">
          {categories.map(cat => (
            <div key={cat.id} className="flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors">
              <span className="font-medium text-gray-800">{cat.name}</span>
              <div className="flex gap-2">
                <button
                  onClick={() => openEdit(cat)}
                  className="text-sm text-gray-500 hover:text-indigo-600 px-2 py-1 rounded transition-colors"
                >
                  Edit
                </button>
                <button
                  onClick={() => setDeleteConfirm(cat)}
                  className="text-sm text-gray-500 hover:text-red-600 px-2 py-1 rounded transition-colors"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <Modal
          title={editingCategory ? 'Rename category' : 'New category'}
          onClose={() => setShowModal(false)}
        >
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
              <input
                type="text"
                value={formName}
                onChange={e => { setFormName(e.target.value); setFormError(null) }}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="e.g. Groceries"
                autoFocus
              />
              {formError && <p className="mt-1 text-sm text-red-600">{formError}</p>}
            </div>
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
                {editingCategory ? 'Save' : 'Create'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {deleteConfirm && (
        <Modal title="Delete category" onClose={() => setDeleteConfirm(null)}>
          <p className="text-gray-600 mb-4">
            Delete <strong>{deleteConfirm.name}</strong>? This cannot be undone.
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
