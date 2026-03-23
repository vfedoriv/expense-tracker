import { Navigate } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import { LoadingSpinner } from '../components/common/LoadingSpinner';

export function LoginPage() {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <LoadingSpinner size="lg" message="Loading..." />
      </div>
    );
  }

  // In fake auth mode (milestones 1-4), auto-redirect to dashboard if authenticated
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md rounded-xl bg-white p-8 shadow-lg">
        <h1 className="mb-2 text-center text-2xl font-bold text-gray-900">
          Expense Tracker
        </h1>
        <p className="mb-8 text-center text-sm text-gray-500">
          Sign in to manage your expenses
        </p>
        <div className="space-y-3">
          <button
            className="flex w-full items-center justify-center gap-3 rounded-lg border border-gray-300 bg-white px-4 py-3 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            disabled
          >
            Continue with Google
          </button>
          <button
            className="flex w-full items-center justify-center gap-3 rounded-lg border border-gray-300 bg-white px-4 py-3 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            disabled
          >
            Continue with GitHub
          </button>
        </div>
        <p className="mt-4 text-center text-xs text-gray-400">
          SSO login will be available in a future update
        </p>
      </div>
    </div>
  );
}
