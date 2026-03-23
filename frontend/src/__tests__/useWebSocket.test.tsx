import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { useWebSocket } from '../hooks/useWebSocket';
import { AuthProvider } from '../context/AuthContext';
import { BudgetAlertNotification } from '../components/BudgetAlertNotification';
import type { User, BudgetAlertMessage } from '../types';

// Mock @stomp/stompjs
const mockSubscribe = vi.fn();
const mockActivate = vi.fn();
const mockDeactivate = vi.fn();
let mockOnConnect: (() => void) | null = null;
let mockWebSocketFactory: (() => unknown) | null = null;

vi.mock('@stomp/stompjs', () => {
  return {
    Client: class MockClient {
      active = true;
      subscribe = mockSubscribe;
      activate = mockActivate;
      deactivate = mockDeactivate;

      constructor(config: Record<string, unknown>) {
        mockOnConnect = config.onConnect as () => void;
        mockWebSocketFactory = config.webSocketFactory as () => unknown;
      }
    },
  };
});

const mockSockJSConstructor = vi.fn();
vi.mock('sockjs-client', () => {
  return {
    default: class MockSockJS {
      constructor(url: string) {
        mockSockJSConstructor(url);
      }
    },
  };
});

const mockUser: User = {
  id: 1,
  provider: 'fake',
  providerUserId: 'fake-user-1',
  email: 'admin@test.com',
  displayName: 'Test User',
  avatarUrl: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function TestComponent() {
  const { alerts, dismissAlert } = useWebSocket();
  return (
    <div>
      <BudgetAlertNotification alerts={alerts} onDismiss={dismissAlert} />
      <div data-testid="alert-count">{alerts.length}</div>
    </div>
  );
}

function renderWithAuth() {
  return render(
    <BrowserRouter>
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    </BrowserRouter>
  );
}

describe('useWebSocket', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.restoreAllMocks();
    mockOnConnect = null;
    mockWebSocketFactory = null;
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockUser),
    } as Response);

    // Re-setup mocks after restoreAllMocks
    mockSubscribe.mockReset();
    mockActivate.mockReset();
    mockDeactivate.mockReset();
    mockSockJSConstructor.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('activates STOMP client after authentication', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });
  });

  it('passes userId as query parameter in SockJS connection URL', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    // Call the webSocketFactory to verify the SockJS URL includes userId
    expect(mockWebSocketFactory).not.toBeNull();
    mockWebSocketFactory!();
    expect(mockSockJSConstructor).toHaveBeenCalledWith('/ws?userId=1');
  });

  it('subscribes to /user/topic/budget-alerts on connect', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    // Simulate STOMP connection
    act(() => {
      if (mockOnConnect) mockOnConnect();
    });

    expect(mockSubscribe).toHaveBeenCalledWith(
      '/user/topic/budget-alerts',
      expect.any(Function)
    );
  });

  it('displays alert toast when message is received', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    // Simulate STOMP connection
    act(() => {
      if (mockOnConnect) mockOnConnect();
    });

    // Get the callback function passed to subscribe
    const subscribeCallback = mockSubscribe.mock.calls[0][1];
    const alertMsg: BudgetAlertMessage = {
      type: 'BUDGET_ALERT',
      threshold: 50,
      currentSpending: 250,
      budgetAmount: 500,
      yearMonth: '2026-03',
    };

    // Simulate receiving a message
    act(() => {
      subscribeCallback({ body: JSON.stringify(alertMsg) });
    });

    expect(screen.getByText('You have spent 50% of your monthly budget')).toBeInTheDocument();
    expect(screen.getByTestId('budget-alert-info')).toBeInTheDocument();
  });

  it('shows warning styling for 80% threshold', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    act(() => {
      if (mockOnConnect) mockOnConnect();
    });

    const subscribeCallback = mockSubscribe.mock.calls[0][1];
    const alertMsg: BudgetAlertMessage = {
      type: 'BUDGET_ALERT',
      threshold: 80,
      currentSpending: 400,
      budgetAmount: 500,
      yearMonth: '2026-03',
    };

    act(() => {
      subscribeCallback({ body: JSON.stringify(alertMsg) });
    });

    expect(screen.getByText('You have spent 80% of your monthly budget')).toBeInTheDocument();
    expect(screen.getByTestId('budget-alert-warning')).toBeInTheDocument();
  });

  it('shows danger styling for 100% threshold', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    act(() => {
      if (mockOnConnect) mockOnConnect();
    });

    const subscribeCallback = mockSubscribe.mock.calls[0][1];
    const alertMsg: BudgetAlertMessage = {
      type: 'BUDGET_ALERT',
      threshold: 100,
      currentSpending: 500,
      budgetAmount: 500,
      yearMonth: '2026-03',
    };

    act(() => {
      subscribeCallback({ body: JSON.stringify(alertMsg) });
    });

    expect(screen.getByText('You have spent 100% of your monthly budget')).toBeInTheDocument();
    expect(screen.getByTestId('budget-alert-danger')).toBeInTheDocument();
  });

  it('auto-dismisses alert after 8 seconds', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    act(() => {
      if (mockOnConnect) mockOnConnect();
    });

    const subscribeCallback = mockSubscribe.mock.calls[0][1];
    const alertMsg: BudgetAlertMessage = {
      type: 'BUDGET_ALERT',
      threshold: 50,
      currentSpending: 250,
      budgetAmount: 500,
      yearMonth: '2026-03',
    };

    act(() => {
      subscribeCallback({ body: JSON.stringify(alertMsg) });
    });

    expect(screen.getByText('You have spent 50% of your monthly budget')).toBeInTheDocument();

    // Advance time by 8 seconds
    act(() => {
      vi.advanceTimersByTime(8000);
    });

    expect(screen.queryByText('You have spent 50% of your monthly budget')).not.toBeInTheDocument();
  });

  it('can manually dismiss an alert', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    act(() => {
      if (mockOnConnect) mockOnConnect();
    });

    const subscribeCallback = mockSubscribe.mock.calls[0][1];
    const alertMsg: BudgetAlertMessage = {
      type: 'BUDGET_ALERT',
      threshold: 50,
      currentSpending: 250,
      budgetAmount: 500,
      yearMonth: '2026-03',
    };

    act(() => {
      subscribeCallback({ body: JSON.stringify(alertMsg) });
    });

    expect(screen.getByText('You have spent 50% of your monthly budget')).toBeInTheDocument();

    const dismissButton = screen.getByLabelText('Dismiss info alert');
    await user.click(dismissButton);

    expect(screen.queryByText('You have spent 50% of your monthly budget')).not.toBeInTheDocument();
  });

  it('handles multiple alerts simultaneously', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    act(() => {
      if (mockOnConnect) mockOnConnect();
    });

    const subscribeCallback = mockSubscribe.mock.calls[0][1];

    act(() => {
      subscribeCallback({
        body: JSON.stringify({
          type: 'BUDGET_ALERT',
          threshold: 50,
          currentSpending: 250,
          budgetAmount: 500,
          yearMonth: '2026-03',
        }),
      });
    });

    act(() => {
      subscribeCallback({
        body: JSON.stringify({
          type: 'BUDGET_ALERT',
          threshold: 80,
          currentSpending: 400,
          budgetAmount: 500,
          yearMonth: '2026-03',
        }),
      });
    });

    expect(screen.getByTestId('budget-alert-info')).toBeInTheDocument();
    expect(screen.getByTestId('budget-alert-warning')).toBeInTheDocument();
    expect(screen.getByTestId('alert-count').textContent).toBe('2');
  });

  it('deactivates client on unmount', async () => {
    const { unmount } = renderWithAuth();

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalled();
    });

    unmount();

    expect(mockDeactivate).toHaveBeenCalled();
  });
});
