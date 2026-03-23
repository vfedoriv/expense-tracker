package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BudgetResponse(
    Long id,
    Integer year,
    Integer month,
    BigDecimal amount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
