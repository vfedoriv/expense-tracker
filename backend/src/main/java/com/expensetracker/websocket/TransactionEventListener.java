package com.expensetracker.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Listens for transaction change events and triggers budget alert checks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final BudgetAlertService budgetAlertService;

    @Async
    @EventListener
    public void onTransactionChanged(TransactionChangedEvent event) {
        LocalDate now = LocalDate.now();
        log.debug("Transaction changed for user {}, checking budget alerts", event.userId());
        budgetAlertService.checkAndSendAlerts(event.userId(), now.getYear(), now.getMonthValue());
    }
}
