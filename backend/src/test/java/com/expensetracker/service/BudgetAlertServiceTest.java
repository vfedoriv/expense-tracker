package com.expensetracker.service;

import com.expensetracker.dto.response.BudgetAlertMessage;
import com.expensetracker.entity.BudgetAlertState;
import com.expensetracker.entity.MonthlyBudget;
import com.expensetracker.entity.User;
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

    @InjectMocks
    private BudgetAlertService budgetAlertService;

    @Captor
    private ArgumentCaptor<BudgetAlertMessage> alertCaptor;

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
    void evaluateAlerts_sendsAlertViaMessagingTemplate() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        YearMonth now = YearMonth.now();
        Short year = (short) now.getYear();
        Short month = (short) now.getMonthValue();

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, year, month))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("500.00")); // 50%
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, year, month))
            .thenReturn(Optional.of(alertState));

        budgetAlertService.evaluateAlerts(1L);

        verify(messagingTemplate).convertAndSendToUser(
            eq("1"),
            eq("/topic/budget-alerts"),
            alertCaptor.capture()
        );

        BudgetAlertMessage alert = alertCaptor.getValue();
        assertThat(alert.type()).isEqualTo("BUDGET_ALERT");
        assertThat(alert.threshold()).isEqualTo(50);
    }

    @Test
    void sendInitialAlerts_sendsAlertForPreCrossedThresholds() {
        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        BudgetAlertState alertState = createAlertState(false, false, false);

        YearMonth now = YearMonth.now();
        Short year = (short) now.getYear();
        Short month = (short) now.getMonthValue();

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, year, month))
            .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("900.00")); // 90% - crosses 50 and 80
        when(budgetAlertStateRepository.findByUserIdAndYearAndMonth(1L, year, month))
            .thenReturn(Optional.of(alertState));

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

    private MonthlyBudget createBudget(BigDecimal amount) {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setId(1L);
        budget.setUser(testUser);
        budget.setYear((short) 2026);
        budget.setMonth((short) 3);
        budget.setAmount(amount);
        return budget;
    }

    private BudgetAlertState createAlertState(boolean fired50, boolean fired80, boolean fired100) {
        BudgetAlertState state = new BudgetAlertState();
        state.setId(1L);
        state.setUser(testUser);
        state.setYear((short) 2026);
        state.setMonth((short) 3);
        state.setThreshold50Fired(fired50);
        state.setThreshold80Fired(fired80);
        state.setThreshold100Fired(fired100);
        return state;
    }
}
