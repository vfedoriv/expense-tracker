import { useState, useEffect, useCallback } from 'react';
import { transactionsApi, type TransactionInput } from '../api/transactions';
import { categoriesApi } from '../api/categories';
import type { Transaction, Category, Page } from '../types';
import { TransactionList } from '../features/transactions/TransactionList';
import { TransactionFilters, type TransactionFilterValues } from '../features/transactions/TransactionFilters';
import { TransactionForm } from '../features/transactions/TransactionForm';
import { Button } from '../components/ui/Button';
import { Pagination } from '../components/ui/Pagination';

const emptyFilters: TransactionFilterValues = {
  q: '',
  categoryId: '',
  dateFrom: '',
  dateTo: '',
  amountMin: '',
  amountMax: '',
};

export function TransactionsPage() {
  const [transactions, setTransactions] = useState<Page<Transaction> | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState<TransactionFilterValues>(emptyFilters);
  const [page, setPage] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);

  const fetchCategories = useCallback(async () => {
    try {
      const data = await categoriesApi.getAll();
      setCategories(data);
    } catch { /* handled */ }
  }, []);

  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    try {
      const data = await transactionsApi.search({
        q: filters.q || undefined,
        categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
        dateFrom: filters.dateFrom || undefined,
        dateTo: filters.dateTo || undefined,
        amountMin: filters.amountMin ? Number(filters.amountMin) : undefined,
        amountMax: filters.amountMax ? Number(filters.amountMax) : undefined,
        page,
        size: 20,
      });
      setTransactions(data);
    } catch { /* handled */ }
    finally { setLoading(false); }
  }, [filters, page]);

  useEffect(() => { fetchCategories(); }, [fetchCategories]);
  useEffect(() => { fetchTransactions(); }, [fetchTransactions]);

  const handleFilterChange = (newFilters: TransactionFilterValues) => {
    setFilters(newFilters);
    setPage(0);
  };

  const handleCreate = async (data: TransactionInput) => {
    await transactionsApi.create(data);
    setFormOpen(false);
    fetchTransactions();
  };

  const handleUpdate = async (data: TransactionInput) => {
    if (!editingTransaction) return;
    await transactionsApi.update(editingTransaction.id, data);
    setEditingTransaction(null);
    fetchTransactions();
  };

  const handleDelete = async (id: number) => {
    await transactionsApi.delete(id);
    fetchTransactions();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Transactions</h1>
        <Button onClick={() => setFormOpen(true)}>Add Transaction</Button>
      </div>

      <TransactionFilters
        filters={filters}
        onFilterChange={handleFilterChange}
        categories={categories}
      />

      <TransactionList
        transactions={transactions?.content ?? []}
        loading={loading}
        onEdit={(tx) => setEditingTransaction(tx)}
        onDelete={handleDelete}
      />

      {transactions && (
        <Pagination
          page={transactions.number}
          totalPages={transactions.totalPages}
          onPageChange={setPage}
        />
      )}

      <TransactionForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSubmit={handleCreate}
        categories={categories}
      />

      <TransactionForm
        open={editingTransaction !== null}
        onClose={() => setEditingTransaction(null)}
        onSubmit={handleUpdate}
        categories={categories}
        initialData={editingTransaction ?? undefined}
      />
    </div>
  );
}
