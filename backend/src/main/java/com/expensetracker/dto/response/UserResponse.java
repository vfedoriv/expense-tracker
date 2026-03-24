package com.expensetracker.dto.response;

public record UserResponse(
        Long id, String provider, String email, String displayName, String avatarUrl) {}
