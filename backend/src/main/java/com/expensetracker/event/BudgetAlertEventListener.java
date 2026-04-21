package com.expensetracker.event;

import com.expensetracker.dto.response.BudgetAlertMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Listens for BudgetAlertEvent and sends WebSocket messages AFTER the
 * database transaction commits. This prevents phantom alerts when a
 * transaction rollback occurs and avoids race conditions where the
 * WebSocket message arrives before the HTTP response.
 */
@Component
public class BudgetAlertEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    public BudgetAlertEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBudgetAlertEvent(BudgetAlertEvent event) {
        for (BudgetAlertMessage alert : event.alerts()) {
            messagingTemplate.convertAndSendToUser(
                String.valueOf(event.userId()),
                "/topic/budget-alerts",
                alert
            );
        }
    }
}
