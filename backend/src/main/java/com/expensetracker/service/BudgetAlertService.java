package com.expensetracker.service;

import com.expensetracker.dto.response.BudgetAlertMessage;
import com.expensetracker.entity.BudgetAlertState;
import com.expensetracker.entity.MonthlyBudget;
import com.expensetracker.entity.User;
import com.expensetracker.event.BudgetAlertEvent;
import com.expensetracker.repository.BudgetAlertStateRepository;
import com.expensetracker.repository.MonthlyBudgetRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetAlertService {

    private static final int[] THRESHOLDS = {50, 80, 100};

    private final BudgetAlertStateRepository budgetAlertStateRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public BudgetAlertService(
            BudgetAlertStateRepository budgetAlertStateRepository,
            MonthlyBudgetRepository monthlyBudgetRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            ApplicationEventPublisher eventPublisher) {
        this.budgetAlertStateRepository = budgetAlertStateRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Evaluates budget alerts for a user for the month of the given transaction date. Called after
     * any transaction create/update/delete. Publishes a BudgetAlertEvent so that WebSocket messages
     * are sent only AFTER the database transaction commits (via @TransactionalEventListener).
     */
    @Transactional
    public void evaluateAlerts(Long userId, LocalDate transactionDate) {
        YearMonth month = YearMonth.from(transactionDate);
        List<BudgetAlertMessage> alerts = calculateAndFireAlerts(userId, month);
        if (!alerts.isEmpty()) {
            eventPublisher.publishEvent(new BudgetAlertEvent(userId, alerts));
        }
    }

    /**
     * Checks if any thresholds are already crossed for ALL months with active budgets and sends
     * those alerts immediately. Called when a client subscribes via WebSocket. Uses a read-only
     * path that does NOT check or modify fired flags.
     */
    @Transactional(readOnly = true)
    public void sendInitialAlerts(Long userId) {
        List<MonthlyBudget> budgets = monthlyBudgetRepository.findAllByUserId(userId);
        List<BudgetAlertMessage> allAlerts = new ArrayList<>();
        for (MonthlyBudget budget : budgets) {
            YearMonth ym = YearMonth.of(budget.getYear(), budget.getMonth());
            allAlerts.addAll(getCurrentlyCrossedAlerts(userId, ym));
        }
        sendAlerts(userId, allAlerts);
    }

    /**
     * Generates alert messages for ALL currently crossed thresholds WITHOUT checking or modifying
     * BudgetAlertState fired flags. Used for WebSocket reconnection to re-send previously fired
     * alerts.
     */
    @Transactional(readOnly = true)
    public List<BudgetAlertMessage> getCurrentlyCrossedAlerts(Long userId, YearMonth yearMonth) {
        List<BudgetAlertMessage> alerts = new ArrayList<>();

        Short year = (short) yearMonth.getYear();
        Short month = (short) yearMonth.getMonthValue();

        Optional<MonthlyBudget> budgetOpt =
                monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, year, month);
        if (budgetOpt.isEmpty()) {
            return alerts;
        }

        MonthlyBudget budget = budgetOpt.get();
        BigDecimal budgetAmount = budget.getAmount();

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        BigDecimal totalSpent =
                transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate);

        BigDecimal percentage = BigDecimal.ZERO;
        if (budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
            percentage =
                    totalSpent
                            .multiply(BigDecimal.valueOf(100))
                            .divide(budgetAmount, 2, RoundingMode.HALF_UP);
        }

        String yearMonthStr =
                String.format("%04d-%02d", yearMonth.getYear(), yearMonth.getMonthValue());

        for (int threshold : THRESHOLDS) {
            if (percentage.compareTo(BigDecimal.valueOf(threshold)) >= 0) {
                alerts.add(
                        BudgetAlertMessage.of(threshold, totalSpent, budgetAmount, yearMonthStr));
            }
        }

        return alerts;
    }

    /**
     * Resets the BudgetAlertState for a given user and month, clearing all fired flags. Called when
     * a budget amount changes to allow re-evaluation from scratch.
     */
    @Transactional
    public void resetAlertState(Long userId, Short year, Short month) {
        Optional<BudgetAlertState> stateOpt =
                budgetAlertStateRepository.findByUserIdAndYearAndMonth(userId, year, month);
        stateOpt.ifPresent(
                state -> {
                    state.setThreshold50Fired(false);
                    state.setThreshold80Fired(false);
                    state.setThreshold100Fired(false);
                    budgetAlertStateRepository.save(state);
                });
    }

    /**
     * Core threshold logic: calculates spending percentage and fires alerts for any newly crossed
     * thresholds. Returns the list of alert messages to send.
     */
    @Transactional
    public List<BudgetAlertMessage> calculateAndFireAlerts(Long userId, YearMonth yearMonth) {
        List<BudgetAlertMessage> alerts = new ArrayList<>();

        Short year = (short) yearMonth.getYear();
        Short month = (short) yearMonth.getMonthValue();

        // No budget means no alerts
        Optional<MonthlyBudget> budgetOpt =
                monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, year, month);
        if (budgetOpt.isEmpty()) {
            return alerts;
        }

        MonthlyBudget budget = budgetOpt.get();
        BigDecimal budgetAmount = budget.getAmount();

        // Calculate total spending for the month
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        BigDecimal totalSpent =
                transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate);

        // Calculate percentage
        BigDecimal percentage = BigDecimal.ZERO;
        if (budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
            percentage =
                    totalSpent
                            .multiply(BigDecimal.valueOf(100))
                            .divide(budgetAmount, 2, RoundingMode.HALF_UP);
        }

        // Get or create alert state
        BudgetAlertState alertState =
                budgetAlertStateRepository
                        .findByUserIdAndYearAndMonth(userId, year, month)
                        .orElseGet(
                                () -> {
                                    User user = userRepository.findById(userId).orElseThrow();
                                    BudgetAlertState newState = new BudgetAlertState();
                                    newState.setUser(user);
                                    newState.setYear(year);
                                    newState.setMonth(month);
                                    return budgetAlertStateRepository.save(newState);
                                });

        String yearMonthStr =
                String.format("%04d-%02d", yearMonth.getYear(), yearMonth.getMonthValue());

        // Check each threshold
        for (int threshold : THRESHOLDS) {
            if (percentage.compareTo(BigDecimal.valueOf(threshold)) >= 0
                    && !isThresholdFired(alertState, threshold)) {
                setThresholdFired(alertState, threshold);
                alerts.add(
                        BudgetAlertMessage.of(threshold, totalSpent, budgetAmount, yearMonthStr));
            }
        }

        if (!alerts.isEmpty()) {
            budgetAlertStateRepository.save(alertState);
        }

        return alerts;
    }

    private void sendAlerts(Long userId, List<BudgetAlertMessage> alerts) {
        for (BudgetAlertMessage alert : alerts) {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId), "/topic/budget-alerts", alert);
        }
    }

    private boolean isThresholdFired(BudgetAlertState state, int threshold) {
        return switch (threshold) {
            case 50 -> Boolean.TRUE.equals(state.getThreshold50Fired());
            case 80 -> Boolean.TRUE.equals(state.getThreshold80Fired());
            case 100 -> Boolean.TRUE.equals(state.getThreshold100Fired());
            default -> false;
        };
    }

    private void setThresholdFired(BudgetAlertState state, int threshold) {
        switch (threshold) {
            case 50 -> state.setThreshold50Fired(true);
            case 80 -> state.setThreshold80Fired(true);
            case 100 -> state.setThreshold100Fired(true);
        }
    }
}
