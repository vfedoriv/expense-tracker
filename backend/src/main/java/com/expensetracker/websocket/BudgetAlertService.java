package com.expensetracker.websocket;

import com.expensetracker.budget.BudgetService;
import com.expensetracker.budget.dto.BudgetSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertService {

    private static final List<Integer> THRESHOLDS = List.of(50, 80, 100);
    private static final String ALERT_DESTINATION = "/topic/budget-alerts";

    private final BudgetService budgetService;
    private final BudgetThresholdTracker thresholdTracker;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Called on WebSocket subscribe: sends the highest currently crossed threshold
     * and marks all crossed thresholds as fired so transaction changes don't re-send them.
     */
    public void sendCurrentStatus(Long userId, int year, int month) {
        BudgetSummaryResponse summary = budgetService.getSummary(userId, year, month);
        if (!summary.budgetSet()) {
            log.info("[ALERT] User {}: no budget set for {}-{}, skipping", userId, year, month);
            return;
        }

        double usagePercent = summary.usagePercent() != null ? summary.usagePercent() : 0.0;
        YearMonth yearMonth = YearMonth.of(year, month);
        log.info("[ALERT] User {} subscribe: usage={}% for {}", userId, String.format("%.1f", usagePercent), yearMonth);

        // Find the highest crossed threshold to send as current status
        int highestCrossed = -1;
        for (int threshold : THRESHOLDS) {
            if (usagePercent >= threshold) {
                highestCrossed = threshold;
                thresholdTracker.markFired(userId, yearMonth, threshold);
            }
        }

        if (highestCrossed > 0) {
            BudgetAlertMessage alert = new BudgetAlertMessage(
                highestCrossed,
                summary.totalSpent(),
                summary.budget(),
                formatMessage(highestCrossed, yearMonth)
            );
            log.info("[ALERT] Sending subscribe status to user {}: {}% threshold (spent={}, budget={})",
                userId, highestCrossed, summary.totalSpent(), summary.budget());
            messagingTemplate.convertAndSendToUser(
                userId.toString(), ALERT_DESTINATION, alert);
        }
    }

    /**
     * Called on transaction changes: resets thresholds above current usage
     * (so re-crossing fires again) and sends only newly crossed thresholds.
     */
    public void checkAndSendAlerts(Long userId, int year, int month) {
        BudgetSummaryResponse summary = budgetService.getSummary(userId, year, month);
        if (!summary.budgetSet()) {
            log.info("[ALERT] User {}: no budget set for {}-{}, skipping", userId, year, month);
            return;
        }

        double usagePercent = summary.usagePercent() != null ? summary.usagePercent() : 0.0;
        YearMonth yearMonth = YearMonth.of(year, month);
        log.info("[ALERT] User {} transaction change: usage={}% for {}", userId, String.format("%.1f", usagePercent), yearMonth);

        // Reset thresholds that are no longer crossed (usage decreased)
        thresholdTracker.resetAbove(userId, yearMonth, usagePercent);

        for (int threshold : THRESHOLDS) {
            if (usagePercent >= threshold) {
                if (thresholdTracker.shouldFire(userId, yearMonth, threshold)) {
                    BudgetAlertMessage alert = new BudgetAlertMessage(
                        threshold,
                        summary.totalSpent(),
                        summary.budget(),
                        formatMessage(threshold, yearMonth)
                    );
                    log.info("[ALERT] Sending threshold alert to user {}: {}% (spent={}, budget={})",
                        userId, threshold, summary.totalSpent(), summary.budget());
                    messagingTemplate.convertAndSendToUser(
                        userId.toString(), ALERT_DESTINATION, alert);
                }
            }
        }
    }

    private String formatMessage(int threshold, YearMonth yearMonth) {
        String monthLabel = yearMonth.getMonth().name().charAt(0)
            + yearMonth.getMonth().name().substring(1).toLowerCase()
            + " " + yearMonth.getYear();
        if (threshold == 100) {
            return "You have reached 100% of your budget for " + monthLabel + "!";
        }
        return "You have used " + threshold + "% of your budget for " + monthLabel + ".";
    }
}
