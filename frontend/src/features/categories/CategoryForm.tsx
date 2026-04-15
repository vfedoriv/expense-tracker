import { useState, type FormEvent } from 'react';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';

interface CategoryFormProps {
  onSubmit: (name: string) => Promise<void>;
  error: string | null;
}

export function CategoryForm({ onSubmit, error }: CategoryFormProps) {
  const [name, setName] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    const trimmed = name.trim();
    if (!trimmed) return;

    setSubmitting(true);
    try {
      await onSubmit(trimmed);
      setName('');
    } catch {
      // Error display handled via the error prop
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      <div className="flex items-end gap-3">
        <div className="flex-1">
          <Input
            label="New category"
            placeholder="e.g. Groceries"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={submitting}
          />
        </div>
        <Button type="submit" disabled={!name.trim() || submitting}>
          {submitting ? 'Adding...' : 'Add'}
        </Button>
      </div>
    </form>
  );
}
