import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from './useAuth';
import type { BudgetAlertMessage, BudgetAlertToast } from '../types';

const TOAST_DISMISS_MS = 8000;
const RECONNECT_DELAY_MS = 5000;

function getSeverity(threshold: number): 'info' | 'warning' | 'danger' {
  if (threshold >= 100) return 'danger';
  if (threshold >= 80) return 'warning';
  return 'info';
}

function buildToastMessage(threshold: number): string {
  if (threshold >= 100) {
    return `You have spent 100% of your monthly budget`;
  }
  return `You have spent ${threshold}% of your monthly budget`;
}

export function useWebSocket() {
  const { isAuthenticated } = useAuth();
  const [alerts, setAlerts] = useState<BudgetAlertToast[]>([]);
  const clientRef = useRef<Client | null>(null);
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const dismissAlert = useCallback((id: string) => {
    setAlerts((prev) => prev.filter((a) => a.id !== id));
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
  }, []);

  const addAlert = useCallback((msg: BudgetAlertMessage) => {
    const id = `${msg.yearMonth}-${msg.threshold}-${Date.now()}`;
    const toast: BudgetAlertToast = {
      id,
      threshold: msg.threshold,
      currentSpending: msg.currentSpending,
      budgetAmount: msg.budgetAmount,
      yearMonth: msg.yearMonth,
      severity: getSeverity(msg.threshold),
      message: buildToastMessage(msg.threshold),
    };

    setAlerts((prev) => [...prev, toast]);

    const timer = setTimeout(() => {
      setAlerts((prev) => prev.filter((a) => a.id !== id));
      timersRef.current.delete(id);
    }, TOAST_DISMISS_MS);
    timersRef.current.set(id, timer);
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    // In SSO mode, the session cookie is sent automatically.
    // The /ws endpoint uses the authenticated session for user identification.
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        client.subscribe('/user/topic/budget-alerts', (message) => {
          try {
            const alertMsg = JSON.parse(message.body) as BudgetAlertMessage;
            addAlert(alertMsg);
          } catch {
            // Ignore malformed messages
          }
        });
      },
      onStompError: (frame) => {
        console.error('WebSocket STOMP error:', frame.headers['message']);
      },
    });

    client.activate();
    clientRef.current = client;

    const currentTimers = timersRef.current;

    return () => {
      if (client.active) {
        client.deactivate();
      }
      clientRef.current = null;
      // Clean up all timers
      currentTimers.forEach((timer) => clearTimeout(timer));
      currentTimers.clear();
    };
  }, [isAuthenticated, addAlert]);

  return { alerts, dismissAlert };
}
