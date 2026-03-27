package com.expensetracker.budget.dto;

import java.math.BigDecimal;

public record BudgetSummaryResponse(
    Integer year,
    Integer month,
    BigDecimal budget,
    BigDecimal totalSpent,
    BigDecimal remaining,
    Double usagePercent,
    boolean budgetSet
) {
    public static BudgetSummaryResponse noBudget(int year, int month, BigDecimal totalSpent) {
        return new BudgetSummaryResponse(year, month, null, totalSpent, null, null, false);
    }

    public static BudgetSummaryResponse withBudget(int year, int month, BigDecimal budget, BigDecimal totalSpent) {
        BigDecimal remaining = budget.subtract(totalSpent);
        double usagePercent = budget.compareTo(BigDecimal.ZERO) > 0
            ? totalSpent.doubleValue() / budget.doubleValue() * 100.0
            : 0.0;
        return new BudgetSummaryResponse(year, month, budget, totalSpent, remaining, usagePercent, true);
    }
}
