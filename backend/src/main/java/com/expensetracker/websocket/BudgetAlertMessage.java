package com.expensetracker.websocket;

import java.math.BigDecimal;

/**
 * Server-to-client WebSocket alert message.
 * Format: { "threshold": 50|80|100, "spent": ..., "budget": ..., "message": "..." }
 */
public record BudgetAlertMessage(
    int threshold,
    BigDecimal spent,
    BigDecimal budget,
    String message
) {}
