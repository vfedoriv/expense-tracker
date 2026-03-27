import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { BudgetAlertMessage } from '../types'
import { FAKE_AUTH, getStoredEmail } from '../contexts/AuthContext'

export function useBudgetAlerts(
  yearMonth: string,
  onAlert: (alert: BudgetAlertMessage) => void
) {
  const clientRef = useRef<Client | null>(null)
  const onAlertRef = useRef(onAlert)
  onAlertRef.current = onAlert

  useEffect(() => {
    const connectHeaders: Record<string, string> = {}
    if (FAKE_AUTH) {
      connectHeaders['X-User-Email'] = getStoredEmail()
    }

    console.log('[WS] Connecting to /ws for budget alerts, month:', yearMonth)

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('[WS] Connected. Subscribing to /user/topic/budget-alerts')
        client.subscribe('/user/topic/budget-alerts', (msg) => {
          try {
            const alert = JSON.parse(msg.body) as BudgetAlertMessage
            console.log('[WS] Received budget alert:', alert.threshold + '%', alert.message)
            onAlertRef.current(alert)
          } catch (e) {
            console.warn('[WS] Failed to parse budget alert message:', e)
          }
        })

        console.log('[WS] Sending subscribe message for month:', yearMonth)
        client.publish({
          destination: '/app/budget-alerts/subscribe',
          body: JSON.stringify({ month: yearMonth }),
        })
      },
      onDisconnect: () => {
        console.log('[WS] Disconnected')
      },
      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message'], frame.body)
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      console.log('[WS] Deactivating client')
      client.deactivate()
    }
  }, [yearMonth])
}
