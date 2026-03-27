package com.expensetracker.websocket;

import com.expensetracker.budget.BudgetService;
import com.expensetracker.budget.dto.BudgetSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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

    public void checkAndSendAlerts(Long userId, int year, int month) {
        BudgetSummaryResponse summary = budgetService.getSummary(userId, year, month);
        if (!summary.budgetSet()) {
            return;
        }

        double usagePercent = summary.usagePercent() != null ? summary.usagePercent() : 0.0;
        YearMonth yearMonth = YearMonth.of(year, month);

        for (int threshold : THRESHOLDS) {
            if (usagePercent >= threshold) {
                if (thresholdTracker.shouldFire(userId, yearMonth, threshold)) {
                    BudgetAlertMessage alert = new BudgetAlertMessage(
                        threshold,
                        summary.totalSpent(),
                        summary.budget(),
                        threshold == 100
                            ? "You have reached 100% of your budget!"
                            : "You have used " + threshold + "% of your budget."
                    );
                    log.info("Sending budget alert to user {}: {}%", userId, threshold);
                    messagingTemplate.convertAndSendToUser(
                        userId.toString(),
                        ALERT_DESTINATION,
                        alert
                    );
                }
            }
        }
    }
}
