package com.expensetracker.category.dto;

import com.expensetracker.category.Category;

import java.time.Instant;

public record CategoryResponse(
    Long id,
    String name,
    Instant createdAt,
    Instant updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getCreatedAt(),
            category.getUpdatedAt()
        );
    }
}
