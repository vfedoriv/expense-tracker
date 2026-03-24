package com.expensetracker.dto.response;

import java.time.OffsetDateTime;

public record CategoryResponse(Long id, String name, OffsetDateTime createdAt) {}
