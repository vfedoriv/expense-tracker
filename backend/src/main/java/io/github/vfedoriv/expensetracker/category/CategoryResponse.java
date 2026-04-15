package io.github.vfedoriv.expensetracker.category;

import java.time.LocalDateTime;

public record CategoryResponse(
    Long id,
    String name,
    LocalDateTime createdAt
) {}
