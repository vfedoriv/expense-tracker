import { useState } from 'react';
import { Button } from '../../components/ui/Button';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { EmptyState } from '../../components/ui/EmptyState';
import type { Transaction } from '../../types';

interface TransactionListProps {
  transactions: Transaction[];
  onEdit: (transaction: Transaction) => void;
  onDelete: (id: number) => void;
  loading: boolean;
}

function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(amount);
}

function formatDate(dateString: string): string {
  return new Date(dateString + 'T00:00:00').toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function TransactionList({ transactions, onEdit, onDelete, loading }: TransactionListProps) {
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

  if (loading) {
    return <LoadingSpinner />;
  }

  if (transactions.length === 0) {
    return (
      <EmptyState
        title="No transactions found"
        message="Create your first transaction to get started."
      />
    );
  }

  const handleDeleteClick = (id: number) => {
    setConfirmDeleteId(id);
  };

  const handleConfirmDelete = (id: number) => {
    onDelete(id);
    setConfirmDeleteId(null);
  };

  const handleCancelDelete = () => {
    setConfirmDeleteId(null);
  };

  return (
    <>
      {/* Desktop table (md and up) */}
      <div className="hidden overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm md:block">
        <table className="w-full">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50">
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500">
                Date
              </th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500">
                Title
              </th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500">
                Category
              </th>
              <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500">
                Amount
              </th>
              <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {transactions.map((tx) => (
              <tr key={tx.id} className="transition-colors hover:bg-gray-50">
                <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-600">
                  {formatDate(tx.transactionDate)}
                </td>
                <td className="px-4 py-3">
                  <div className="text-sm font-medium text-gray-900">{tx.title}</div>
                  {tx.notes && (
                    <div className="max-w-xs truncate text-xs text-gray-500">{tx.notes}</div>
                  )}
                </td>
                <td className="whitespace-nowrap px-4 py-3">
                  <span className="inline-flex rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700">
                    {tx.categoryName}
                  </span>
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-right text-sm font-semibold text-gray-900">
                  {formatCurrency(tx.amount, tx.currency)}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-right">
                  {confirmDeleteId === tx.id ? (
                    <div className="flex items-center justify-end gap-2">
                      <span className="text-xs text-red-600">Delete?</span>
                      <Button
                        variant="danger"
                        size="sm"
                        onClick={() => handleConfirmDelete(tx.id)}
                      >
                        Yes
                      </Button>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={handleCancelDelete}
                      >
                        No
                      </Button>
                    </div>
                  ) : (
                    <div className="flex items-center justify-end gap-2">
                      <Button variant="secondary" size="sm" onClick={() => onEdit(tx)}>
                        Edit
                      </Button>
                      <Button variant="secondary" size="sm" onClick={() => handleDeleteClick(tx.id)}>
                        Delete
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile cards (below md) */}
      <div className="flex flex-col gap-3 md:hidden">
        {transactions.map((tx) => (
          <div
            key={tx.id}
            className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
          >
            <div className="flex items-start justify-between">
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-gray-900">{tx.title}</div>
                <div className="mt-0.5 text-xs text-gray-500">{formatDate(tx.transactionDate)}</div>
              </div>
              <div className="ml-3 text-sm font-semibold text-gray-900">
                {formatCurrency(tx.amount, tx.currency)}
              </div>
            </div>

            <div className="mt-2">
              <span className="inline-flex rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700">
                {tx.categoryName}
              </span>
            </div>

            {tx.notes && (
              <p className="mt-2 text-xs text-gray-500">{tx.notes}</p>
            )}

            <div className="mt-3 flex gap-2 border-t border-gray-100 pt-3">
              {confirmDeleteId === tx.id ? (
                <>
                  <span className="flex items-center text-xs text-red-600">Confirm delete?</span>
                  <Button variant="danger" size="sm" onClick={() => handleConfirmDelete(tx.id)}>
                    Yes
                  </Button>
                  <Button variant="secondary" size="sm" onClick={handleCancelDelete}>
                    No
                  </Button>
                </>
              ) : (
                <>
                  <Button variant="secondary" size="sm" onClick={() => onEdit(tx)}>
                    Edit
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => handleDeleteClick(tx.id)}>
                    Delete
                  </Button>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}
