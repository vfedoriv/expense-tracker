import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { useWebSocket } from '../../hooks/useWebSocket';
import { BudgetAlertNotification } from '../BudgetAlertNotification';

const navItems = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/transactions', label: 'Transactions' },
  { to: '/categories', label: 'Categories' },
] as const;

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { alerts, dismissAlert } = useWebSocket();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <BudgetAlertNotification alerts={alerts} onDismiss={dismissAlert} />
      <header className="border-b border-gray-200 bg-white shadow-sm">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between">
            {/* App name */}
            <div className="flex items-center gap-8">
              <NavLink to="/dashboard" className="text-xl font-bold text-blue-600">
                Expense Tracker
              </NavLink>

              {/* Desktop navigation */}
              <nav className="hidden md:flex md:gap-1" aria-label="Main navigation">
                {navItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      `rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-blue-50 text-blue-700'
                          : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                      }`
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </div>

            {/* User info + logout + mobile menu button */}
            <div className="flex items-center gap-3">
              {user && (
                <div className="hidden items-center gap-2 sm:flex">
                  {user.avatarUrl ? (
                    <img
                      src={user.avatarUrl}
                      alt={user.displayName}
                      className="h-8 w-8 rounded-full object-cover"
                      data-testid="user-avatar"
                    />
                  ) : (
                    <div
                      className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-sm font-medium text-blue-700"
                      data-testid="user-avatar-placeholder"
                      aria-label={user.displayName}
                    >
                      {user.displayName.charAt(0).toUpperCase()}
                    </div>
                  )}
                  <span className="text-sm text-gray-700">
                    {user.displayName}
                  </span>
                </div>
              )}

              {/* Logout button */}
              <button
                onClick={handleLogout}
                className="hidden rounded-lg px-3 py-2 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 sm:block"
                data-testid="logout-btn"
              >
                Logout
              </button>

              {/* Mobile menu button */}
              <button
                className="rounded-lg p-2 text-gray-600 transition-colors hover:bg-gray-100 md:hidden"
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                aria-label="Toggle navigation menu"
                aria-expanded={mobileMenuOpen}
              >
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  {mobileMenuOpen ? (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  ) : (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                  )}
                </svg>
              </button>
            </div>
          </div>
        </div>

        {/* Mobile navigation */}
        {mobileMenuOpen && (
          <nav className="border-t border-gray-200 px-4 py-2 md:hidden" aria-label="Mobile navigation">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={() => setMobileMenuOpen(false)}
                className={({ isActive }) =>
                  `block rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-blue-50 text-blue-700'
                      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
            {user && (
              <div className="mt-2 flex items-center gap-2 border-t border-gray-200 px-3 py-2">
                {user.avatarUrl ? (
                  <img
                    src={user.avatarUrl}
                    alt={user.displayName}
                    className="h-6 w-6 rounded-full object-cover"
                  />
                ) : (
                  <div className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-100 text-xs font-medium text-blue-700">
                    {user.displayName.charAt(0).toUpperCase()}
                  </div>
                )}
                <span className="text-sm text-gray-500">{user.displayName}</span>
              </div>
            )}
            <button
              onClick={handleLogout}
              className="mt-1 block w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900"
              data-testid="mobile-logout-btn"
            >
              Logout
            </button>
          </nav>
        )}
      </header>

      {/* Main content */}
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        <Outlet />
      </main>
    </div>
  );
}
