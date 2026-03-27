import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { BudgetAlertMessage } from '../types'
import { getStoredEmail } from '../contexts/AuthContext'

export function useBudgetAlerts(
  yearMonth: string,
  onAlert: (alert: BudgetAlertMessage) => void
) {
  const clientRef = useRef<Client | null>(null)
  const onAlertRef = useRef(onAlert)
  onAlertRef.current = onAlert

  useEffect(() => {
    const email = getStoredEmail()
    if (!email) return

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {
        'X-User-Email': email,
      },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/user/topic/budget-alerts', (msg) => {
          try {
            const alert = JSON.parse(msg.body) as BudgetAlertMessage
            onAlertRef.current(alert)
          } catch {
            // ignore malformed messages
          }
        })

        // Send subscribe message to request current month alert check
        client.publish({
          destination: '/app/budget-alerts/subscribe',
          body: JSON.stringify({ month: yearMonth }),
        })
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [yearMonth])
}
