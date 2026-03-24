package com.expensetracker.dto.response;

import java.math.BigDecimal;

public record BudgetAlertMessage(
        String type,
        int threshold,
        BigDecimal currentSpending,
        BigDecimal budgetAmount,
        String yearMonth) {
    public static BudgetAlertMessage of(
            int threshold, BigDecimal currentSpending, BigDecimal budgetAmount, String yearMonth) {
        return new BudgetAlertMessage(
                "BUDGET_ALERT", threshold, currentSpending, budgetAmount, yearMonth);
    }
}
