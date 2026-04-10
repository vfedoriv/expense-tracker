package io.github.vfedoriv.expensetracker.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
    @NotBlank(message = "Title is required")
    String title,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Size(max = 3, message = "Currency code must not exceed 3 characters")
    String currency,

    @NotNull(message = "Category is required")
    Long categoryId,

    @NotNull(message = "Transaction date is required")
    LocalDate transactionDate,

    String notes
) {}
