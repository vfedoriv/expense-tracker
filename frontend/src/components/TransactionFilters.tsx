import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Button } from './ui/Button';
import type { Category, TransactionFilters as TransactionFiltersType } from '../types';

type DatePreset = 'all' | 'this-month' | 'last-month' | 'custom';

interface TransactionFiltersProps {
  filters: TransactionFiltersType;
  onFiltersChange: (filters: TransactionFiltersType) => void;
  categories: Category[];
}

function getThisMonthRange(): { dateFrom: string; dateTo: string } {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth();
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  return {
    dateFrom: formatLocalDate(firstDay),
    dateTo: formatLocalDate(lastDay),
  };
}

function getLastMonthRange(): { dateFrom: string; dateTo: string } {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() - 1;
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  return {
    dateFrom: formatLocalDate(firstDay),
    dateTo: formatLocalDate(lastDay),
  };
}

function formatLocalDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function detectPreset(filters: TransactionFiltersType): DatePreset {
  if (!filters.dateFrom && !filters.dateTo) return 'all';
  const thisMonth = getThisMonthRange();
  if (filters.dateFrom === thisMonth.dateFrom && filters.dateTo === thisMonth.dateTo)
    return 'this-month';
  const lastMonth = getLastMonthRange();
  if (filters.dateFrom === lastMonth.dateFrom && filters.dateTo === lastMonth.dateTo)
    return 'last-month';
  if (filters.dateFrom || filters.dateTo) return 'custom';
  return 'all';
}

function hasActiveFilters(filters: TransactionFiltersType): boolean {
  return !!(
    filters.search ||
    filters.categoryId !== undefined ||
    filters.dateFrom ||
    filters.dateTo ||
    filters.amountMin !== undefined ||
    filters.amountMax !== undefined
  );
}

/**
 * Inner component for the search input that manages its own debounce state.
 * Keyed by a resetKey from the parent to reset on clear.
 */
function SearchInput({
  initialValue,
  filters,
  onFiltersChange,
}: {
  initialValue: string;
  filters: TransactionFiltersType;
  onFiltersChange: (filters: TransactionFiltersType) => void;
}) {
  const [value, setValue] = useState(initialValue);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleChange = useCallback(
    (newValue: string) => {
      setValue(newValue);

      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }

      debounceTimerRef.current = setTimeout(() => {
        const trimmed = newValue.trim();
        onFiltersChange({
          ...filters,
          search: trimmed || undefined,
        });
      }, 300);
    },
    [filters, onFiltersChange],
  );

  // Cleanup debounce timer on unmount
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  return (
    <div className="relative">
      <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
        <svg
          className="h-4 w-4 text-gray-400"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
      </div>
      <input
        type="text"
        value={value}
        onChange={(e) => handleChange(e.target.value)}
        placeholder="Search transactions..."
        className="block w-full rounded-lg border border-gray-300 py-2 pl-10 pr-3 text-sm shadow-sm transition-colors placeholder:text-gray-400 hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
        aria-label="Search transactions"
      />
    </div>
  );
}

