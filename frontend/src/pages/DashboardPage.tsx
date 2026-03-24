import { useState, useCallback } from 'react';
import { useDashboard } from '../hooks/useDashboard';
import { useBudget } from '../hooks/useBudget';
import { formatCurrency } from '../utils/formatters';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Modal } from '../components/ui/Modal';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
] as const;

function getInitialMonth(): { year: number; month: number } {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1 };
}

function getProgressBarColor(percentage: number): string {
  if (percentage > 80) return 'bg-red-500';
  if (percentage >= 50) return 'bg-yellow-500';
  return 'bg-green-500';
}

function getProgressBarBgColor(percentage: number): string {
  if (percentage > 80) return 'bg-red-100';
  if (percentage >= 50) return 'bg-yellow-100';
  return 'bg-green-100';
}

export function DashboardPage() {
  const [selectedDate, setSelectedDate] = useState(getInitialMonth);
  const { dashboard, loading, error, fetchDashboard } = useDashboard(
    selectedDate.year,
    selectedDate.month,
  );

  const [budgetModalOpen, setBudgetModalOpen] = useState(false);
  const [budgetAmount, setBudgetAmount] = useState('');
  const [budgetValidationError, setBudgetValidationError] = useState('');
  const { saving, error: budgetError, saveBudget, clearError: clearBudgetError } = useBudget();

  const handlePrevMonth = useCallback(() => {
    setSelectedDate((prev) => {
      if (prev.month === 1) {
        return { year: prev.year - 1, month: 12 };
      }
      return { year: prev.year, month: prev.month - 1 };
    });
  }, []);

  const handleNextMonth = useCallback(() => {
    setSelectedDate((prev) => {
      if (prev.month === 12) {
        return { year: prev.year + 1, month: 1 };
      }
      return { year: prev.year, month: prev.month + 1 };
    });
  }, []);

  const handleOpenBudgetModal = useCallback(() => {
    // Pre-populate with existing budget if set
    if (dashboard?.budgetAmount !== null && dashboard?.budgetAmount !== undefined) {
      setBudgetAmount(String(dashboard.budgetAmount));
    } else {
      setBudgetAmount('');
    }
    setBudgetValidationError('');
    clearBudgetError();
    setBudgetModalOpen(true);
  }, [dashboard, clearBudgetError]);

  const handleCloseBudgetModal = useCallback(() => {
    setBudgetModalOpen(false);
    setBudgetAmount('');
    setBudgetValidationError('');
    clearBudgetError();
  }, [clearBudgetError]);

  const handleSaveBudget = useCallback(async () => {
    // Validate
    const trimmed = budgetAmount.trim();
    if (!trimmed) {
      setBudgetValidationError('Budget amount is required');
      return;
    }
    const amount = parseFloat(trimmed);
    if (isNaN(amount) || amount <= 0) {
      setBudgetValidationError('Budget amount must be greater than 0');
      return;
    }

    setBudgetValidationError('');

    try {
      await saveBudget({
        year: selectedDate.year,
        month: selectedDate.month,
        amount,
      });
      handleCloseBudgetModal();
      // Refresh dashboard data
      await fetchDashboard(selectedDate.year, selectedDate.month);
    } catch {
      // Error is already set in the hook
    }
  }, [budgetAmount, selectedDate, saveBudget, handleCloseBudgetModal, fetchDashboard]);

  const hasBudget = dashboard !== null && dashboard.budgetAmount !== null;
  const usagePercentage = dashboard?.usagePercentage ?? 0;
  const displayPercentage = Math.min(usagePercentage, 100);

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-gray-900">Dashboard</h1>

      {/* Month Selector */}
      <div className="mb-6 flex items-center gap-3">
        <button
          onClick={handlePrevMonth}
          className="rounded-lg border border-gray-300 p-2 text-gray-600 transition-colors hover:border-gray-400 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          aria-label="Previous month"
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M15 19l-7-7 7-7"
            />
          </svg>
        </button>
        <span className="min-w-[180px] text-center text-lg font-semibold text-gray-900">
          {MONTH_NAMES[selectedDate.month - 1]} {selectedDate.year}
        </span>
        <button
          onClick={handleNextMonth}
          className="rounded-lg border border-gray-300 p-2 text-gray-600 transition-colors hover:border-gray-400 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          aria-label="Next month"
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      {/* Loading State */}
      {loading && <LoadingSpinner message="Loading dashboard data..." />}

      {/* Error State */}
      {error && !loading && (
        <ErrorMessage
          message={error}
          onRetry={() => void fetchDashboard(selectedDate.year, selectedDate.month)}
        />
      )}

      {/* Dashboard Content */}
      {!loading && !error && dashboard && (
        <div className="space-y-6">
          {/* Stats Grid */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {/* Total Spent Card */}
            <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
              <p className="text-sm font-medium text-gray-500">Total Spent</p>
              <p className="mt-2 text-3xl font-bold text-gray-900">
                {formatCurrency(dashboard.totalSpent)}
              </p>
              <p className="mt-1 text-sm text-gray-500">
                {MONTH_NAMES[selectedDate.month - 1]} {selectedDate.year}
              </p>
            </div>

            {/* Budget Card */}
            <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
              <p className="text-sm font-medium text-gray-500">Budget</p>
              {hasBudget ? (
                <>
                  <p className="mt-2 text-3xl font-bold text-gray-900">
                    {formatCurrency(dashboard.budgetAmount as number)}
                  </p>
                  <button
                    onClick={handleOpenBudgetModal}
                    className="mt-1 rounded text-sm font-medium text-blue-600 transition-colors hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                  >
                    Edit Budget
                  </button>
                </>
              ) : (
                <>
                  <p className="mt-2 text-lg font-medium text-gray-400">No budget set</p>
                  <button
                    onClick={handleOpenBudgetModal}
                    className="mt-1 rounded text-sm font-medium text-blue-600 transition-colors hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                  >
                    Set Budget
                  </button>
                </>
              )}
            </div>

            {/* Remaining Budget Card - only show if budget exists */}
            {hasBudget && (
              <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
                <p className="text-sm font-medium text-gray-500">Remaining</p>
                <p
                  className={`mt-2 text-3xl font-bold ${
                    (dashboard.remaining as number) < 0 ? 'text-red-600' : 'text-gray-900'
                  }`}
                >
                  {formatCurrency(dashboard.remaining as number)}
                </p>
                {(dashboard.remaining as number) < 0 && (
                  <p className="mt-1 text-sm font-medium text-red-600">Over budget</p>
                )}
              </div>
            )}
          </div>

          {/* Budget Usage Progress Bar - only show if budget exists */}
          {hasBudget && (
            <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium text-gray-500">Budget Usage</p>
                <p className="text-sm font-semibold text-gray-900">{usagePercentage.toFixed(1)}%</p>
              </div>
              <div
                className={`mt-3 h-4 w-full overflow-hidden rounded-full ${getProgressBarBgColor(usagePercentage)}`}
              >
                <div
                  className={`h-full rounded-full transition-all duration-500 ${getProgressBarColor(usagePercentage)}`}
                  style={{ width: `${displayPercentage}%` }}
                  role="progressbar"
                  aria-valuenow={usagePercentage}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label={`Budget usage: ${usagePercentage.toFixed(1)}%`}
                />
              </div>
              <div className="mt-2 flex justify-between text-xs text-gray-400">
                <span>0%</span>
                <span>50%</span>
                <span>100%</span>
              </div>
            </div>
          )}

          {/* No Budget Empty State */}
          {!hasBudget && (
            <EmptyState
              title="No budget set for this month"
              description="Set a monthly budget to track your spending and see how you're doing."
              icon={
                <svg className="h-12 w-12" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              }
              action={<Button onClick={handleOpenBudgetModal}>Set Budget</Button>}
            />
          )}
        </div>
      )}

      {/* Budget Modal */}
      <Modal
        isOpen={budgetModalOpen}
        onClose={handleCloseBudgetModal}
        title={hasBudget ? 'Edit Budget' : 'Set Budget'}
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-500">
            Set your budget for {MONTH_NAMES[selectedDate.month - 1]} {selectedDate.year}
          </p>
          <Input
            label="Budget Amount ($)"
            type="number"
            min="0.01"
            step="0.01"
            placeholder="Enter budget amount"
            value={budgetAmount}
            onChange={(e) => {
              setBudgetAmount(e.target.value);
              setBudgetValidationError('');
            }}
            error={budgetValidationError}
          />
          {budgetError && <ErrorMessage message={budgetError} />}
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={handleCloseBudgetModal} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={() => void handleSaveBudget()} disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
