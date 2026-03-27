package com.expensetracker.websocket;

/**
 * Client-to-server WebSocket message.
 * Client sends this to subscribe to budget alerts for a specific month.
 * Format: { "type": "subscribe", "month": "2026-03" }
 */
public record BudgetSubscribeMessage(
    String type,
    String month
) {}
