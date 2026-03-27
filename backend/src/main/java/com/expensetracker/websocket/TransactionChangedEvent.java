package com.expensetracker.websocket;

import java.time.LocalDate;
import java.util.Set;

/**
 * Application event published when a transaction is created, updated, or deleted.
 * Contains the affected month(s) so budget alerts check the correct period.
 */
public record TransactionChangedEvent(Long userId, Set<LocalDate> affectedDates) {

    public TransactionChangedEvent(Long userId, LocalDate date) {
        this(userId, Set.of(date));
    }

    public TransactionChangedEvent(Long userId, LocalDate date1, LocalDate date2) {
        this(userId, date1.equals(date2) ? Set.of(date1) : Set.of(date1, date2));
    }
}
