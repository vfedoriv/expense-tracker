package com.expensetracker.user.dto;

import com.expensetracker.user.User;

import java.time.Instant;

public record UserResponse(
    Long id,
    String provider,
    String providerUserId,
    String email,
    String displayName,
    String avatarUrl,
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getProvider(),
            user.getProviderUserId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getCreatedAt()
        );
    }
}
