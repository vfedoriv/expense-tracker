package com.expensetracker.budget;

import com.expensetracker.budget.dto.BudgetSummaryResponse;
import com.expensetracker.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BudgetService {

    private final MonthlyBudgetRepository budgetRepository;
    private final TransactionService transactionService;

    @Transactional(readOnly = true)
    public BudgetSummaryResponse getSummary(Long userId, int year, int month) {
        BigDecimal totalSpent = transactionService.sumForMonth(userId, year, month);
        Optional<MonthlyBudget> budgetOpt = budgetRepository.findByUserIdAndYearAndMonth(userId, year, month);

        if (budgetOpt.isEmpty()) {
            return BudgetSummaryResponse.noBudget(year, month, totalSpent);
        }
        return BudgetSummaryResponse.withBudget(year, month, budgetOpt.get().getAmount(), totalSpent);
    }

    public MonthlyBudget setOrUpdate(Long userId, int year, int month, BigDecimal amount) {
        MonthlyBudget budget = budgetRepository.findByUserIdAndYearAndMonth(userId, year, month)
            .orElseGet(() -> MonthlyBudget.builder().userId(userId).year(year).month(month).build());
        budget.setAmount(amount);
        return budgetRepository.save(budget);
    }
}
