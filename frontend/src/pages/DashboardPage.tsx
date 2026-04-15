import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { budgetsApi } from '../api/budgets';
import { transactionsApi } from '../api/transactions';
import type { BudgetSummary, Transaction } from '../types';
import { BudgetSummaryCard } from '../features/budget/BudgetSummaryCard';
import { BudgetSetForm } from '../features/budget/BudgetSetForm';
import { LoadingSpinner } from '../components/ui/LoadingSpinner';
import { Modal } from '../components/ui/Modal';

export function DashboardPage() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [summary, setSummary] = useState<BudgetSummary | null>(null);
  const [recentTransactions, setRecentTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [budgetModalOpen, setBudgetModalOpen] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [budgetData, txData] = await Promise.all([
        budgetsApi.getSummary(year, month),
        transactionsApi.search({
          dateFrom: `${year}-${String(month).padStart(2, '0')}-01`,
          dateTo: `${year}-${String(month).padStart(2, '0')}-${new Date(year, month, 0).getDate()}`,
          size: 5,
          sort: 'transactionDate,desc',
        }),
      ]);
      setSummary(budgetData);
      setRecentTransactions(txData.content);
    } catch {
      // handled by API client
    } finally {
      setLoading(false);
    }
  }, [year, month]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleMonthChange = (offset: number) => {
    let m = month + offset;
    let y = year;
    if (m > 12) { m = 1; y++; }
    if (m < 1) { m = 12; y--; }
    setMonth(m);
    setYear(y);
  };

  const handleSaveBudget = async (amount: number) => {
    await budgetsApi.setBudget(year, month, amount);
    setBudgetModalOpen(false);
    fetchData();
  };

  const handleDeleteBudget = async () => {
    await budgetsApi.deleteBudget(year, month);
    setBudgetModalOpen(false);
    fetchData();
  };

  const monthName = new Date(year, month - 1).toLocaleString('default', { month: 'long', year: 'numeric' });

  if (loading) return <LoadingSpinner />;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <div className="flex items-center gap-2">
          <button onClick={() => handleMonthChange(-1)} className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" /></svg>
          </button>
          <span className="text-lg font-medium text-gray-900 min-w-[180px] text-center">{monthName}</span>
          <button onClick={() => handleMonthChange(1)} className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" /></svg>
          </button>
        </div>
      </div>

      {summary && (
        <BudgetSummaryCard summary={summary} onSetBudget={() => setBudgetModalOpen(true)} />
      )}

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        <div className="p-4 border-b border-gray-200 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">Recent Transactions</h2>
          <Link to="/transactions" className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
            View all
          </Link>
        </div>
        {recentTransactions.length === 0 ? (
          <div className="p-8 text-center text-gray-500">No transactions this month</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {recentTransactions.map(tx => (
              <li key={tx.id} className="px-4 py-3 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-900">{tx.title}</p>
                  <p className="text-xs text-gray-500">{tx.categoryName} &middot; {tx.transactionDate}</p>
                </div>
                <span className="text-sm font-semibold text-gray-900">
                  {new Intl.NumberFormat('en-US', { style: 'currency', currency: tx.currency }).format(tx.amount)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <Modal open={budgetModalOpen} onClose={() => setBudgetModalOpen(false)} title={`Set Budget — ${monthName}`}>
        <BudgetSetForm
          currentAmount={summary?.budgetAmount ?? null}
          onSave={handleSaveBudget}
          onDelete={handleDeleteBudget}
        />
      </Modal>
    </div>
  );
}
