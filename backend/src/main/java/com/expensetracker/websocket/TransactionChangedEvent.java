package com.expensetracker.websocket;

/**
 * Application event published when a transaction is created, updated, or deleted.
 */
public record TransactionChangedEvent(Long userId) {}
