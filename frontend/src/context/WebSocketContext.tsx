import { createContext, useContext, useEffect, useState, useRef, useCallback, type ReactNode } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';
import { useAuth } from './AuthContext';
import type { BudgetAlert } from '../types';

interface WebSocketContextType {
  alerts: BudgetAlert[];
  dismissAlert: (threshold: number) => void;
  connected: boolean;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

export function WebSocketProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [alerts, setAlerts] = useState<BudgetAlert[]>([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  const dismissAlert = useCallback((threshold: number) => {
    setAlerts(prev => prev.filter(a => a.threshold !== threshold));
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: '/app/alerts/ack',
        body: JSON.stringify({ threshold }),
      });
    }
  }, []);

  useEffect(() => {
    if (!user) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/user/queue/budget-alerts', (message) => {
          const alert: BudgetAlert = JSON.parse(message.body);
          setAlerts(prev => {
            if (prev.some(a => a.threshold === alert.threshold)) return prev;
            return [...prev, alert];
          });
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [user]);

  return (
    <WebSocketContext.Provider value={{ alerts, dismissAlert, connected }}>
      {children}
    </WebSocketContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useWebSocket() {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocket must be used within a WebSocketProvider');
  }
  return context;
}
