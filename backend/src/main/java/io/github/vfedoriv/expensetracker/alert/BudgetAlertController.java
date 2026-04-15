package io.github.vfedoriv.expensetracker.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket (STOMP) controller for budget alerts.
 * <p>
 * Handles two interactions:
 * <ul>
 *   <li>{@code /queue/budget-alerts} subscription — triggers an
 *       immediate alert check so the client receives any outstanding
 *       alerts on connect.</li>
 *   <li>{@code /app/alerts/ack} message — allows the client to
 *       acknowledge a specific threshold alert.</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
public class BudgetAlertController {

    private final BudgetAlertService budgetAlertService;

    /**
     * Fired when a client subscribes to their personal budget-alert queue.
     * Immediately checks whether any unacknowledged thresholds have been
     * crossed and pushes them to the new subscriber.
     */
    @SubscribeMapping("/queue/budget-alerts")
    public void onSubscribe(Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        budgetAlertService.checkAndSendAlerts(userId);
    }

    /**
     * Receives an acknowledgement message from the client.
     * Expected payload: {@code {"threshold": 50}} (or 80, 100).
     */
    @MessageMapping("/alerts/ack")
    public void acknowledgeAlert(Map<String, Object> payload, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        int threshold = ((Number) payload.get("threshold")).intValue();
        budgetAlertService.acknowledgeAlert(userId, threshold);
    }
}
