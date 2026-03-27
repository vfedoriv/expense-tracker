package com.expensetracker.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionFilter(
    String search,
    Long categoryId,
    LocalDate dateFrom,
    LocalDate dateTo,
    BigDecimal amountMin,
    BigDecimal amountMax
) {}
