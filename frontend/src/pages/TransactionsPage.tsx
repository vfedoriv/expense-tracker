import { useState, useCallback } from 'react';
import { useTransactions } from '../hooks/useTransactions';
import { useCategories } from '../hooks/useCategories';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { Input } from '../components/ui/Input';
import { EmptyState } from '../components/common/EmptyState';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { isNotBlank, isPositiveAmount, isValidDate } from '../utils/validators';
import { formatCurrency, formatDate } from '../utils/formatters';
import type { Transaction, TransactionRequest } from '../types';

type ModalMode = 'create' | 'edit' | null;

interface FormErrors {
  title?: string;
  amount?: string;
  transactionDate?: string;
  categoryId?: string;
}

interface Toast {
  message: string;
  type: 'error' | 'success';
}

export function TransactionsPage() {
  const {
    transactions,
    loading,
    error,
    fetchTransactions,
    createTransaction,
    updateTransaction,
    deleteTransaction,
  } = useTransactions();

  const { categories } = useCategories();

  // Modal state
  const [modalMode, setModalMode] = useState<ModalMode>(null);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);

  // Form fields
  const [title, setTitle] = useState('');
  const [amount, setAmount] = useState('');
  const [transactionDate, setTransactionDate] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [notes, setNotes] = useState('');

  // Form errors
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Delete confirmation state
  const [deleteTarget, setDeleteTarget] = useState<Transaction | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Toast state
  const [toast, setToast] = useState<Toast | null>(null);

  const showToast = useCallback((message: string, type: 'error' | 'success' = 'error') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  }, []);

  function resetForm() {
    setTitle('');
    setAmount('');
    setTransactionDate('');
    setCategoryId('');
    setNotes('');
    setFormErrors({});
    setSubmitError(null);
  }

  function openCreateModal() {
    resetForm();
    setModalMode('create');
  }

  function openEditModal(transaction: Transaction) {
    setEditingTransaction(transaction);
    setTitle(transaction.title);
    setAmount(String(transaction.amount));
    setTransactionDate(transaction.transactionDate);
    setCategoryId(String(transaction.categoryId));
    setNotes(transaction.notes ?? '');
    setFormErrors({});
    setSubmitError(null);
    setModalMode('edit');
  }

  function closeModal() {
    setModalMode(null);
    setEditingTransaction(null);
    resetForm();
  }

  function openDeleteConfirmation(transaction: Transaction) {
    setDeleteTarget(transaction);
    setDeleteError(null);
  }

  function closeDeleteConfirmation() {
    setDeleteTarget(null);
    setDeleteError(null);
  }

  function validateForm(): FormErrors {
    const errors: FormErrors = {};

    if (!isNotBlank(title)) {
      errors.title = 'Title is required';
    }

    const amountNum = parseFloat(amount);
    if (!amount || isNaN(amountNum)) {
      errors.amount = 'Amount is required';
    } else if (!isPositiveAmount(amountNum)) {
      errors.amount = 'Amount must be greater than 0';
    }

    if (!isValidDate(transactionDate)) {
      errors.transactionDate = 'Date is required';
    }

    if (!categoryId) {
      errors.categoryId = 'Category is required';
    }

    return errors;
  }

  async function handleSubmit() {
    const errors = validateForm();
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors);
      return;
    }

    setFormErrors({});
    setSubmitError(null);
    setSubmitting(true);

    const request: TransactionRequest = {
      title: title.trim(),
      amount: parseFloat(amount),
      transactionDate,
      categoryId: parseInt(categoryId, 10),
      notes: notes.trim() || undefined,
    };

    try {
      if (modalMode === 'create') {
        await createTransaction(request);
        showToast('Transaction created successfully', 'success');
      } else if (modalMode === 'edit' && editingTransaction) {
        await updateTransaction(editingTransaction.id, request);
        showToast('Transaction updated successfully', 'success');
      }
      closeModal();
    } catch (err: unknown) {
      const apiError = err as { message?: string };
      const errorMessage = apiError.message || 'An error occurred';
      setSubmitError(errorMessage);
      showToast(errorMessage, 'error');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;

    setDeleting(true);
    setDeleteError(null);

    try {
      await deleteTransaction(deleteTarget.id);
      closeDeleteConfirmation();
      showToast('Transaction deleted successfully', 'success');
    } catch (err: unknown) {
      const apiError = err as { message?: string };
      const errorMessage = apiError.message || 'Failed to delete transaction';
      setDeleteError(errorMessage);
      showToast(errorMessage, 'error');
    } finally {
      setDeleting(false);
    }
  }

  function clearFieldError(field: keyof FormErrors) {
    if (formErrors[field]) {
      setFormErrors((prev) => {
        const updated = { ...prev };
        delete updated[field];
        return updated;
      });
    }
  }

  function truncateText(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return text.slice(0, maxLength) + '…';
  }

  // Loading state
  if (loading) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-bold text-gray-900">Transactions</h1>
        <LoadingSpinner size="lg" message="Loading transactions..." />
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-bold text-gray-900">Transactions</h1>
        <ErrorMessage message="Failed to load transactions" onRetry={fetchTransactions} />
      </div>
    );
  }

  return (
    <div>
      {/* Toast notification */}
      {toast && (
        <div
          className={`fixed right-4 top-4 z-[60] rounded-lg px-4 py-3 shadow-lg transition-all ${
            toast.type === 'error'
              ? 'border border-red-200 bg-red-50 text-red-700'
              : 'border border-green-200 bg-green-50 text-green-700'
          }`}
          role="alert"
        >
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium">{toast.message}</p>
            <button
              onClick={() => setToast(null)}
              className="ml-2 text-current opacity-70 hover:opacity-100"
              aria-label="Close toast"
            >
              ×
            </button>
          </div>
        </div>
      )}

      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Transactions</h1>
        <Button onClick={openCreateModal}>Add Transaction</Button>
      </div>

      {/* Empty state */}
      {transactions.length === 0 ? (
        <EmptyState
          title="No transactions yet"
          description="Start tracking your expenses by adding your first transaction."
          icon={
            <svg className="h-12 w-12" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M2.25 18.75a60.07 60.07 0 0115.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 013 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 00-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 01-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 003 15h-.75M15 10.5a3 3 0 11-6 0 3 3 0 016 0zm3 0h.008v.008H18V10.5zm-12 0h.008v.008H6V10.5z"
              />
            </svg>
          }
          action={
            <Button onClick={openCreateModal}>Add Transaction</Button>
          }
        />
      ) : (
        <>
          {/* Desktop table view */}
          <div className="hidden overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm sm:block">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Title
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Amount
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Date
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Category
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Notes
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {transactions.map((transaction) => (
                  <tr
                    key={transaction.id}
                    className="transition-colors hover:bg-gray-50"
                  >
                    <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-gray-900">
                      {transaction.title}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900">
                      {formatCurrency(transaction.amount)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                      {formatDate(transaction.transactionDate)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                      <span className="inline-flex items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700">
                        {transaction.categoryName}
                      </span>
                    </td>
                    <td className="max-w-xs px-6 py-4 text-sm text-gray-500">
                      <span className="block truncate">
                        {transaction.notes ? truncateText(transaction.notes, 50) : '—'}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-right text-sm">
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openEditModal(transaction)}
                          aria-label={`Edit ${transaction.title}`}
                        >
                          Edit
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openDeleteConfirmation(transaction)}
                          aria-label={`Delete ${transaction.title}`}
                        >
                          Delete
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile card view */}
          <div className="flex flex-col gap-3 sm:hidden">
            {transactions.map((transaction) => (
              <div
                key={transaction.id}
                className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md"
              >
                <div className="flex items-start justify-between">
                  <div className="min-w-0 flex-1">
                    <h3 className="truncate text-sm font-medium text-gray-900">
                      {transaction.title}
                    </h3>
                    <p className="mt-1 text-lg font-semibold text-gray-900">
                      {formatCurrency(transaction.amount)}
                    </p>
                  </div>
                  <span className="ml-2 inline-flex items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700">
                    {transaction.categoryName}
                  </span>
                </div>
                <div className="mt-2 flex items-center gap-4 text-xs text-gray-500">
                  <span>{formatDate(transaction.transactionDate)}</span>
                  {transaction.notes && (
                    <span className="truncate">{truncateText(transaction.notes, 30)}</span>
                  )}
                </div>
                <div className="mt-3 flex gap-2 border-t border-gray-100 pt-3">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => openEditModal(transaction)}
                    aria-label={`Edit ${transaction.title}`}
                  >
                    Edit
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => openDeleteConfirmation(transaction)}
                    aria-label={`Delete ${transaction.title}`}
                  >
                    Delete
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {/* Create/Edit Modal */}
      <Modal
        isOpen={modalMode !== null}
        onClose={closeModal}
        title={modalMode === 'create' ? 'Add Transaction' : 'Edit Transaction'}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void handleSubmit();
          }}
        >
          <div className="flex flex-col gap-4">
            <Input
              label="Title"
              value={title}
              onChange={(e) => {
                setTitle(e.target.value);
                clearFieldError('title');
              }}
              error={formErrors.title}
              placeholder="Enter transaction title"
              autoFocus
            />

            <div className="w-full">
              <label
                htmlFor="amount"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Amount (USD)
              </label>
              <div className="relative">
                <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-sm text-gray-500">
                  $
                </span>
                <input
                  id="amount"
                  type="number"
                  value={amount}
                  onChange={(e) => {
                    setAmount(e.target.value);
                    clearFieldError('amount');
                  }}
                  className={`block w-full rounded-lg border py-2 pl-7 pr-3 text-sm shadow-sm transition-colors placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-offset-0 ${
                    formErrors.amount
                      ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
                      : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                  }`}
                  placeholder="0.00"
                  step="0.01"
                  aria-invalid={formErrors.amount ? 'true' : 'false'}
                  aria-describedby={formErrors.amount ? 'amount-error' : undefined}
                />
              </div>
              {formErrors.amount && (
                <p id="amount-error" className="mt-1 text-sm text-red-600">
                  {formErrors.amount}
                </p>
              )}
            </div>

            <Input
              label="Date"
              type="date"
              value={transactionDate}
              onChange={(e) => {
                setTransactionDate(e.target.value);
                clearFieldError('transactionDate');
              }}
              error={formErrors.transactionDate}
            />

            <div className="w-full">
              <label
                htmlFor="category"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Category
              </label>
              <select
                id="category"
                value={categoryId}
                onChange={(e) => {
                  setCategoryId(e.target.value);
                  clearFieldError('categoryId');
                }}
                className={`block w-full rounded-lg border px-3 py-2 text-sm shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-offset-0 ${
                  formErrors.categoryId
                    ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
                    : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                }`}
                aria-invalid={formErrors.categoryId ? 'true' : 'false'}
                aria-describedby={formErrors.categoryId ? 'category-error' : undefined}
              >
                <option value="">Select a category</option>
                {categories.map((cat) => (
                  <option key={cat.id} value={String(cat.id)}>
                    {cat.name}
                  </option>
                ))}
              </select>
              {formErrors.categoryId && (
                <p id="category-error" className="mt-1 text-sm text-red-600">
                  {formErrors.categoryId}
                </p>
              )}
            </div>

            <div className="w-full">
              <label
                htmlFor="notes"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Notes
              </label>
              <textarea
                id="notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
                placeholder="Optional notes"
                rows={3}
              />
            </div>
          </div>

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
              {submitting
                ? 'Saving...'
                : modalMode === 'create'
                  ? 'Create'
                  : 'Save'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={deleteTarget !== null}
        onClose={closeDeleteConfirmation}
        title="Delete Transaction"
      >
        <p className="text-sm text-gray-600">
          Are you sure you want to delete{' '}
          <span className="font-semibold">{deleteTarget?.title}</span>?
        </p>
        <p className="mt-1 text-sm text-gray-500">
          This cannot be undone.
        </p>
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
