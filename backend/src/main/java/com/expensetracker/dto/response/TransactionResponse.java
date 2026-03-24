package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long id,
        String title,
        BigDecimal amount,
        String currency,
        LocalDate transactionDate,
        Long categoryId,
        String categoryName,
        String notes,
        OffsetDateTime createdAt) {}
