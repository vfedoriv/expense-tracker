import { Button } from '../../components/ui/Button';
import type { BudgetSummary } from '../../types';

interface BudgetSummaryCardProps {
  summary: BudgetSummary;
  onSetBudget: () => void;
}

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount);
}

function getProgressColor(percentage: number): string {
  if (percentage > 100) return 'bg-red-500';
  if (percentage >= 80) return 'bg-orange-500';
  if (percentage >= 50) return 'bg-yellow-500';
  return 'bg-green-500';
}

function getProgressTextColor(percentage: number): string {
  if (percentage > 100) return 'text-red-700';
  if (percentage >= 80) return 'text-orange-700';
  if (percentage >= 50) return 'text-yellow-700';
  return 'text-green-700';
}

export function BudgetSummaryCard({ summary, onSetBudget }: BudgetSummaryCardProps) {
  if (!summary.budgetSet) {
    return (
      <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col items-center py-4 text-center">
          <svg
            className="mb-3 h-10 w-10 text-gray-300"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M2.25 18.75a60.07 60.07 0 0115.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 013 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 00-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 01-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 003 15h-.75M15 10.5a3 3 0 11-6 0 3 3 0 016 0zm3 0h.008v.008H18V10.5zm-12 0h.008v.008H6V10.5z"
            />
          </svg>
          <h3 className="text-sm font-medium text-gray-900">No budget set</h3>
          <p className="mt-1 text-sm text-gray-500">
            Set a monthly budget to track your spending
          </p>
          <Button className="mt-4" onClick={onSetBudget}>
            Set Budget
          </Button>
        </div>
      </div>
    );
  }

  const percentage = summary.percentage ?? 0;
  const clampedPercentage = Math.min(percentage, 100);

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-gray-500">Monthly Budget</h3>
        <button
          type="button"
          onClick={onSetBudget}
          className="text-sm font-medium text-indigo-600 transition-colors hover:text-indigo-700"
        >
          Edit
        </button>
      </div>

      <div className="mt-4 flex items-baseline gap-2">
        <span className="text-2xl font-bold text-gray-900">
          {formatCurrency(summary.totalSpent)}
        </span>
        <span className="text-sm text-gray-500">
          of {formatCurrency(summary.budgetAmount!)}
        </span>
      </div>

      <div className="mt-3">
        <div className="h-2.5 w-full overflow-hidden rounded-full bg-gray-100">
          <div
            className={`h-full rounded-full transition-all duration-500 ${getProgressColor(percentage)}`}
            style={{ width: `${clampedPercentage}%` }}
          />
        </div>
        <div className="mt-2 flex items-center justify-between text-sm">
          <span className={`font-medium ${getProgressTextColor(percentage)}`}>
            {percentage.toFixed(0)}% used
          </span>
          <span className="text-gray-500">
            {summary.remaining !== null && summary.remaining >= 0
              ? `${formatCurrency(summary.remaining)} remaining`
              : summary.remaining !== null
                ? `${formatCurrency(Math.abs(summary.remaining))} over budget`
                : ''}
          </span>
        </div>
      </div>
    </div>
  );
}
