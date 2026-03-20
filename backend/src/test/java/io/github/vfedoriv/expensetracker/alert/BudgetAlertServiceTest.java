package io.github.vfedoriv.expensetracker.alert;

import io.github.vfedoriv.expensetracker.budget.MonthlyBudget;
import io.github.vfedoriv.expensetracker.budget.MonthlyBudgetRepository;
import io.github.vfedoriv.expensetracker.transaction.TransactionChangedEvent;
import io.github.vfedoriv.expensetracker.transaction.TransactionRepository;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetAlertServiceTest {

    @Mock
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetAlertLogRepository budgetAlertLogRepository;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<BudgetAlertLog> alertLogCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> messageCaptor;

    @InjectMocks
    private BudgetAlertService budgetAlertService;

    private User testUser;
    private final LocalDate today = LocalDate.now();
    private final int currentYear = today.getYear();
    private final int currentMonth = today.getMonthValue();
    private final LocalDate firstDay = YearMonth.of(currentYear, currentMonth).atDay(1);
    private final LocalDate lastDay = YearMonth.of(currentYear, currentMonth).atEndOfMonth();

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .provider("fake")
                .providerUserId("fake-user-1")
                .email("admin@test.com")
                .displayName("Admin User")
                .build();
    }

    @Nested
    @DisplayName("checkAndSendAlerts")
    class CheckAndSendAlerts {

        @Test
        @DisplayName("Should not send alerts when no budget is set")
        void noBudget_noAlertsSent() {
            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.empty());

            budgetAlertService.checkAndSendAlerts(1L);

            verify(simpMessagingTemplate, never())
                    .convertAndSendToUser(anyString(), anyString(), any());
            verify(budgetAlertLogRepository, never()).save(any(BudgetAlertLog.class));
        }

        @Test
        @DisplayName("Should send 50% threshold alert when spending crosses 50%")
        void fiftyPercentThresholdCrossed_alertSent() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L).user(testUser).year(currentYear).month(currentMonth)
                    .amount(new BigDecimal("1000.00")).build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.of(budget));
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("550.00"));

            // 50% threshold not yet alerted
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 50)).thenReturn(false);
            // 80% and 100% not crossed (55% < 80%)
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(budgetAlertLogRepository.save(any(BudgetAlertLog.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            budgetAlertService.checkAndSendAlerts(1L);

            verify(budgetAlertLogRepository).save(alertLogCaptor.capture());
            BudgetAlertLog savedLog = alertLogCaptor.getValue();
            assertThat(savedLog.getThreshold()).isEqualTo(50);
            assertThat(savedLog.getYear()).isEqualTo(currentYear);
            assertThat(savedLog.getMonth()).isEqualTo(currentMonth);
            assertThat(savedLog.getAcknowledged()).isFalse();

            verify(simpMessagingTemplate).convertAndSendToUser(
                    eq("1"), eq("/queue/budget-alerts"), messageCaptor.capture());
            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("type")).isEqualTo("BUDGET_ALERT");
            assertThat(message.get("threshold")).isEqualTo(50);
        }

        @Test
        @DisplayName("Should send 80% threshold alert when spending crosses 80%")
        void eightyPercentThresholdCrossed_alertSent() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L).user(testUser).year(currentYear).month(currentMonth)
                    .amount(new BigDecimal("1000.00")).build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.of(budget));
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("850.00"));

            // 50% already alerted, 80% not yet, 100% not crossed
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 50)).thenReturn(true);
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 80)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(budgetAlertLogRepository.save(any(BudgetAlertLog.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            budgetAlertService.checkAndSendAlerts(1L);

            verify(budgetAlertLogRepository).save(alertLogCaptor.capture());
            assertThat(alertLogCaptor.getValue().getThreshold()).isEqualTo(80);

            verify(simpMessagingTemplate).convertAndSendToUser(
                    eq("1"), eq("/queue/budget-alerts"), messageCaptor.capture());
            assertThat(messageCaptor.getValue().get("threshold")).isEqualTo(80);
        }

        @Test
        @DisplayName("Should send 100% threshold alert when spending crosses 100%")
        void hundredPercentThresholdCrossed_alertSent() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L).user(testUser).year(currentYear).month(currentMonth)
                    .amount(new BigDecimal("1000.00")).build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.of(budget));
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("1050.00"));

            // 50% and 80% already alerted, 100% not yet
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 50)).thenReturn(true);
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 80)).thenReturn(true);
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 100)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(budgetAlertLogRepository.save(any(BudgetAlertLog.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            budgetAlertService.checkAndSendAlerts(1L);

            verify(budgetAlertLogRepository).save(alertLogCaptor.capture());
            assertThat(alertLogCaptor.getValue().getThreshold()).isEqualTo(100);

            verify(simpMessagingTemplate).convertAndSendToUser(
                    eq("1"), eq("/queue/budget-alerts"), messageCaptor.capture());
            assertThat(messageCaptor.getValue().get("threshold")).isEqualTo(100);
        }

        @Test
        @DisplayName("Should not send duplicate alert for already-alerted threshold")
        void alreadyAlertedThreshold_noDuplicateAlert() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L).user(testUser).year(currentYear).month(currentMonth)
                    .amount(new BigDecimal("1000.00")).build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.of(budget));
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("550.00"));

            // 50% already alerted
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 50)).thenReturn(true);

            budgetAlertService.checkAndSendAlerts(1L);

            verify(budgetAlertLogRepository, never()).save(any(BudgetAlertLog.class));
            verify(simpMessagingTemplate, never())
                    .convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should send multiple alerts when multiple thresholds crossed simultaneously")
        void multipleThresholdsCrossed_multipleAlertsSent() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L).user(testUser).year(currentYear).month(currentMonth)
                    .amount(new BigDecimal("1000.00")).build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.of(budget));
            // Spending at 105% crosses all three thresholds
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("1050.00"));

            // None alerted yet
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 50)).thenReturn(false);
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 80)).thenReturn(false);
            when(budgetAlertLogRepository.existsByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 100)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(budgetAlertLogRepository.save(any(BudgetAlertLog.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            budgetAlertService.checkAndSendAlerts(1L);

            verify(budgetAlertLogRepository, times(3)).save(alertLogCaptor.capture());
            assertThat(alertLogCaptor.getAllValues())
                    .extracting(BudgetAlertLog::getThreshold)
                    .containsExactly(50, 80, 100);

            verify(simpMessagingTemplate, times(3))
                    .convertAndSendToUser(eq("1"), eq("/queue/budget-alerts"), any());
        }

        @Test
        @DisplayName("Should not send alerts when spending is below 50%")
        void spendingBelow50Percent_noAlerts() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L).user(testUser).year(currentYear).month(currentMonth)
                    .amount(new BigDecimal("1000.00")).build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.of(budget));
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("300.00"));

            budgetAlertService.checkAndSendAlerts(1L);

            verify(budgetAlertLogRepository, never()).save(any(BudgetAlertLog.class));
            verify(simpMessagingTemplate, never())
                    .convertAndSendToUser(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("acknowledgeAlert")
    class AcknowledgeAlert {

        @Test
        @DisplayName("Should set acknowledged=true on existing alert log")
        void acknowledgeAlert_setsAcknowledgedTrue() {
            BudgetAlertLog alertLog = BudgetAlertLog.builder()
                    .id(1L)
                    .user(testUser)
                    .year(currentYear)
                    .month(currentMonth)
                    .threshold(50)
                    .acknowledged(false)
                    .build();

            when(budgetAlertLogRepository.findByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 50))
                    .thenReturn(Optional.of(alertLog));
            when(budgetAlertLogRepository.save(any(BudgetAlertLog.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            budgetAlertService.acknowledgeAlert(1L, 50);

            assertThat(alertLog.getAcknowledged()).isTrue();
            verify(budgetAlertLogRepository).save(alertLog);
        }

        @Test
        @DisplayName("Should do nothing when alert log not found")
        void acknowledgeAlert_notFound_doesNothing() {
            when(budgetAlertLogRepository.findByUserIdAndYearAndMonthAndThreshold(
                    1L, currentYear, currentMonth, 50))
                    .thenReturn(Optional.empty());

            budgetAlertService.acknowledgeAlert(1L, 50);

            verify(budgetAlertLogRepository, never()).save(any(BudgetAlertLog.class));
        }
    }

    @Nested
    @DisplayName("onTransactionChanged")
    class OnTransactionChanged {

        @Test
        @DisplayName("Should delegate to checkAndSendAlerts with correct userId")
        void onTransactionChanged_delegatesToCheckAndSendAlerts() {
            // No budget set, so checkAndSendAlerts will return early
            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, currentYear, currentMonth))
                    .thenReturn(Optional.empty());

            budgetAlertService.onTransactionChanged(new TransactionChangedEvent(1L));

            verify(monthlyBudgetRepository)
                    .findByUserIdAndYearAndMonth(1L, currentYear, currentMonth);
        }
    }
}
