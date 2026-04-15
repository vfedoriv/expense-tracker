import { useState, useEffect, type FormEvent } from 'react';
import { Modal } from '../../components/ui/Modal';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Button } from '../../components/ui/Button';
import type { Category, Transaction } from '../../types';
import type { TransactionInput } from '../../api/transactions';

interface TransactionFormProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: TransactionInput) => Promise<void>;
  categories: Category[];
  initialData?: Transaction;
}

interface FormErrors {
  title?: string;
  amount?: string;
  categoryId?: string;
  transactionDate?: string;
}

function todayString(): string {
  return new Date().toISOString().split('T')[0];
}

export function TransactionForm({
  open,
  onClose,
  onSubmit,
  categories,
  initialData,
}: TransactionFormProps) {
  const isEdit = !!initialData;

  const [title, setTitle] = useState('');
  const [amount, setAmount] = useState('');
  const [currency] = useState('USD');
  const [categoryId, setCategoryId] = useState('');
  const [transactionDate, setTransactionDate] = useState('');
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      if (initialData) {
        setTitle(initialData.title);
        setAmount(String(initialData.amount));
        setCategoryId(String(initialData.categoryId));
        setTransactionDate(initialData.transactionDate);
        setNotes(initialData.notes ?? '');
      } else {
        setTitle('');
        setAmount('');
        setCategoryId('');
        setTransactionDate(todayString());
        setNotes('');
      }
      setErrors({});
      setSubmitting(false);
    }
  }, [open, initialData]);

  const validate = (): FormErrors => {
    const errs: FormErrors = {};

    if (!title.trim()) {
      errs.title = 'Title is required';
    }

    const amountNum = parseFloat(amount);
    if (!amount || isNaN(amountNum) || amountNum <= 0) {
      errs.amount = 'Amount must be greater than 0';
    }

    if (!categoryId) {
      errs.categoryId = 'Category is required';
    }

    if (!transactionDate) {
      errs.transactionDate = 'Date is required';
    }

    return errs;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    const formErrors = validate();
    if (Object.keys(formErrors).length > 0) {
      setErrors(formErrors);
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        title: title.trim(),
        amount: parseFloat(amount),
        currency,
        categoryId: parseInt(categoryId, 10),
        transactionDate,
        notes: notes.trim() || undefined,
      });
      onClose();
    } catch {
      // Error handling is expected to be done by the parent
    } finally {
      setSubmitting(false);
    }
  };

  const categoryOptions = categories.map((c) => ({
    value: String(c.id),
    label: c.name,
  }));

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Edit Transaction' : 'New Transaction'}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input
          label="Title"
          placeholder="e.g. Grocery shopping"
          value={title}
          onChange={(e) => {
            setTitle(e.target.value);
            if (errors.title) setErrors((prev) => ({ ...prev, title: undefined }));
          }}
          error={errors.title}
          required
        />

        <div className="grid grid-cols-2 gap-4">
          <Input
            label="Amount"
            type="number"
            placeholder="0.00"
            min={0}
            step="0.01"
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value);
              if (errors.amount) setErrors((prev) => ({ ...prev, amount: undefined }));
            }}
            error={errors.amount}
            required
          />
          <Input
            label="Currency"
            value={currency}
            readOnly
            disabled
          />
        </div>

        <Select
          label="Category"
          placeholder="Select a category"
          options={categoryOptions}
          value={categoryId}
          onChange={(e) => {
            setCategoryId(e.target.value);
            if (errors.categoryId) setErrors((prev) => ({ ...prev, categoryId: undefined }));
          }}
          error={errors.categoryId}
          required
        />

        <Input
          label="Date"
          type="date"
          value={transactionDate}
          onChange={(e) => {
            setTransactionDate(e.target.value);
            if (errors.transactionDate) setErrors((prev) => ({ ...prev, transactionDate: undefined }));
          }}
          error={errors.transactionDate}
          required
        />

        <div>
          <label htmlFor="notes" className="mb-1 block text-sm font-medium text-gray-700">
            Notes
          </label>
          <textarea
            id="notes"
            rows={3}
            placeholder="Optional notes..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 placeholder-gray-400 shadow-sm transition-colors focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-0"
          />
        </div>

        <div className="flex justify-end gap-3 border-t border-gray-200 pt-4">
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Saving...' : isEdit ? 'Update' : 'Create'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
