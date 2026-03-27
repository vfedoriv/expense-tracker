package com.expensetracker.security;

import com.expensetracker.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Unified principal for both fake auth and OAuth2 (Google OIDC + GitHub OAuth2).
 * Implements OidcUser so it can be returned from CustomOidcUserService for Google,
 * and OAuth2User so it works with GitHub's non-OIDC flow.
 */
public class UserPrincipal implements Principal, OAuth2User, OidcUser {

    private final User user;
    private final OidcUser oidcDelegate; // non-null only for OIDC providers (Google)

    /** Used for GitHub (non-OIDC) and fake auth. */
    public UserPrincipal(User user) {
        this.user = user;
        this.oidcDelegate = null;
    }

    /** Used for Google (OIDC). Delegates OIDC-specific methods to the default OidcUser. */
    public UserPrincipal(User user, OidcUser oidcDelegate) {
        this.user = user;
        this.oidcDelegate = oidcDelegate;
    }

    public User user() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    // --- Principal ---

    @Override
    public String getName() {
        return user.getId().toString();
    }

    // --- OAuth2User ---

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

    // --- OidcUser (delegated to oidcDelegate when present) ---

    @Override
    public Map<String, Object> getClaims() {
        return oidcDelegate != null ? oidcDelegate.getClaims() : Map.of();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcDelegate != null ? oidcDelegate.getUserInfo() : null;
    }

    @Override
    public OidcIdToken getIdToken() {
        return oidcDelegate != null ? oidcDelegate.getIdToken() : null;
    }
}
