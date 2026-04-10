package io.github.vfedoriv.expensetracker.transaction;

/**
 * Spring application event published after a transaction is created, updated, or deleted.
 * Used by the budget alert system to recalculate thresholds.
 */
public record TransactionChangedEvent(Long userId) {}
