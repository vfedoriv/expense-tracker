package io.github.vfedoriv.expensetracker.budget;

import io.github.vfedoriv.expensetracker.exception.ResourceNotFoundException;
import io.github.vfedoriv.expensetracker.transaction.TransactionRepository;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public BudgetSummaryResponse getSummary(Long userId, int year, int month) {
        Optional<MonthlyBudget> budgetOpt = monthlyBudgetRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        BigDecimal totalSpent = calculateSpent(userId, year, month);

        if (budgetOpt.isEmpty()) {
            return new BudgetSummaryResponse(
                    false,
                    null,
                    totalSpent,
                    null,
                    null,
                    year,
                    month
            );
        }

        MonthlyBudget budget = budgetOpt.get();
        BigDecimal budgetAmount = budget.getAmount();
        BigDecimal remaining = budgetAmount.subtract(totalSpent);
        int percentage = budgetAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.multiply(BigDecimal.valueOf(100))
                    .divide(budgetAmount, 0, RoundingMode.HALF_UP)
                    .intValue()
                : 0;

        return new BudgetSummaryResponse(
                true,
                budgetAmount,
                totalSpent,
                remaining,
                percentage,
                year,
                month
        );
    }

    @Transactional
    public BudgetSummaryResponse setBudget(Long userId, int year, int month, BudgetRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MonthlyBudget budget = monthlyBudgetRepository
                .findByUserIdAndYearAndMonth(userId, year, month)
                .orElseGet(() -> MonthlyBudget.builder()
                        .user(user)
                        .year(year)
                        .month(month)
                        .build());

        budget.setAmount(request.amount());
        monthlyBudgetRepository.save(budget);

        return getSummary(userId, year, month);
    }

    @Transactional
    public void deleteBudget(Long userId, int year, int month) {
        MonthlyBudget budget = monthlyBudgetRepository
                .findByUserIdAndYearAndMonth(userId, year, month)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Budget not found for " + year + "-" + month));

        monthlyBudgetRepository.delete(budget);
    }

    private BigDecimal calculateSpent(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();
        return transactionRepository.sumByUserAndDateRange(userId, firstDay, lastDay);
    }
}
