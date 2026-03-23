package com.expensetracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
    @NotBlank(message = "Title must not be blank")
    String title,

    @NotNull(message = "Amount must not be null")
    @Positive(message = "Amount must be greater than 0")
    BigDecimal amount,

    @NotNull(message = "Transaction date must not be null")
    LocalDate transactionDate,

    @NotNull(message = "Category ID must not be null")
    Long categoryId,

    String notes
) {
}
