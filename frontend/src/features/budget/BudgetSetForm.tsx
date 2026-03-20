import { useState, useEffect, type FormEvent } from 'react';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';

interface BudgetSetFormProps {
  currentAmount: number | null;
  onSave: (amount: number) => Promise<void>;
  onDelete: () => Promise<void>;
}

export function BudgetSetForm({ currentAmount, onSave, onDelete }: BudgetSetFormProps) {
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const hasBudget = currentAmount !== null;

  useEffect(() => {
    if (currentAmount !== null) {
      setAmount(String(currentAmount));
    } else {
      setAmount('');
    }
  }, [currentAmount]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    const parsed = parseFloat(amount);
    if (!amount || isNaN(parsed) || parsed <= 0) {
      setError('Budget amount must be greater than 0');
      return;
    }

    setSaving(true);
    try {
      await onSave(parsed);
    } catch {
      setError('Failed to save budget');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    setError(null);
    setDeleting(true);
    try {
      await onDelete();
    } catch {
      setError('Failed to delete budget');
    } finally {
      setDeleting(false);
    }
  };

  const busy = saving || deleting;

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <Input
        label="Monthly budget amount"
        type="number"
        placeholder="0.00"
        min={0}
        step="0.01"
        value={amount}
        onChange={(e) => {
          setAmount(e.target.value);
          if (error) setError(null);
        }}
        disabled={busy}
      />

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={busy || !amount.trim()}>
          {saving ? 'Saving...' : 'Save'}
        </Button>
        {hasBudget && (
          <Button
            type="button"
            variant="danger"
            onClick={handleDelete}
            disabled={busy}
          >
            {deleting ? 'Deleting...' : 'Delete Budget'}
          </Button>
        )}
      </div>
    </form>
  );
}
