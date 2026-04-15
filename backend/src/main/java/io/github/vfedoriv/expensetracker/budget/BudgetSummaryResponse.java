package io.github.vfedoriv.expensetracker.budget;

import java.math.BigDecimal;

public record BudgetSummaryResponse(
    boolean budgetSet,
    BigDecimal budgetAmount,
    BigDecimal totalSpent,
    BigDecimal remaining,
    Integer percentage,
    Integer year,
    Integer month
) {}
