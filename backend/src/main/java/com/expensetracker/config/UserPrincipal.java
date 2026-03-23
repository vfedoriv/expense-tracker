package com.expensetracker.config;

import java.security.Principal;

public class UserPrincipal implements Principal {

    private final Long userId;
    private final String email;
    private final String displayName;

    public UserPrincipal(Long userId, String email, String displayName) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
