import { useEffect, useRef } from 'react';
import { useWebSocket } from '../../context/WebSocketContext';

const AUTO_DISMISS_MS = 8000;

function getAlertStyles(threshold: number): { border: string; bg: string; text: string; icon: string } {
  if (threshold >= 100) {
    return {
      border: 'border-red-400',
      bg: 'bg-red-50',
      text: 'text-red-800',
      icon: 'text-red-500',
    };
  }
  if (threshold >= 80) {
    return {
      border: 'border-orange-400',
      bg: 'bg-orange-50',
      text: 'text-orange-800',
      icon: 'text-orange-500',
    };
  }
  return {
    border: 'border-yellow-400',
    bg: 'bg-yellow-50',
    text: 'text-yellow-800',
    icon: 'text-yellow-500',
  };
}

function getAlertLabel(threshold: number): string {
  if (threshold >= 100) return 'Budget exceeded';
  if (threshold >= 80) return 'Budget warning';
  return 'Budget notice';
}

export function AlertContainer() {
  const { alerts, dismissAlert } = useWebSocket();
  const timersRef = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  useEffect(() => {
    const currentTimers = timersRef.current;

    for (const alert of alerts) {
      if (!currentTimers.has(alert.threshold)) {
        const timer = setTimeout(() => {
          dismissAlert(alert.threshold);
          currentTimers.delete(alert.threshold);
        }, AUTO_DISMISS_MS);
        currentTimers.set(alert.threshold, timer);
      }
    }

    // Cleanup timers for dismissed alerts
    for (const [threshold, timer] of currentTimers.entries()) {
      if (!alerts.some((a) => a.threshold === threshold)) {
        clearTimeout(timer);
        currentTimers.delete(threshold);
      }
    }

    return () => {
      for (const timer of currentTimers.values()) {
        clearTimeout(timer);
      }
    };
  }, [alerts, dismissAlert]);

  if (alerts.length === 0) return null;

  return (
    <div className="fixed right-4 top-4 z-50 flex w-80 flex-col gap-3">
      {alerts.map((alert) => {
        const styles = getAlertStyles(alert.threshold);

        return (
          <div
            key={alert.threshold}
            className={`rounded-lg border-l-4 ${styles.border} ${styles.bg} p-4 shadow-lg transition-all duration-300`}
            role="alert"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                <svg
                  className={`mt-0.5 h-5 w-5 shrink-0 ${styles.icon}`}
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth={2}
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"
                  />
                </svg>
                <div>
                  <p className={`text-sm font-semibold ${styles.text}`}>
                    {getAlertLabel(alert.threshold)}
                  </p>
                  <p className={`mt-1 text-sm ${styles.text} opacity-90`}>
                    {alert.message}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => dismissAlert(alert.threshold)}
                className={`shrink-0 rounded p-1 ${styles.text} opacity-70 transition-opacity hover:opacity-100`}
                aria-label="Dismiss alert"
              >
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
