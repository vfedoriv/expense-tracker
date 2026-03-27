package com.expensetracker.transaction.dto;

import com.expensetracker.transaction.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
    Long id,
    String title,
    BigDecimal amount,
    String currency,
    LocalDate transactionDate,
    Long categoryId,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
            t.getId(),
            t.getTitle(),
            t.getAmount(),
            t.getCurrency(),
            t.getTransactionDate(),
            t.getCategoryId(),
            t.getNotes(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }
}
