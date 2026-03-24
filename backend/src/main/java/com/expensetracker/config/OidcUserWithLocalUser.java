package com.expensetracker.config;

import com.expensetracker.entity.User;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Wraps an OidcUser (Google) with the local database User entity. */
public class OidcUserWithLocalUser implements OidcUser {

    private final OidcUser delegate;
    private final User localUser;

    public OidcUserWithLocalUser(OidcUser delegate, User localUser) {
        this.delegate = delegate;
        this.localUser = localUser;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
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
