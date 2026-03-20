package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.user.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "oauth2")
public class OAuth2UserContext implements UserContext {

    @Override
    public Long getUserId() {
        return getLocalUser().getId();
    }

    @Override
    public String getEmail() {
        return getLocalUser().getEmail();
    }

    @Override
    public String getDisplayName() {
        return getLocalUser().getDisplayName();
    }

    @Override
    public AppUser getCurrentUser() {
        User user = getLocalUser();
        return new AppUser(user.getId(), user.getProvider(), user.getProviderUserId(),
                user.getEmail(), user.getDisplayName());
    }

    private User getLocalUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OAuth2User principal = (OAuth2User) auth.getPrincipal();
        if (principal instanceof OAuth2UserPrincipal p) {
            return p.getLocalUser();
        }
        if (principal instanceof OidcUserPrincipal p) {
            return p.getLocalUser();
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
    }
}
