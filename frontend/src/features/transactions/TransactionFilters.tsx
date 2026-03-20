import { useState, useEffect } from 'react';
import { useDebounce } from '../../hooks/useDebounce';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Button } from '../../components/ui/Button';
import type { Category } from '../../types';

export interface TransactionFilterValues {
  q: string;
  categoryId: string;
  dateFrom: string;
  dateTo: string;
  amountMin: string;
  amountMax: string;
}

interface TransactionFiltersProps {
  filters: TransactionFilterValues;
  onFilterChange: (filters: TransactionFilterValues) => void;
  categories: Category[];
}

function getThisMonthRange(): { dateFrom: string; dateTo: string } {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const lastDay = new Date(year, now.getMonth() + 1, 0).getDate();
  return {
    dateFrom: `${year}-${month}-01`,
    dateTo: `${year}-${month}-${String(lastDay).padStart(2, '0')}`,
  };
}

function getLastMonthRange(): { dateFrom: string; dateTo: string } {
  const now = new Date();
  const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const year = lastMonth.getFullYear();
  const month = String(lastMonth.getMonth() + 1).padStart(2, '0');
  const lastDay = new Date(year, lastMonth.getMonth() + 1, 0).getDate();
  return {
    dateFrom: `${year}-${month}-01`,
    dateTo: `${year}-${month}-${String(lastDay).padStart(2, '0')}`,
  };
}

export function TransactionFilters({ filters, onFilterChange, categories }: TransactionFiltersProps) {
  const [searchTerm, setSearchTerm] = useState(filters.q);
  const debouncedSearch = useDebounce(searchTerm, 300);

  useEffect(() => {
    if (debouncedSearch !== filters.q) {
      onFilterChange({ ...filters, q: debouncedSearch });
    }
  }, [debouncedSearch]); // eslint-disable-line react-hooks/exhaustive-deps

  const updateFilter = (key: keyof TransactionFilterValues, value: string) => {
    onFilterChange({ ...filters, [key]: value });
  };

  const handleThisMonth = () => {
    const range = getThisMonthRange();
    onFilterChange({ ...filters, ...range });
  };

  const handleLastMonth = () => {
    const range = getLastMonthRange();
    onFilterChange({ ...filters, ...range });
  };

  const handleClearFilters = () => {
    setSearchTerm('');
    onFilterChange({
      q: '',
      categoryId: '',
      dateFrom: '',
      dateTo: '',
      amountMin: '',
      amountMax: '',
    });
  };

  const categoryOptions = categories.map((c) => ({
    value: String(c.id),
    label: c.name,
  }));

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Input
          label="Search"
          placeholder="Search transactions..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />

        <Select
          label="Category"
          placeholder="All categories"
          options={categoryOptions}
          value={filters.categoryId}
          onChange={(e) => updateFilter('categoryId', e.target.value)}
        />

        <Input
          label="Date from"
          type="date"
          value={filters.dateFrom}
          onChange={(e) => updateFilter('dateFrom', e.target.value)}
        />

        <Input
          label="Date to"
          type="date"
          value={filters.dateTo}
          onChange={(e) => updateFilter('dateTo', e.target.value)}
        />

        <Input
          label="Min amount"
          type="number"
          placeholder="0.00"
          min={0}
          step="0.01"
          value={filters.amountMin}
          onChange={(e) => updateFilter('amountMin', e.target.value)}
        />

        <Input
          label="Max amount"
          type="number"
          placeholder="0.00"
          min={0}
          step="0.01"
          value={filters.amountMax}
          onChange={(e) => updateFilter('amountMax', e.target.value)}
        />

        <div className="flex items-end gap-2">
          <Button variant="secondary" size="sm" onClick={handleThisMonth}>
            This Month
          </Button>
          <Button variant="secondary" size="sm" onClick={handleLastMonth}>
            Last Month
          </Button>
        </div>

        <div className="flex items-end">
          <button
            type="button"
            onClick={handleClearFilters}
            className="px-3 py-1.5 text-sm font-medium text-gray-500 hover:text-gray-700 transition-colors"
          >
            Clear filters
          </button>
        </div>
      </div>
    </div>
  );
}
