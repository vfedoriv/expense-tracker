package com.expensetracker.security;

import com.expensetracker.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public record UserPrincipal(User user) implements Principal, OAuth2User {

    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    @Override
    public String getName() {
        return user.getId().toString();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of(
            "id", user.getId(),
            "email", user.getEmail() != null ? user.getEmail() : "",
            "name", user.getDisplayName()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
