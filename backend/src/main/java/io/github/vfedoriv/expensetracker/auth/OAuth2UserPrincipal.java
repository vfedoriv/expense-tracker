package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public class OAuth2UserPrincipal implements OAuth2User {

    private final OAuth2User delegate;
    private final User localUser;

    public OAuth2UserPrincipal(OAuth2User delegate, User localUser) {
        this.delegate = delegate;
        this.localUser = localUser;
    }

    public User getLocalUser() {
        return localUser;
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
        return String.valueOf(localUser.getId());
    }
}