export function TransactionFilters({
  filters,
  onFiltersChange,
  categories,
}: TransactionFiltersProps) {
  // resetKey increments on clear to force SearchInput to remount with empty value
  const [resetKey, setResetKey] = useState(0);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  // Track whether user explicitly selected "Custom" (for date picker visibility)
  const [customDateSelected, setCustomDateSelected] = useState(false);

  // Derive datePreset from filters on every render — no local state needed
  const datePreset = useMemo((): DatePreset => {
    if (customDateSelected) return 'custom';
    return detectPreset(filters);
  }, [filters, customDateSelected]);

  function handleCategoryChange(value: string) {
    onFiltersChange({
      ...filters,
      categoryId: value ? Number(value) : undefined,
    });
  }

  function handleDatePresetChange(preset: DatePreset) {
    setCustomDateSelected(preset === 'custom');
    if (preset === 'this-month') {
      const range = getThisMonthRange();
      onFiltersChange({ ...filters, dateFrom: range.dateFrom, dateTo: range.dateTo });
    } else if (preset === 'last-month') {
      const range = getLastMonthRange();
      onFiltersChange({ ...filters, dateFrom: range.dateFrom, dateTo: range.dateTo });
    } else if (preset === 'all') {
      onFiltersChange({ ...filters, dateFrom: undefined, dateTo: undefined });
    }
    // 'custom' doesn't change dates immediately — user picks them
  }

  function handleCustomDateFrom(value: string) {
    onFiltersChange({ ...filters, dateFrom: value || undefined });
  }

  function handleCustomDateTo(value: string) {
    onFiltersChange({ ...filters, dateTo: value || undefined });
  }

  function handleAmountMin(value: string) {
    const num = value === '' ? undefined : Number(value);
    onFiltersChange({ ...filters, amountMin: num });
  }

  function handleAmountMax(value: string) {
    const num = value === '' ? undefined : Number(value);
    onFiltersChange({ ...filters, amountMax: num });
  }

  function handleClearFilters() {
    setResetKey((k) => k + 1);
    setCustomDateSelected(false);
    onFiltersChange({});
  }

  const activeFilters = hasActiveFilters(filters);

  return (
    <div className="mb-6 space-y-3">
      {/* Search input — keyed by resetKey to reset on clear */}
      <SearchInput
        key={resetKey}
        initialValue={filters.search ?? ''}
        filters={filters}
        onFiltersChange={onFiltersChange}
      />

      {/* Filter toggle + Clear */}
      <div className="flex items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => setFiltersExpanded(!filtersExpanded)}
          aria-expanded={filtersExpanded}
          aria-controls="filter-panel"
        >
          <svg
            className="mr-1.5 h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z"
            />
          </svg>
          Filters
          {activeFilters && !filters.search && (
            <span className="ml-1.5 inline-flex h-5 w-5 items-center justify-center rounded-full bg-blue-100 text-xs font-medium text-blue-700">
              !
            </span>
          )}
        </Button>
        {activeFilters && (
          <Button variant="ghost" size="sm" onClick={handleClearFilters}>
            Clear Filters
          </Button>
        )}
      </div>

      {/* Collapsible filter panel */}
      {filtersExpanded && (
        <div
          id="filter-panel"
          className="grid gap-4 rounded-xl border border-gray-200 bg-gray-50 p-4 sm:grid-cols-2 lg:grid-cols-4"
        >
          {/* Category filter */}
          <div>
            <label
              htmlFor="filter-category"
              className="mb-1 block text-xs font-medium text-gray-700"
            >
              Category
            </label>
            <select
              id="filter-category"
              value={filters.categoryId !== undefined ? String(filters.categoryId) : ''}
              onChange={(e) => handleCategoryChange(e.target.value)}
              className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm transition-colors hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
            >
              <option value="">All Categories</option>
              {categories.map((cat) => (
                <option key={cat.id} value={String(cat.id)}>
                  {cat.name}
                </option>
              ))}
            </select>
          </div>

          {/* Date range */}
          <div className="sm:col-span-2 lg:col-span-1">
            <label
              htmlFor="filter-date-preset"
              className="mb-1 block text-xs font-medium text-gray-700"
            >
              Date Range
            </label>
            <select
              id="filter-date-preset"
              value={datePreset}
              onChange={(e) => handleDatePresetChange(e.target.value as DatePreset)}
              className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm transition-colors hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
            >
              <option value="all">All Time</option>
              <option value="this-month">This Month</option>
              <option value="last-month">Last Month</option>
              <option value="custom">Custom</option>
            </select>
            {datePreset === 'custom' && (
              <div className="mt-2 grid grid-cols-2 gap-2">
                <div>
                  <label htmlFor="filter-date-from" className="sr-only">
                    From date
                  </label>
                  <input
                    id="filter-date-from"
                    type="date"
                    value={filters.dateFrom ?? ''}
                    onChange={(e) => handleCustomDateFrom(e.target.value)}
                    className="block w-full rounded-lg border border-gray-300 px-2 py-1.5 text-sm shadow-sm transition-colors hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
                    aria-label="From date"
                  />
                </div>
                <div>
                  <label htmlFor="filter-date-to" className="sr-only">
                    To date
                  </label>
                  <input
                    id="filter-date-to"
                    type="date"
                    value={filters.dateTo ?? ''}
                    onChange={(e) => handleCustomDateTo(e.target.value)}
                    className="block w-full rounded-lg border border-gray-300 px-2 py-1.5 text-sm shadow-sm transition-colors hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
                    aria-label="To date"
                  />
                </div>
              </div>
            )}
          </div>

          {/* Amount range */}
          <div className="sm:col-span-2 lg:col-span-2">
            <span className="mb-1 block text-xs font-medium text-gray-700">Amount Range</span>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label htmlFor="filter-amount-min" className="sr-only">
                  Min amount
                </label>
                <input
                  id="filter-amount-min"
                  type="number"
                  value={filters.amountMin !== undefined ? String(filters.amountMin) : ''}
                  onChange={(e) => handleAmountMin(e.target.value)}
                  placeholder="Min"
                  step="0.01"
                  min="0"
                  className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-gray-400 hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
                  aria-label="Minimum amount"
                />
              </div>
              <div>
                <label htmlFor="filter-amount-max" className="sr-only">
                  Max amount
                </label>
                <input
                  id="filter-amount-max"
                  type="number"
                  value={filters.amountMax !== undefined ? String(filters.amountMax) : ''}
                  onChange={(e) => handleAmountMax(e.target.value)}
                  placeholder="Max"
                  step="0.01"
                  min="0"
                  className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-gray-400 hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-0"
                  aria-label="Maximum amount"
                />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
