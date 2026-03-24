import { useState } from 'react';
import { useCategories } from '../hooks/useCategories';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { Input } from '../components/ui/Input';
import { EmptyState } from '../components/common/EmptyState';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { isNotBlank } from '../utils/validators';
import { formatDate } from '../utils/formatters';
import type { Category } from '../types';

type ModalMode = 'create' | 'edit' | null;

export function CategoriesPage() {
  const {
    categories,
    loading,
    error,
    fetchCategories,
    createCategory,
    updateCategory,
    deleteCategory,
  } = useCategories();

  const [modalMode, setModalMode] = useState<ModalMode>(null);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [categoryName, setCategoryName] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Delete confirmation state
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  function openCreateModal() {
    setCategoryName('');
    setNameError(null);
    setSubmitError(null);
    setModalMode('create');
  }

  function openEditModal(category: Category) {
    setEditingCategory(category);
    setCategoryName(category.name);
    setNameError(null);
    setSubmitError(null);
    setModalMode('edit');
  }

  function closeModal() {
    setModalMode(null);
    setEditingCategory(null);
    setCategoryName('');
    setNameError(null);
    setSubmitError(null);
  }

  function openDeleteConfirmation(category: Category) {
    setDeleteTarget(category);
    setDeleteError(null);
  }

  function closeDeleteConfirmation() {
    setDeleteTarget(null);
    setDeleteError(null);
  }

  async function handleSubmit() {
    // Validate
    if (!isNotBlank(categoryName)) {
      setNameError('Name is required');
      return;
    }

    setNameError(null);
    setSubmitError(null);
    setSubmitting(true);

    try {
      if (modalMode === 'create') {
        await createCategory({ name: categoryName.trim() });
      } else if (modalMode === 'edit' && editingCategory) {
        await updateCategory(editingCategory.id, { name: categoryName.trim() });
      }
      closeModal();
    } catch (err: unknown) {
      const apiError = err as { message?: string };
      setSubmitError(apiError.message || 'An error occurred');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;

    setDeleting(true);
    setDeleteError(null);

    try {
      await deleteCategory(deleteTarget.id);
      closeDeleteConfirmation();
    } catch (err: unknown) {
      const apiError = err as { message?: string };
      setDeleteError(apiError.message || 'Failed to delete category');
    } finally {
      setDeleting(false);
    }
  }

  // Loading state
  if (loading) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-bold text-gray-900">Categories</h1>
        <LoadingSpinner size="lg" message="Loading categories..." />
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-bold text-gray-900">Categories</h1>
        <ErrorMessage message="Failed to load categories" onRetry={fetchCategories} />
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Categories</h1>
        <Button onClick={openCreateModal}>Create Category</Button>
      </div>

      {/* Empty state */}
      {categories.length === 0 ? (
        <EmptyState
          title="No categories yet"
          description="Create one to get started."
          icon={
            <svg className="h-12 w-12" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M9.568 3H5.25A2.25 2.25 0 003 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581c.699.699 1.78.872 2.607.33a18.095 18.095 0 005.223-5.223c.542-.827.369-1.908-.33-2.607L11.16 3.66A2.25 2.25 0 009.568 3z"
              />
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M6 6h.008v.008H6V6z"
              />
            </svg>
          }
          action={<Button onClick={openCreateModal}>Create Category</Button>}
        />
      ) : (
        /* Category list */
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {categories.map((category) => (
            <div
              key={category.id}
              className="group rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md focus-within:ring-2 focus-within:ring-blue-500"
            >
              <div className="min-w-0">
                <h3 className="truncate text-base font-medium text-gray-900">{category.name}</h3>
                <p className="mt-1 text-xs text-gray-500">
                  Created {formatDate(category.createdAt)}
                </p>
              </div>
              <div className="mt-3 flex gap-1 border-t border-gray-100 pt-3">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => openEditModal(category)}
                  aria-label={`Edit ${category.name}`}
                >
                  Edit
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => openDeleteConfirmation(category)}
                  aria-label={`Delete ${category.name}`}
                >
                  Delete
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create/Edit Modal */}
      <Modal
        isOpen={modalMode !== null}
        onClose={closeModal}
        title={modalMode === 'create' ? 'Create Category' : 'Edit Category'}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void handleSubmit();
          }}
        >
          <Input
            label="Name"
            value={categoryName}
            onChange={(e) => {
              setCategoryName(e.target.value);
              if (nameError) setNameError(null);
              if (submitError) setSubmitError(null);
            }}
            error={nameError ?? undefined}
            placeholder="Enter category name"
            autoFocus
          />
          {submitError && (
            <div className="mt-3">
              <ErrorMessage message={submitError} />
            </div>
          )}
          <div className="mt-4 flex justify-end gap-3">
            <Button variant="secondary" type="button" onClick={closeModal}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Saving...' : modalMode === 'create' ? 'Create' : 'Save'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={deleteTarget !== null}
        onClose={closeDeleteConfirmation}
        title="Delete Category"
      >
        <p className="text-sm text-gray-600">
          Are you sure you want to delete{' '}
          <span className="font-semibold">{deleteTarget?.name}</span>?
        </p>
        <p className="mt-1 text-sm text-gray-500">This cannot be undone.</p>
        {deleteError && (
          <div className="mt-3">
            <ErrorMessage message={deleteError} />
          </div>
        )}
        <div className="mt-4 flex justify-end gap-3">
          <Button variant="secondary" onClick={closeDeleteConfirmation}>
            Cancel
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} disabled={deleting}>
            {deleting ? 'Deleting...' : 'Confirm'}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
