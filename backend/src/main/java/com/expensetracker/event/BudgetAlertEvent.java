package com.expensetracker.event;

import com.expensetracker.dto.response.BudgetAlertMessage;
import java.util.List;

/**
 * Application event published when budget alerts need to be sent via WebSocket. Used
 * with @TransactionalEventListener to ensure alerts are only sent after the database transaction
 * commits successfully.
 */
public record BudgetAlertEvent(Long userId, List<BudgetAlertMessage> alerts) {}
