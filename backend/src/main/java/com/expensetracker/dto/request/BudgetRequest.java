package com.expensetracker.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BudgetRequest(
    @NotNull(message = "Year must not be null")
    Integer year,

    @NotNull(message = "Month must not be null")
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    Integer month,

    @NotNull(message = "Amount must not be null")
    @Positive(message = "Amount must be greater than 0")
    BigDecimal amount
) {
}
