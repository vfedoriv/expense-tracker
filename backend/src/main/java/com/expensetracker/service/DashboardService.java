package com.expensetracker.service;

import com.expensetracker.dto.response.DashboardResponse;
import com.expensetracker.entity.MonthlyBudget;
import com.expensetracker.repository.MonthlyBudgetRepository;
import com.expensetracker.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public DashboardService(
            TransactionRepository transactionRepository,
            MonthlyBudgetRepository monthlyBudgetRepository) {
        this.transactionRepository = transactionRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        BigDecimal totalSpent =
                transactionRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate);

        Optional<MonthlyBudget> budgetOpt =
                monthlyBudgetRepository.findByUserIdAndYearAndMonth(
                        userId, (short) year, (short) month);

        if (budgetOpt.isPresent()) {
            BigDecimal budgetAmount = budgetOpt.get().getAmount();
            BigDecimal remaining = budgetAmount.subtract(totalSpent);
            BigDecimal usagePercentage = BigDecimal.ZERO;
            if (budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
                usagePercentage =
                        totalSpent
                                .multiply(BigDecimal.valueOf(100))
                                .divide(budgetAmount, 2, RoundingMode.HALF_UP);
            }
            return new DashboardResponse(totalSpent, budgetAmount, remaining, usagePercentage);
        } else {
            return new DashboardResponse(totalSpent, null, null, null);
        }
    }
}
