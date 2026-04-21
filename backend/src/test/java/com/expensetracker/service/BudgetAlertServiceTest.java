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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetAlertServiceTest {

    @Mock
    private BudgetAlertStateRepository budgetAlertStateRepository;

    @Mock
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BudgetAlertService budgetAlertService;

    @Captor
    private ArgumentCaptor<BudgetAlertMessage> alertCaptor;

    @Captor
    private ArgumentCaptor<BudgetAlertEvent> eventCaptor;

    private User testUser;
    private YearMonth testYearMonth;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setProvider("fake");
        testUser.setProviderUserId("fake-user-1");
        testUser.setEmail("admin@test.com");
        testUser.setDisplayName("Test User");

        testYearMonth = YearMonth.of(2026, 3);
    }

    @Test
    void calculateAndFireAlerts_noBudget_returnsEmpty() {
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.empty());

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).isEmpty();
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void calculateAndFireAlerts_below50Percent_noAlerts() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("400.00")); // 40%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).isEmpty();
    }

    @Test
    void calculateAndFireAlerts_crosses50Percent_fires50Alert() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("500.00")); // exactly 50%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).threshold()).isEqualTo(50);
        assertThat(alerts.get(0).type()).isEqualTo("BUDGET_ALERT");
        assertThat(alerts.get(0).currentSpending()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(alerts.get(0).budgetAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(alerts.get(0).yearMonth()).isEqualTo("2026-03");
        assertThat(alertState.getThreshold50Fired()).isTrue();
        verify(budgetAlertStateRepository).save(alertState);
    }

    @Test
    void calculateAndFireAlerts_crosses80Percent_fires50And80Alerts() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("850.00")); // 85%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).threshold()).isEqualTo(50);
        assertThat(alerts.get(1).threshold()).isEqualTo(80);
        assertThat(alertState.getThreshold50Fired()).isTrue();
        assertThat(alertState.getThreshold80Fired()).isTrue();
    }

    @Test
    void calculateAndFireAlerts_crosses100Percent_firesAllThreeAlerts() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("1100.00")); // 110%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(3);
        assertThat(alerts.get(0).threshold()).isEqualTo(50);
        assertThat(alerts.get(1).threshold()).isEqualTo(80);
        assertThat(alerts.get(2).threshold()).isEqualTo(100);
        assertThat(alertState.getThreshold50Fired()).isTrue();
        assertThat(alertState.getThreshold80Fired()).isTrue();
        assertThat(alertState.getThreshold100Fired()).isTrue();
    }

    @Test
    void calculateAndFireAlerts_50AlreadyFired_onlyFires80() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(true, false, false); // 50 already fired

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("850.00")); // 85%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).threshold()).isEqualTo(80);
    }

    @Test
    void calculateAndFireAlerts_allAlreadyFired_noAlerts() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(true, true, true); // all fired

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("1100.00")); // 110%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).isEmpty();
    }

    @Test
    void evaluateAlerts_publishesEventInsteadOfSendingDirectly() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("500.00")); // 50%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        budgetAlertService.evaluateAlerts(1L, LocalDate.of(2026, 3, 15));

        // evaluateAlerts now publishes an event instead of sending directly via messagingTemplate
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        BudgetAlertEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.alerts()).hasSize(1);

        BudgetAlertMessage alert = event.alerts().get(0);
        assertThat(alert.type()).isEqualTo("BUDGET_ALERT");
        assertThat(alert.threshold()).isEqualTo(50);

        // Verify messagingTemplate is NOT called directly from evaluateAlerts
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void sendInitialAlerts_sendsAlertForPreCrossedThresholds() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));

        when(monthlyBudgetRepository.findAllByUserId(1L))
            .thenReturn(List.of(budget));
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("900.00")); // 90% - crosses 50 and 80

        budgetAlertService.sendInitialAlerts(1L);

        verify(messagingTemplate, times(2)).convertAndSendToUser(
            eq("1"),
            eq("/topic/budget-alerts"),
            any(BudgetAlertMessage.class)
        );
    }

    @Test
    void calculateAndFireAlerts_noAlertState_createsNew() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("600.00")); // 60% - crosses 50
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(budgetAlertStateRepository.save(any(BudgetAlertState.class)))
            .thenAnswer(invocation -> {
                BudgetAlertState state = invocation.getArgument(0);
                state.setId(1L);
                return state;
            });

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).threshold()).isEqualTo(50);
        // save called twice: once for creating new state, once for updating threshold
        verify(budgetAlertStateRepository, times(2)).save(any(BudgetAlertState.class));
    }

    @Test
    void calculateAndFireAlerts_exactlyAt50_firesAlert() {
        MonthlyBudget budget = createBudget(new BigDecimal("200.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("100.00")); // exactly 50%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).threshold()).isEqualTo(50);
    }

    @Test
    void calculateAndFireAlerts_zeroBudgetAmount_noAlerts() {
        MonthlyBudget budget = createBudget(BigDecimal.ZERO);
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("100.00"));
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).isEmpty();
    }

    // ==================== COMPREHENSIVE TESTS ====================

    /**
     * Test 1: evaluateAlerts uses transaction date, not current date.
     * Create budget for January, add January transaction crossing 50% -> verify 50% alert fires
     * even when current calendar month is NOT January.
     */
    @Test
    void evaluateAlerts_usesTransactionDateNotCurrentDate() {
        // Budget for January 2026
        MonthlyBudget janBudget = createBudgetForMonth(new BigDecimal("1000.00"), (short) 2026, (short) 1);
        BudgetAlertState janState = createAlertStateForMonth(false, false, false, (short) 2026, (short) 1);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 1))
            .thenReturn(Optional.of(janBudget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L),
            eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 31))))
            .thenReturn(new BigDecimal("500.00")); // 50%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 1))
            .thenReturn(Optional.of(janState));

        // Pass a January transaction date (current month might be March or any other month)
        budgetAlertService.evaluateAlerts(1L, LocalDate.of(2026, 1, 15));

        // evaluateAlerts publishes event (sent after commit by listener)
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        BudgetAlertEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.alerts()).hasSize(1);

        BudgetAlertMessage alert = event.alerts().get(0);
        assertThat(alert.threshold()).isEqualTo(50);
        assertThat(alert.yearMonth()).isEqualTo("2026-01");
        assertThat(janState.getThreshold50Fired()).isTrue();
    }

    /**
     * Test 2: Each threshold fires independently (50%, then 80%, then 100%).
     * Simulates adding $500 (50%), then $300 more (80%), then $200 more (100%).
     */
    @Test
    void eachThresholdFiresIndependently_inSequence() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        // Step 1: $500 spent (50%)
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("500.00"));

        List<BudgetAlertMessage> alerts1 = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);
        assertThat(alerts1).hasSize(1);
        assertThat(alerts1.get(0).threshold()).isEqualTo(50);
        assertThat(alertState.getThreshold50Fired()).isTrue();

        // Step 2: $800 spent (80%) - 50% already fired
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("800.00"));

        List<BudgetAlertMessage> alerts2 = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);
        assertThat(alerts2).hasSize(1);
        assertThat(alerts2.get(0).threshold()).isEqualTo(80);
        assertThat(alertState.getThreshold80Fired()).isTrue();

        // Step 3: $1000 spent (100%) - 50% and 80% already fired
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("1000.00"));

        List<BudgetAlertMessage> alerts3 = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);
        assertThat(alerts3).hasSize(1);
        assertThat(alerts3.get(0).threshold()).isEqualTo(100);
        assertThat(alertState.getThreshold100Fired()).isTrue();
    }

    /**
     * Test 3: sendInitialAlerts re-sends all crossed thresholds (even previously fired ones).
     * Fire 50% and 80% via evaluateAlerts, then call sendInitialAlerts -> verify it sends both again.
     */
    @Test
    void sendInitialAlerts_resendsPreviouslyFiredAlerts() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        // Alert state shows 50% and 80% already fired
        BudgetAlertState alertState = createAlertState(true, true, false);

        when(monthlyBudgetRepository.findAllByUserId(1L))
            .thenReturn(List.of(budget));
        // getCurrentlyCrossedAlerts reads budget
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        // spending at 85% - crosses 50 and 80 thresholds
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("850.00"));

        budgetAlertService.sendInitialAlerts(1L);

        // Should send BOTH 50% and 80% alerts again (getCurrentlyCrossedAlerts ignores fired flags)
        verify(messagingTemplate, times(2)).convertAndSendToUser(
            eq("1"),
            eq("/topic/budget-alerts"),
            alertCaptor.capture()
        );

        List<BudgetAlertMessage> sentAlerts = alertCaptor.getAllValues();
        assertThat(sentAlerts).extracting(BudgetAlertMessage::threshold).containsExactly(50, 80);

        // Verify fired flags were NOT modified (read-only)
        verify(budgetAlertStateRepository, never()).save(any(BudgetAlertState.class));
    }

    /**
     * Test 4: Budget creation triggers alert evaluation.
     * This test verifies the getCurrentlyCrossedAlerts method works correctly
     * when expenses already exist for a month without a budget.
     */
    @Test
    void getCurrentlyCrossedAlerts_returnsAlertsForExistingExpenses() {
        // Budget for March 2026 with $1000
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        // $600 already spent = 60% -> should show 50% alert
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L),
            eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
            .thenReturn(new BigDecimal("600.00"));

        List<BudgetAlertMessage> alerts = budgetAlertService.getCurrentlyCrossedAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).threshold()).isEqualTo(50);
        // getCurrentlyCrossedAlerts should NOT modify any state
        verify(budgetAlertStateRepository, never()).save(any());
        verify(budgetAlertStateRepository, never()).findByUserIdAndYearAndMonth(any(), any(), any());
    }

    /**
     * Test 5: Budget update re-evaluates alerts.
     * Verify resetAlertState clears fired flags.
     */
    @Test
    void resetAlertState_clearsAllFiredFlags() {
        BudgetAlertState alertState = createAlertState(true, true, true);

        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        budgetAlertService.resetAlertState(1L, (short) 2026, (short) 3);

        assertThat(alertState.getThreshold50Fired()).isFalse();
        assertThat(alertState.getThreshold80Fired()).isFalse();
        assertThat(alertState.getThreshold100Fired()).isFalse();
        verify(budgetAlertStateRepository).save(alertState);
    }

    /**
     * Test 5b: Budget update with increased amount - verify 100% alert fires after reset.
     * $800 spending against $1000 budget (80% fired). Update budget to $500 -> now 160% -> 100% fires.
     */
    @Test
    void budgetDecrease_triggersHigherThresholdAfterReset() {
        // After budget decrease to $500, spending is $800 = 160%
        MonthlyBudget budget = createBudget(new BigDecimal("500.00"));
        // State was reset, so all flags are false
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("800.00")); // 160%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(3);
        assertThat(alerts).extracting(BudgetAlertMessage::threshold).containsExactly(50, 80, 100);
    }

    /**
     * Test 5c: Budget increase - verify state is properly reset.
     * Budget increased to $2000 with $800 spending (40%) -> no thresholds crossed.
     */
    @Test
    void budgetIncrease_afterReset_noThresholdsCrossed() {
        MonthlyBudget budget = createBudget(new BigDecimal("2000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("800.00")); // 40%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        List<BudgetAlertMessage> alerts = budgetAlertService.calculateAndFireAlerts(1L, testYearMonth);

        assertThat(alerts).isEmpty();
        assertThat(alertState.getThreshold50Fired()).isFalse();
        assertThat(alertState.getThreshold80Fired()).isFalse();
        assertThat(alertState.getThreshold100Fired()).isFalse();
    }

    /**
     * Test 6: Cross-month isolation.
     * Create budgets for January and February. Add transactions only to January.
     * Only January alerts fire; February is unaffected.
     */
    @Test
    void crossMonthIsolation_onlyAffectedMonthFiresAlerts() {
        MonthlyBudget janBudget = createBudgetForMonth(new BigDecimal("1000.00"), (short) 2026, (short) 1);
        MonthlyBudget febBudget = createBudgetForMonth(new BigDecimal("1000.00"), (short) 2026, (short) 2);
        BudgetAlertState janState = createAlertStateForMonth(false, false, false, (short) 2026, (short) 1);
        BudgetAlertState febState = createAlertStateForMonth(false, false, false, (short) 2026, (short) 2);

        // January: budget exists, spending at 60%
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 1))
            .thenReturn(Optional.of(janBudget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(1L,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(new BigDecimal("600.00"));
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 1))
            .thenReturn(Optional.of(janState));

        // February: budget exists, spending at $0
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 2))
            .thenReturn(Optional.of(febBudget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(1L,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
            .thenReturn(BigDecimal.ZERO);
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 2))
            .thenReturn(Optional.of(febState));

        // Evaluate January
        List<BudgetAlertMessage> janAlerts = budgetAlertService.calculateAndFireAlerts(1L, YearMonth.of(2026, 1));
        assertThat(janAlerts).hasSize(1);
        assertThat(janAlerts.get(0).threshold()).isEqualTo(50);
        assertThat(janState.getThreshold50Fired()).isTrue();

        // Evaluate February
        List<BudgetAlertMessage> febAlerts = budgetAlertService.calculateAndFireAlerts(1L, YearMonth.of(2026, 2));
        assertThat(febAlerts).isEmpty();
        assertThat(febState.getThreshold50Fired()).isFalse();
    }

    /**
     * Test 7: Delete transaction re-evaluates for the transaction's month.
     * At 100% threshold, spending drops but already-fired flags remain
     * (the evaluateAlerts method only fires NEW thresholds, never unfires old ones).
     */
    @Test
    void evaluateAlerts_afterTransactionDelete_noNewAlertsWhenBelowPreviousThreshold() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        // All thresholds already fired (was at 100%)
        BudgetAlertState alertState = createAlertState(true, true, true);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        // After deletion, spending dropped to 70% (below 80% but above 50%)
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("700.00"));
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(alertState));

        // evaluateAlerts on delete
        budgetAlertService.evaluateAlerts(1L, LocalDate.of(2026, 3, 15));

        // No new alerts since all thresholds already fired and spending is below 80/100 now
        // but the fired flags stay (once-per-threshold-per-month rule)
        // No event published because no new alerts
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(alertState.getThreshold50Fired()).isTrue();
        assertThat(alertState.getThreshold80Fired()).isTrue();
        assertThat(alertState.getThreshold100Fired()).isTrue();
    }

    /**
     * Test: sendInitialAlerts with multiple months with active budgets.
     */
    @Test
    void sendInitialAlerts_checksAllMonthsWithActiveBudgets() {
        MonthlyBudget janBudget = createBudgetForMonth(new BigDecimal("1000.00"), (short) 2026, (short) 1);
        MonthlyBudget febBudget = createBudgetForMonth(new BigDecimal("500.00"), (short) 2026, (short) 2);

        when(monthlyBudgetRepository.findAllByUserId(1L))
            .thenReturn(List.of(janBudget, febBudget));

        // January: 60% -> crosses 50%
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 1))
            .thenReturn(Optional.of(janBudget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(1L,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(new BigDecimal("600.00"));

        // February: 90% -> crosses 50% and 80%
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 2))
            .thenReturn(Optional.of(febBudget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(1L,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
            .thenReturn(new BigDecimal("450.00"));

        budgetAlertService.sendInitialAlerts(1L);

        // Jan: 1 alert (50%), Feb: 2 alerts (50%, 80%) = 3 total
        verify(messagingTemplate, times(3)).convertAndSendToUser(
            eq("1"),
            eq("/topic/budget-alerts"),
            alertCaptor.capture()
        );

        List<BudgetAlertMessage> allAlerts = alertCaptor.getAllValues();
        assertThat(allAlerts).hasSize(3);
        // January 50%
        assertThat(allAlerts.get(0).threshold()).isEqualTo(50);
        assertThat(allAlerts.get(0).yearMonth()).isEqualTo("2026-01");
        // February 50% and 80%
        assertThat(allAlerts.get(1).threshold()).isEqualTo(50);
        assertThat(allAlerts.get(1).yearMonth()).isEqualTo("2026-02");
        assertThat(allAlerts.get(2).threshold()).isEqualTo(80);
        assertThat(allAlerts.get(2).yearMonth()).isEqualTo("2026-02");
    }

    /**
     * Test: sendInitialAlerts with no budgets sends no alerts.
     */
    @Test
    void sendInitialAlerts_noBudgets_sendsNoAlerts() {
        when(monthlyBudgetRepository.findAllByUserId(1L))
            .thenReturn(List.of());

        budgetAlertService.sendInitialAlerts(1L);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    /**
     * Test: resetAlertState when no state exists does nothing.
     */
    @Test
    void resetAlertState_noExistingState_doesNothing() {
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.empty());

        budgetAlertService.resetAlertState(1L, (short) 2026, (short) 3);

        verify(budgetAlertStateRepository, never()).save(any());
    }

    /**
     * Test: getCurrentlyCrossedAlerts with no budget returns empty.
     */
    @Test
    void getCurrentlyCrossedAlerts_noBudget_returnsEmpty() {
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.empty());

        List<BudgetAlertMessage> alerts = budgetAlertService.getCurrentlyCrossedAlerts(1L, testYearMonth);

        assertThat(alerts).isEmpty();
    }

    /**
     * Test: getCurrentlyCrossedAlerts returns all crossed thresholds including 100%.
     */
    @Test
    void getCurrentlyCrossedAlerts_allThresholdsCrossed_returnsAll() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("1200.00")); // 120%

        List<BudgetAlertMessage> alerts = budgetAlertService.getCurrentlyCrossedAlerts(1L, testYearMonth);

        assertThat(alerts).hasSize(3);
        assertThat(alerts).extracting(BudgetAlertMessage::threshold).containsExactly(50, 80, 100);
    }

    // ==================== CROSS-MONTH SEQUENTIAL TEST ====================

    /**
     * Comprehensive cross-month sequential test matching the exact user scenario:
     * - Create budgets for Feb, Mar, Apr (each $1000)
     * - Add expense to Feb crossing 50% -> verify 50% alert fires for Feb
     * - Then add expense to Mar crossing 50% -> verify SEPARATE 50% alert fires for Mar
     * - Then add expense to Apr crossing 80% -> verify 80% alert fires for Apr
     * - Verify all three are independent alert sends (events published)
     */
    @Test
    void crossMonthSequential_independentAlertsForEachMonth() {
        // Create budgets for Feb, Mar, Apr
        MonthlyBudget febBudget = createBudgetForMonth(new BigDecimal("1000.00"), (short) 2026, (short) 2);
        MonthlyBudget marBudget = createBudgetForMonth(new BigDecimal("1000.00"), (short) 2026, (short) 3);
        MonthlyBudget aprBudget = createBudgetForMonth(new BigDecimal("1000.00"), (short) 2026, (short) 4);

        // Create independent alert states for each month (all fresh/unfired)
        BudgetAlertState febState = createAlertStateForMonth(false, false, false, (short) 2026, (short) 2);
        BudgetAlertState marState = createAlertStateForMonth(false, false, false, (short) 2026, (short) 3);
        BudgetAlertState aprState = createAlertStateForMonth(false, false, false, (short) 2026, (short) 4);

        // Setup mocks for February
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 2))
            .thenReturn(Optional.of(febBudget));
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 2))
            .thenReturn(Optional.of(febState));
        when(transactionRepository.sumAmountByUserIdAndDateRange(1L,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
            .thenReturn(new BigDecimal("550.00")); // 55% -> crosses 50%

        // Setup mocks for March
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(marBudget));
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(marState));
        when(transactionRepository.sumAmountByUserIdAndDateRange(1L,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(new BigDecimal("600.00")); // 60% -> crosses 50%

        // Setup mocks for April
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 4))
            .thenReturn(Optional.of(aprBudget));
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 4))
            .thenReturn(Optional.of(aprState));
        when(transactionRepository.sumAmountByUserIdAndDateRange(1L,
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
            .thenReturn(new BigDecimal("850.00")); // 85% -> crosses 50% and 80%

        // Step 1: Add expense to February crossing 50%
        budgetAlertService.evaluateAlerts(1L, LocalDate.of(2026, 2, 15));

        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        BudgetAlertEvent febEvent = eventCaptor.getValue();
        assertThat(febEvent.userId()).isEqualTo(1L);
        assertThat(febEvent.alerts()).hasSize(1);
        assertThat(febEvent.alerts().get(0).threshold()).isEqualTo(50);
        assertThat(febEvent.alerts().get(0).yearMonth()).isEqualTo("2026-02");
        assertThat(febState.getThreshold50Fired()).isTrue();
        assertThat(febState.getThreshold80Fired()).isFalse();

        // Step 2: Add expense to March crossing 50% -> SEPARATE alert for March
        budgetAlertService.evaluateAlerts(1L, LocalDate.of(2026, 3, 10));

        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        BudgetAlertEvent marEvent = eventCaptor.getValue();
        assertThat(marEvent.userId()).isEqualTo(1L);
        assertThat(marEvent.alerts()).hasSize(1);
        assertThat(marEvent.alerts().get(0).threshold()).isEqualTo(50);
        assertThat(marEvent.alerts().get(0).yearMonth()).isEqualTo("2026-03");
        assertThat(marState.getThreshold50Fired()).isTrue();
        assertThat(marState.getThreshold80Fired()).isFalse();

        // Verify Feb state wasn't changed by March evaluation
        assertThat(febState.getThreshold50Fired()).isTrue();
        assertThat(febState.getThreshold80Fired()).isFalse();

        // Step 3: Add expense to April crossing 80% (also crosses 50%)
        budgetAlertService.evaluateAlerts(1L, LocalDate.of(2026, 4, 20));

        verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
        BudgetAlertEvent aprEvent = eventCaptor.getValue();
        assertThat(aprEvent.userId()).isEqualTo(1L);
        assertThat(aprEvent.alerts()).hasSize(2); // 50% and 80%
        assertThat(aprEvent.alerts().get(0).threshold()).isEqualTo(50);
        assertThat(aprEvent.alerts().get(0).yearMonth()).isEqualTo("2026-04");
        assertThat(aprEvent.alerts().get(1).threshold()).isEqualTo(80);
        assertThat(aprEvent.alerts().get(1).yearMonth()).isEqualTo("2026-04");
        assertThat(aprState.getThreshold50Fired()).isTrue();
        assertThat(aprState.getThreshold80Fired()).isTrue();
        assertThat(aprState.getThreshold100Fired()).isFalse();

        // Verify all three months have independent states
        // Feb: only 50% fired
        assertThat(febState.getThreshold50Fired()).isTrue();
        assertThat(febState.getThreshold80Fired()).isFalse();
        assertThat(febState.getThreshold100Fired()).isFalse();

        // Mar: only 50% fired
        assertThat(marState.getThreshold50Fired()).isTrue();
        assertThat(marState.getThreshold80Fired()).isFalse();
        assertThat(marState.getThreshold100Fired()).isFalse();

        // Apr: 50% and 80% fired
        assertThat(aprState.getThreshold50Fired()).isTrue();
        assertThat(aprState.getThreshold80Fired()).isTrue();
        assertThat(aprState.getThreshold100Fired()).isFalse();

        // Verify messagingTemplate was NEVER called directly (only via event listener)
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    // ==================== HELPER METHODS ====================

    private MonthlyBudget createBudget(BigDecimal amount) {
        return createBudgetForMonth(amount, (short) 2026, (short) 3);
    }

    private MonthlyBudget createBudgetForMonth(BigDecimal amount, Short year, Short month) {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setId((long) (year * 100 + month));
        budget.setUser(testUser);
        budget.setYear(year);
        budget.setMonth(month);
        budget.setAmount(amount);
        return budget;
    }

    private BudgetAlertState createAlertState(boolean fired50, boolean fired80, boolean fired100) {
        return createAlertStateForMonth(fired50, fired80, fired100, (short) 2026, (short) 3);
    }

    private BudgetAlertState createAlertStateForMonth(boolean fired50, boolean fired80, boolean fired100,
                                                       Short year, Short month) {
        BudgetAlertState state = new BudgetAlertState();
        state.setId((long) (year * 100 + month));
        state.setUser(testUser);
        state.setYear(year);
        state.setMonth(month);
        state.setThreshold50Fired(fired50);
        state.setThreshold80Fired(fired80);
        state.setThreshold100Fired(fired100);
        return state;
    }
}
