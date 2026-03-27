package com.expensetracker.websocket;

import com.expensetracker.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.YearMonth;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertController {

    private final BudgetAlertService budgetAlertService;

    /**
     * Handles client subscribe message.
     * Client sends: { "type": "subscribe", "month": "2026-03" }
     * Server then checks and pushes budget alerts for the specified month.
     */
    @MessageMapping("/budget-alerts/subscribe")
    public void subscribe(
        @Payload BudgetSubscribeMessage message,
        Principal principal
    ) {
        UserPrincipal userPrincipal = extractUserPrincipal(principal);
        if (userPrincipal == null) {
            log.warn("No authenticated user found for budget alert subscription");
            return;
        }
        log.info("User {} subscribed to budget alerts for month {}", userPrincipal.getId(), message.month());
        if (message.month() == null || message.month().isBlank()) {
            return;
        }
        try {
            YearMonth yearMonth = YearMonth.parse(message.month());
            budgetAlertService.sendCurrentStatus(userPrincipal.getId(), yearMonth.getYear(), yearMonth.getMonthValue());
        } catch (Exception e) {
            log.warn("Invalid month format in subscribe message: {}", message.month());
        }
    }

    private UserPrincipal extractUserPrincipal(Principal principal) {
        if (principal instanceof AbstractAuthenticationToken auth
            && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        return null;
    }
}
