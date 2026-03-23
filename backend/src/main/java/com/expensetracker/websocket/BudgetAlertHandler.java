package com.expensetracker.websocket;

import com.expensetracker.config.UserPrincipal;
import com.expensetracker.service.BudgetAlertService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * Listens for STOMP SUBSCRIBE events. When a client subscribes to
 * /user/topic/budget-alerts, sends initial alerts for any thresholds
 * already crossed in the current month.
 */
@Component
public class BudgetAlertHandler {

    private static final String BUDGET_ALERTS_DESTINATION = "/user/topic/budget-alerts";

    private final BudgetAlertService budgetAlertService;

    public BudgetAlertHandler(BudgetAlertService budgetAlertService) {
        this.budgetAlertService = budgetAlertService;
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        var accessor = org.springframework.messaging.simp.stomp.StompHeaderAccessor
            .wrap(event.getMessage());

        String destination = accessor.getDestination();
        if (destination == null || !destination.equals(BUDGET_ALERTS_DESTINATION)) {
            return;
        }

        var principal = event.getUser();
        if (principal instanceof UserPrincipal userPrincipal) {
            budgetAlertService.sendInitialAlerts(userPrincipal.getUserId());
        }
    }
}
