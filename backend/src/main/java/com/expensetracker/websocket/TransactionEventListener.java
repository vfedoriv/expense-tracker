package com.expensetracker.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Listens for transaction change events and triggers budget alert checks.
 * Uses @TransactionalEventListener to ensure the transaction is committed
 * before reading budget data, so calculations include the latest changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final BudgetAlertService budgetAlertService;

    @Async
    @TransactionalEventListener
    public void onTransactionChanged(TransactionChangedEvent event) {
        Set<YearMonth> months = event.affectedDates().stream()
            .map(YearMonth::from)
            .collect(Collectors.toSet());
        log.info("[ALERT] Transaction changed for user {}, checking budget alerts for months: {}", event.userId(), months);
        for (YearMonth ym : months) {
            budgetAlertService.checkAndSendAlerts(event.userId(), ym.getYear(), ym.getMonthValue());
        }
    }
}
