package com.expensetracker.service;

import com.expensetracker.dto.response.DashboardResponse;
import com.expensetracker.entity.MonthlyBudget;
import com.expensetracker.repository.MonthlyBudgetRepository;
import com.expensetracker.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void getDashboard_withBudgetAndTransactions_returnsCorrectCalculations() {
        Long userId = 1L;
        int year = 2026;
        int month = 3;
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);

        when(transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate))
            .thenReturn(new BigDecimal("750.00"));

        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, (short) year, (short) month))
            .thenReturn(Optional.of(budget));

        DashboardResponse response = dashboardService.getDashboard(userId, year, month);

        assertThat(response.totalSpent()).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(response.budgetAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.remaining()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(response.usagePercentage()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void getDashboard_withBudgetAndNoTransactions_returnsZeroSpent() {
        Long userId = 1L;
        int year = 2026;
        int month = 3;
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);

        when(transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate))
            .thenReturn(BigDecimal.ZERO);

        MonthlyBudget budget = createBudget(new BigDecimal("500.00"));
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, (short) year, (short) month))
            .thenReturn(Optional.of(budget));

        DashboardResponse response = dashboardService.getDashboard(userId, year, month);

        assertThat(response.totalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.budgetAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(response.remaining()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(response.usagePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getDashboard_noBudget_returnsNullBudgetFields() {
        Long userId = 1L;
        int year = 2026;
        int month = 3;
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);

        when(transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate))
            .thenReturn(new BigDecimal("200.00"));

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, (short) year, (short) month))
            .thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard(userId, year, month);

        assertThat(response.totalSpent()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(response.budgetAmount()).isNull();
        assertThat(response.remaining()).isNull();
        assertThat(response.usagePercentage()).isNull();
    }

    @Test
    void getDashboard_overspent_returnsNegativeRemaining() {
        Long userId = 1L;
        int year = 2026;
        int month = 3;
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);

        when(transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate))
            .thenReturn(new BigDecimal("1200.00"));

        MonthlyBudget budget = createBudget(new BigDecimal("1000.00"));
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, (short) year, (short) month))
            .thenReturn(Optional.of(budget));

        DashboardResponse response = dashboardService.getDashboard(userId, year, month);

        assertThat(response.totalSpent()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(response.budgetAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.remaining()).isEqualByComparingTo(new BigDecimal("-200.00"));
        assertThat(response.usagePercentage()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    void getDashboard_noBudgetNoTransactions_returnsZeroSpentAndNullBudget() {
        Long userId = 1L;
        int year = 2026;
        int month = 6;
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);

        when(transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate))
            .thenReturn(BigDecimal.ZERO);

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, (short) year, (short) month))
            .thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard(userId, year, month);

        assertThat(response.totalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.budgetAmount()).isNull();
        assertThat(response.remaining()).isNull();
        assertThat(response.usagePercentage()).isNull();
    }

    @Test
    void getDashboard_monthZero_throwsIllegalArgument() {
        assertThatThrownBy(() -> dashboardService.getDashboard(1L, 2026, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Month must be between 1 and 12");
    }

    @Test
    void getDashboard_month13_throwsIllegalArgument() {
        assertThatThrownBy(() -> dashboardService.getDashboard(1L, 2026, 13))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Month must be between 1 and 12");
    }

    @Test
    void getDashboard_yearTooLow_throwsIllegalArgument() {
        assertThatThrownBy(() -> dashboardService.getDashboard(1L, 1999, 6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Year must be between 2000 and 2100");
    }

    @Test
    void getDashboard_yearTooHigh_throwsIllegalArgument() {
        assertThatThrownBy(() -> dashboardService.getDashboard(1L, 2101, 6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Year must be between 2000 and 2100");
    }

    @Test
    void getDashboard_february_usesCorrectDateRange() {
        Long userId = 1L;
        int year = 2026;
        int month = 2;
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);

        when(transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate))
            .thenReturn(new BigDecimal("100.00"));

        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, (short) year, (short) month))
            .thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard(userId, year, month);

        assertThat(response.totalSpent()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    private MonthlyBudget createBudget(BigDecimal amount) {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setId(1L);
        budget.setYear((short) 2026);
        budget.setMonth((short) 3);
        budget.setAmount(amount);
        budget.setCreatedAt(OffsetDateTime.now());
        budget.setUpdatedAt(OffsetDateTime.now());
        return budget;
    }
}
