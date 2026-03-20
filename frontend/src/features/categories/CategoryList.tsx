import { useState, useRef, useEffect } from 'react';
import { Button } from '../../components/ui/Button';
import type { Category } from '../../types';

interface CategoryListProps {
  categories: Category[];
  onRename: (id: number, newName: string) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  error: string | null;
}

export function CategoryList({ categories, onRename, onDelete, error }: CategoryListProps) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState('');
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const editInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (editingId !== null && editInputRef.current) {
      editInputRef.current.focus();
      editInputRef.current.select();
    }
  }, [editingId]);

  const startEditing = (category: Category) => {
    setEditingId(category.id);
    setEditingName(category.name);
    setActionError(null);
  };

  const cancelEditing = () => {
    setEditingId(null);
    setEditingName('');
  };

  const saveEditing = async () => {
    if (editingId === null || !editingName.trim()) return;

    try {
      await onRename(editingId, editingName.trim());
      setEditingId(null);
      setEditingName('');
      setActionError(null);
    } catch {
      setActionError('Failed to rename category');
    }
  };

  const handleEditKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      saveEditing();
    } else if (e.key === 'Escape') {
      cancelEditing();
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await onDelete(id);
      setConfirmDeleteId(null);
      setActionError(null);
    } catch {
      setActionError('Cannot delete category: it may still have transactions.');
    }
  };

  const displayError = error ?? actionError;

  if (categories.length === 0) {
    return (
      <p className="py-6 text-center text-sm text-gray-500">
        No categories yet. Add one below.
      </p>
    );
  }

  return (
    <div>
      {displayError && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {displayError}
        </div>
      )}

      <ul className="divide-y divide-gray-100">
        {categories.map((category) => (
          <li
            key={category.id}
            className="flex items-center justify-between gap-3 py-3"
          >
            {editingId === category.id ? (
              <div className="flex flex-1 items-center gap-2">
                <input
                  ref={editInputRef}
                  type="text"
                  value={editingName}
                  onChange={(e) => setEditingName(e.target.value)}
                  onKeyDown={handleEditKeyDown}
                  onBlur={cancelEditing}
                  className="flex-1 rounded-md border border-indigo-300 px-3 py-1.5 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                <Button
                  size="sm"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={saveEditing}
                >
                  Save
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={cancelEditing}
                >
                  Cancel
                </Button>
              </div>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => startEditing(category)}
                  className="flex-1 cursor-pointer text-left text-sm font-medium text-gray-900 transition-colors hover:text-indigo-600"
                  title="Click to rename"
                >
                  {category.name}
                </button>
                <div className="flex items-center gap-2">
                  <Button variant="secondary" size="sm" onClick={() => startEditing(category)}>
                    Rename
                  </Button>
                  {confirmDeleteId === category.id ? (
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-red-600">Delete?</span>
                      <Button
                        variant="danger"
                        size="sm"
                        onClick={() => handleDelete(category.id)}
                      >
                        Yes
                      </Button>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setConfirmDeleteId(null)}
                      >
                        No
                      </Button>
                    </div>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => {
                        setConfirmDeleteId(category.id);
                        setActionError(null);
                      }}
                    >
                      Delete
                    </Button>
                  )}
                </div>
              </>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
