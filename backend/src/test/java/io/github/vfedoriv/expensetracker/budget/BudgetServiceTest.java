package io.github.vfedoriv.expensetracker.budget;

import io.github.vfedoriv.expensetracker.exception.ResourceNotFoundException;
import io.github.vfedoriv.expensetracker.transaction.TransactionRepository;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BudgetService budgetService;

    private User testUser;

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
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("Should return summary with budgetSet=true when budget exists")
        void getSummary_withBudget_returnsSummary() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L)
                    .user(testUser)
                    .year(2026)
                    .month(3)
                    .amount(new BigDecimal("1000.00"))
                    .build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.of(budget));

            LocalDate firstDay = YearMonth.of(2026, 3).atDay(1);
            LocalDate lastDay = YearMonth.of(2026, 3).atEndOfMonth();
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("500.00"));

            BudgetSummaryResponse result = budgetService.getSummary(1L, 2026, 3);

            assertThat(result.budgetSet()).isTrue();
            assertThat(result.budgetAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(result.totalSpent()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(result.remaining()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(result.percentage()).isEqualTo(50);
            assertThat(result.year()).isEqualTo(2026);
            assertThat(result.month()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return summary with budgetSet=false when no budget exists")
        void getSummary_withoutBudget_returnsBudgetSetFalse() {
            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.empty());

            LocalDate firstDay = YearMonth.of(2026, 3).atDay(1);
            LocalDate lastDay = YearMonth.of(2026, 3).atEndOfMonth();
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("250.00"));

            BudgetSummaryResponse result = budgetService.getSummary(1L, 2026, 3);

            assertThat(result.budgetSet()).isFalse();
            assertThat(result.budgetAmount()).isNull();
            assertThat(result.remaining()).isNull();
            assertThat(result.percentage()).isNull();
            assertThat(result.totalSpent()).isEqualByComparingTo(new BigDecimal("250.00"));
            assertThat(result.year()).isEqualTo(2026);
            assertThat(result.month()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should calculate percentage correctly when budget is fully spent")
        void getSummary_fullySpent_returns100Percent() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L)
                    .user(testUser)
                    .year(2026)
                    .month(3)
                    .amount(new BigDecimal("1000.00"))
                    .build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.of(budget));

            LocalDate firstDay = YearMonth.of(2026, 3).atDay(1);
            LocalDate lastDay = YearMonth.of(2026, 3).atEndOfMonth();
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("1000.00"));

            BudgetSummaryResponse result = budgetService.getSummary(1L, 2026, 3);

            assertThat(result.percentage()).isEqualTo(100);
            assertThat(result.remaining()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should return zero percentage when no spending")
        void getSummary_noSpending_returnsZeroPercent() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L)
                    .user(testUser)
                    .year(2026)
                    .month(3)
                    .amount(new BigDecimal("1000.00"))
                    .build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.of(budget));

            LocalDate firstDay = YearMonth.of(2026, 3).atDay(1);
            LocalDate lastDay = YearMonth.of(2026, 3).atEndOfMonth();
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(BigDecimal.ZERO);

            BudgetSummaryResponse result = budgetService.getSummary(1L, 2026, 3);

            assertThat(result.percentage()).isEqualTo(0);
            assertThat(result.remaining()).isEqualByComparingTo(new BigDecimal("1000.00"));
        }
    }

    @Nested
    @DisplayName("setBudget")
    class SetBudget {

        @Test
        @DisplayName("Should create new budget when none exists")
        void setBudget_createsNew() {
            BudgetRequest request = new BudgetRequest(new BigDecimal("2000.00"));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.empty());
            when(monthlyBudgetRepository.save(any(MonthlyBudget.class)))
                    .thenAnswer(invocation -> {
                        MonthlyBudget saved = invocation.getArgument(0);
                        saved.setId(1L);
                        return saved;
                    });

            // For getSummary call within setBudget
            MonthlyBudget savedBudget = MonthlyBudget.builder()
                    .id(1L)
                    .user(testUser)
                    .year(2026)
                    .month(3)
                    .amount(new BigDecimal("2000.00"))
                    .build();
            // After save, getSummary is called which queries again
            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.empty())       // first call in setBudget
                    .thenReturn(Optional.of(savedBudget)); // second call in getSummary

            LocalDate firstDay = YearMonth.of(2026, 3).atDay(1);
            LocalDate lastDay = YearMonth.of(2026, 3).atEndOfMonth();
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("500.00"));

            BudgetSummaryResponse result = budgetService.setBudget(1L, 2026, 3, request);

            assertThat(result.budgetSet()).isTrue();
            assertThat(result.budgetAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
            verify(monthlyBudgetRepository).save(any(MonthlyBudget.class));
        }

        @Test
        @DisplayName("Should update existing budget")
        void setBudget_updatesExisting() {
            BudgetRequest request = new BudgetRequest(new BigDecimal("3000.00"));

            MonthlyBudget existingBudget = MonthlyBudget.builder()
                    .id(1L)
                    .user(testUser)
                    .year(2026)
                    .month(3)
                    .amount(new BigDecimal("2000.00"))
                    .build();

            MonthlyBudget updatedBudget = MonthlyBudget.builder()
                    .id(1L)
                    .user(testUser)
                    .year(2026)
                    .month(3)
                    .amount(new BigDecimal("3000.00"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.of(existingBudget))  // first call in setBudget
                    .thenReturn(Optional.of(updatedBudget));   // second call in getSummary
            when(monthlyBudgetRepository.save(any(MonthlyBudget.class)))
                    .thenReturn(updatedBudget);

            LocalDate firstDay = YearMonth.of(2026, 3).atDay(1);
            LocalDate lastDay = YearMonth.of(2026, 3).atEndOfMonth();
            when(transactionRepository.sumByUserAndDateRange(1L, firstDay, lastDay))
                    .thenReturn(new BigDecimal("500.00"));

            BudgetSummaryResponse result = budgetService.setBudget(1L, 2026, 3, request);

            assertThat(result.budgetSet()).isTrue();
            assertThat(result.budgetAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));
            verify(monthlyBudgetRepository).save(existingBudget);
            assertThat(existingBudget.getAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void setBudget_userNotFound_throws() {
            BudgetRequest request = new BudgetRequest(new BigDecimal("2000.00"));

            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.setBudget(1L, 2026, 3, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(monthlyBudgetRepository, never()).save(any(MonthlyBudget.class));
        }
    }

    @Nested
    @DisplayName("deleteBudget")
    class DeleteBudget {

        @Test
        @DisplayName("Should delete budget successfully")
        void deleteBudget_happyPath() {
            MonthlyBudget budget = MonthlyBudget.builder()
                    .id(1L)
                    .user(testUser)
                    .year(2026)
                    .month(3)
                    .amount(new BigDecimal("1000.00"))
                    .build();

            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.of(budget));

            budgetService.deleteBudget(1L, 2026, 3);

            verify(monthlyBudgetRepository).delete(budget);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when budget not found")
        void deleteBudget_notFound_throws() {
            when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, 2026, 3))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.deleteBudget(1L, 2026, 3))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Budget not found");

            verify(monthlyBudgetRepository, never()).delete(any(MonthlyBudget.class));
        }
    }
}
