import type { BudgetAlertToast } from '../types';

interface BudgetAlertNotificationProps {
  alerts: BudgetAlertToast[];
  onDismiss: (id: string) => void;
}

const severityStyles: Record<BudgetAlertToast['severity'], { container: string; icon: string }> = {
  info: {
    container: 'border-blue-300 bg-blue-50 text-blue-800',
    icon: 'text-blue-500',
  },
  warning: {
    container: 'border-yellow-300 bg-yellow-50 text-yellow-800',
    icon: 'text-yellow-500',
  },
  danger: {
    container: 'border-red-300 bg-red-50 text-red-800',
    icon: 'text-red-500',
  },
};

const severityLabels: Record<BudgetAlertToast['severity'], string> = {
  info: 'Info',
  warning: 'Warning',
  danger: 'Alert',
};

function SeverityIcon({ severity }: { severity: BudgetAlertToast['severity'] }) {
  const iconClass = `h-5 w-5 ${severityStyles[severity].icon}`;

  if (severity === 'info') {
    return (
      <svg className={iconClass} fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    );
  }

  if (severity === 'warning') {
    return (
      <svg className={iconClass} fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4.5c-.77-.833-2.694-.833-3.464 0L3.34 16.5c-.77.833.192 2.5 1.732 2.5z" />
      </svg>
    );
  }

  // danger
  return (
    <svg className={iconClass} fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

export function BudgetAlertNotification({ alerts, onDismiss }: BudgetAlertNotificationProps) {
  if (alerts.length === 0) {
    return null;
  }

  return (
    <div
      className="pointer-events-none fixed right-0 top-0 z-50 flex flex-col items-end gap-3 p-4 sm:p-6"
      aria-live="polite"
      aria-label="Budget alert notifications"
    >
      {alerts.map((alert) => {
        const styles = severityStyles[alert.severity];
        const label = severityLabels[alert.severity];

        return (
          <div
            key={alert.id}
            className={`pointer-events-auto w-full max-w-sm rounded-lg border shadow-lg transition-all duration-300 ${styles.container}`}
            role="alert"
            data-testid={`budget-alert-${alert.severity}`}
          >
            <div className="flex items-start gap-3 p-4">
              <SeverityIcon severity={alert.severity} />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold">{label}: Budget {alert.threshold}%</p>
                <p className="mt-1 text-sm">{alert.message}</p>
                <p className="mt-1 text-xs opacity-75">
                  ${alert.currentSpending.toFixed(2)} of ${alert.budgetAmount.toFixed(2)} spent
                </p>
              </div>
              <button
                type="button"
                className="rounded-md p-1 opacity-70 transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-current focus:ring-offset-2"
                onClick={() => onDismiss(alert.id)}
                aria-label={`Dismiss ${label.toLowerCase()} alert`}
              >
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
