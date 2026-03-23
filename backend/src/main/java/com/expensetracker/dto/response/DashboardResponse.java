package com.expensetracker.dto.response;

import java.math.BigDecimal;

public record DashboardResponse(
    BigDecimal totalSpent,
    BigDecimal budgetAmount,
    BigDecimal remaining,
    BigDecimal usagePercentage
) {
}
