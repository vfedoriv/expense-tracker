package com.expensetracker.config;

import com.expensetracker.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * Wraps an OAuth2User with the local database User entity.
 * This allows controllers to access both OAuth2 attributes and local user data.
 */
public class OAuth2UserWithLocalUser implements OAuth2User {

    private final OAuth2User delegate;
    private final User localUser;

    public OAuth2UserWithLocalUser(OAuth2User delegate, User localUser) {
        this.delegate = delegate;
        this.localUser = localUser;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    public User getLocalUser() {
        return localUser;
    }

    public Long getLocalUserId() {
        return localUser.getId();
    }
}
