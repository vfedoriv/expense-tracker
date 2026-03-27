package com.expensetracker.budget;

import com.expensetracker.budget.dto.BudgetSummaryResponse;
import com.expensetracker.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private MonthlyBudgetRepository budgetRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void getSummary_whenNoBudgetSet_returnsNoBudgetState() {
        Long userId = 1L;
        when(transactionService.sumForMonth(userId, 2026, 3)).thenReturn(new BigDecimal("150.00"));
        when(budgetRepository.findByUserIdAndYearAndMonth(userId, 2026, 3)).thenReturn(Optional.empty());

        BudgetSummaryResponse result = budgetService.getSummary(userId, 2026, 3);

        assertThat(result.budgetSet()).isFalse();
        assertThat(result.budget()).isNull();
        assertThat(result.totalSpent()).isEqualByComparingTo("150.00");
        assertThat(result.remaining()).isNull();
        assertThat(result.usagePercent()).isNull();
    }

    @Test
    void getSummary_withBudget_calculatesCorrectly() {
        Long userId = 1L;
        MonthlyBudget budget = MonthlyBudget.builder()
            .id(1L).userId(userId).year(2026).month(3).amount(new BigDecimal("500.00")).build();
        when(transactionService.sumForMonth(userId, 2026, 3)).thenReturn(new BigDecimal("250.00"));
        when(budgetRepository.findByUserIdAndYearAndMonth(userId, 2026, 3)).thenReturn(Optional.of(budget));

        BudgetSummaryResponse result = budgetService.getSummary(userId, 2026, 3);

        assertThat(result.budgetSet()).isTrue();
        assertThat(result.budget()).isEqualByComparingTo("500.00");
        assertThat(result.totalSpent()).isEqualByComparingTo("250.00");
        assertThat(result.remaining()).isEqualByComparingTo("250.00");
        assertThat(result.usagePercent()).isEqualTo(50.0);
    }

    @Test
    void getSummary_whenOverBudget_returnsNegativeRemaining() {
        Long userId = 1L;
        MonthlyBudget budget = MonthlyBudget.builder()
            .id(1L).userId(userId).year(2026).month(3).amount(new BigDecimal("100.00")).build();
        when(transactionService.sumForMonth(userId, 2026, 3)).thenReturn(new BigDecimal("120.00"));
        when(budgetRepository.findByUserIdAndYearAndMonth(userId, 2026, 3)).thenReturn(Optional.of(budget));

        BudgetSummaryResponse result = budgetService.getSummary(userId, 2026, 3);

        assertThat(result.remaining()).isNegative();
        assertThat(result.usagePercent()).isEqualTo(120.0);
    }

    @Test
    void setOrUpdate_whenNoBudgetExists_createsNew() {
        Long userId = 1L;
        when(budgetRepository.findByUserIdAndYearAndMonth(userId, 2026, 3)).thenReturn(Optional.empty());
        when(budgetRepository.save(any(MonthlyBudget.class))).thenAnswer(inv -> inv.getArgument(0));

        budgetService.setOrUpdate(userId, 2026, 3, new BigDecimal("500.00"));

        verify(budgetRepository).save(argThat(b ->
            b.getAmount().compareTo(new BigDecimal("500.00")) == 0 &&
            b.getYear().equals(2026) &&
            b.getMonth().equals(3)
        ));
    }

    @Test
    void setOrUpdate_whenBudgetExists_updatesAmount() {
        Long userId = 1L;
        MonthlyBudget existing = MonthlyBudget.builder()
            .id(1L).userId(userId).year(2026).month(3).amount(new BigDecimal("300.00")).build();
        when(budgetRepository.findByUserIdAndYearAndMonth(userId, 2026, 3)).thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(MonthlyBudget.class))).thenAnswer(inv -> inv.getArgument(0));

        budgetService.setOrUpdate(userId, 2026, 3, new BigDecimal("600.00"));

        verify(budgetRepository).save(argThat(b ->
            b.getId().equals(1L) && b.getAmount().compareTo(new BigDecimal("600.00")) == 0
        ));
    }
}
