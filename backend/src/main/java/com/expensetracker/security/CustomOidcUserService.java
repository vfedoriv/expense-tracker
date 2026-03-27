package com.expensetracker.security;

import com.expensetracker.user.User;
import com.expensetracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserService userService;
    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        Map<String, Object> attributes = oidcUser.getAttributes();

        String providerUserId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String displayName = (String) attributes.getOrDefault("name", email);
        String avatarUrl = (String) attributes.get("picture");

        User user = userService.findOrCreateByOAuth2("google", providerUserId, email, displayName, avatarUrl);
        return new UserPrincipal(user, oidcUser);
    }
}
