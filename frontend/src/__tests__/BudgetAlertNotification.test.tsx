import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BudgetAlertNotification } from '../components/BudgetAlertNotification';
import type { BudgetAlertToast } from '../types';

function createAlert(overrides: Partial<BudgetAlertToast> = {}): BudgetAlertToast {
  return {
    id: 'test-alert-1',
    threshold: 50,
    currentSpending: 250,
    budgetAmount: 500,
    yearMonth: '2026-03',
    severity: 'info',
    message: 'You have spent 50% of your monthly budget',
    ...overrides,
  };
}

describe('BudgetAlertNotification', () => {
  it('renders nothing when there are no alerts', () => {
    const { container } = render(
      <BudgetAlertNotification alerts={[]} onDismiss={vi.fn()} />
    );
    expect(container.innerHTML).toBe('');
  });

  it('renders a 50% info alert with correct styling and content', () => {
    const alert = createAlert({ severity: 'info', threshold: 50 });
    render(<BudgetAlertNotification alerts={[alert]} onDismiss={vi.fn()} />);

    expect(screen.getByText('Info: Budget 50%')).toBeInTheDocument();
    expect(screen.getByText('You have spent 50% of your monthly budget')).toBeInTheDocument();
    expect(screen.getByText('$250.00 of $500.00 spent')).toBeInTheDocument();

    const alertElement = screen.getByTestId('budget-alert-info');
    expect(alertElement).toBeInTheDocument();
    expect(alertElement.className).toContain('bg-blue-50');
    expect(alertElement.className).toContain('border-blue-300');
  });

  it('renders an 80% warning alert with correct styling', () => {
    const alert = createAlert({
      id: 'warn-1',
      threshold: 80,
      severity: 'warning',
      currentSpending: 400,
      message: 'You have spent 80% of your monthly budget',
    });
    render(<BudgetAlertNotification alerts={[alert]} onDismiss={vi.fn()} />);

    expect(screen.getByText('Warning: Budget 80%')).toBeInTheDocument();
    expect(screen.getByText('You have spent 80% of your monthly budget')).toBeInTheDocument();

    const alertElement = screen.getByTestId('budget-alert-warning');
    expect(alertElement.className).toContain('bg-yellow-50');
    expect(alertElement.className).toContain('border-yellow-300');
  });

  it('renders a 100% danger alert with correct styling', () => {
    const alert = createAlert({
      id: 'danger-1',
      threshold: 100,
      severity: 'danger',
      currentSpending: 500,
      message: 'You have spent 100% of your monthly budget',
    });
    render(<BudgetAlertNotification alerts={[alert]} onDismiss={vi.fn()} />);

    expect(screen.getByText('Alert: Budget 100%')).toBeInTheDocument();
    expect(screen.getByText('You have spent 100% of your monthly budget')).toBeInTheDocument();

    const alertElement = screen.getByTestId('budget-alert-danger');
    expect(alertElement.className).toContain('bg-red-50');
    expect(alertElement.className).toContain('border-red-300');
  });

  it('renders multiple alerts simultaneously', () => {
    const alerts = [
      createAlert({ id: 'a1', severity: 'info', threshold: 50 }),
      createAlert({ id: 'a2', severity: 'warning', threshold: 80 }),
      createAlert({ id: 'a3', severity: 'danger', threshold: 100 }),
    ];
    render(<BudgetAlertNotification alerts={alerts} onDismiss={vi.fn()} />);

    expect(screen.getByTestId('budget-alert-info')).toBeInTheDocument();
    expect(screen.getByTestId('budget-alert-warning')).toBeInTheDocument();
    expect(screen.getByTestId('budget-alert-danger')).toBeInTheDocument();
  });

  it('calls onDismiss with the correct alert id when dismiss button is clicked', async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();
    const alert = createAlert({ id: 'dismiss-test' });

    render(<BudgetAlertNotification alerts={[alert]} onDismiss={onDismiss} />);

    const dismissButton = screen.getByLabelText('Dismiss info alert');
    await user.click(dismissButton);

    expect(onDismiss).toHaveBeenCalledTimes(1);
    expect(onDismiss).toHaveBeenCalledWith('dismiss-test');
  });

  it('has accessible role="alert" on each toast', () => {
    const alert = createAlert();
    render(<BudgetAlertNotification alerts={[alert]} onDismiss={vi.fn()} />);

    const alerts = screen.getAllByRole('alert');
    expect(alerts).toHaveLength(1);
  });

  it('displays spending amounts formatted to 2 decimal places', () => {
    const alert = createAlert({
      currentSpending: 123.5,
      budgetAmount: 1000,
    });
    render(<BudgetAlertNotification alerts={[alert]} onDismiss={vi.fn()} />);

    expect(screen.getByText('$123.50 of $1000.00 spent')).toBeInTheDocument();
  });
});
