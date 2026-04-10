package io.github.vfedoriv.expensetracker.alert;

import io.github.vfedoriv.expensetracker.budget.MonthlyBudget;
import io.github.vfedoriv.expensetracker.budget.MonthlyBudgetRepository;
import io.github.vfedoriv.expensetracker.transaction.TransactionChangedEvent;
import io.github.vfedoriv.expensetracker.transaction.TransactionRepository;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core budget-alert logic.
 * <p>
 * Calculates whether the authenticated user's spending for the current
 * calendar month has crossed any of the configured thresholds (50 %, 80 %,
 * 100 %) and, if so, persists an {@link BudgetAlertLog} record and pushes
 * a STOMP message to the user's personal queue.
 * <p>
 * Each threshold fires <b>at most once per month</b>; duplicate alerts are
 * prevented by checking {@link BudgetAlertLogRepository}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertService {

    private static final List<Integer> THRESHOLDS = List.of(50, 80, 100);

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetAlertLogRepository budgetAlertLogRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserRepository userRepository;

    /**
     * Evaluate the current month's spending against the user's budget and
     * send STOMP alerts for any newly-crossed thresholds.
     *
     * @param userId database ID of the user
     */
    @Transactional
    public void checkAndSendAlerts(Long userId) {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        Optional<MonthlyBudget> budgetOpt =
                monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, year, month);

        if (budgetOpt.isEmpty()) {
            log.debug("No budget set for user {} in {}-{}, skipping alerts", userId, year, month);
            return;
        }

        BigDecimal budgetAmount = budgetOpt.get().getAmount();

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        BigDecimal spent = transactionRepository.sumByUserAndDateRange(userId, firstDay, lastDay);

        BigDecimal percentage = spent.multiply(BigDecimal.valueOf(100))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        for (int threshold : THRESHOLDS) {
            if (percentage.compareTo(BigDecimal.valueOf(threshold)) >= 0
                    && !budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    userId, year, month, threshold)) {

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalStateException(
                                "User not found: " + userId));

                BudgetAlertLog alertLog = BudgetAlertLog.builder()
                        .user(user)
                        .year(year)
                        .month(month)
                        .threshold(threshold)
                        .acknowledged(false)
                        .build();

                budgetAlertLogRepository.save(alertLog);

                Map<String, Object> alertMessage = Map.of(
                        "type", "BUDGET_ALERT",
                        "threshold", threshold,
                        "budgetAmount", budgetAmount,
                        "spent", spent,
                        "percentage", percentage,
                        "year", year,
                        "month", month,
                        "message", String.format(
                                "You have reached %d%% of your monthly budget (spent %s of %s)",
                                threshold, spent.toPlainString(), budgetAmount.toPlainString())
                );

                simpMessagingTemplate.convertAndSendToUser(
                        userId.toString(),
                        "/queue/budget-alerts",
                        alertMessage
                );

                log.info("Budget alert sent: user={}, threshold={}%, spent={}, budget={}",
                        userId, threshold, spent, budgetAmount);
            }
        }
    }

    /**
     * Mark a specific threshold alert as acknowledged for the current month.
     *
     * @param userId    database ID of the user
     * @param threshold the threshold value (50, 80, or 100)
     */
    @Transactional
    public void acknowledgeAlert(Long userId, int threshold) {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        budgetAlertLogRepository
                .findByUserIdAndYearAndMonthAndThreshold(userId, year, month, threshold)
                .ifPresent(alertLog -> {
                    alertLog.setAcknowledged(true);
                    budgetAlertLogRepository.save(alertLog);
                    log.info("Alert acknowledged: user={}, threshold={}%, {}-{}",
                            userId, threshold, year, month);
                });
    }

    /**
     * Listens for {@link TransactionChangedEvent} published after any
     * transaction create/update/delete and re-evaluates budget alerts
     * for the affected user.
     */
    @EventListener
    public void onTransactionChanged(TransactionChangedEvent event) {
        checkAndSendAlerts(event.userId());
    }
}
