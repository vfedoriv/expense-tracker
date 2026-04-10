package io.github.vfedoriv.expensetracker.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
    Long id,
    String title,
    BigDecimal amount,
    String currency,
    Long categoryId,
    String categoryName,
    LocalDate transactionDate,
    String notes,
    LocalDateTime createdAt
) {}
