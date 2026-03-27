package com.expensetracker.security;

import com.expensetracker.user.User;
import com.expensetracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String provider = registrationId;
        String providerUserId;
        String email;
        String displayName;
        String avatarUrl;

        if ("github".equals(registrationId)) {
            providerUserId = String.valueOf(attributes.get("id"));
            email = (String) attributes.get("email");
            displayName = (String) attributes.getOrDefault("name", attributes.get("login"));
            avatarUrl = (String) attributes.get("avatar_url");
        } else {
            // Google (and others)
            providerUserId = (String) attributes.get("sub");
            email = (String) attributes.get("email");
            displayName = (String) attributes.getOrDefault("name", email);
            avatarUrl = (String) attributes.get("picture");
        }

        User user = findOrCreateUser(provider, providerUserId, email, displayName, avatarUrl);

        // Return UserPrincipal which implements OAuth2User — works with @AuthenticationPrincipal in all controllers
        return new UserPrincipal(user);
    }

    /** Exposed for test stubs that don't go through the real OAuth2 HTTP flow. */
    public User findOrCreateUser(String provider, String providerUserId, String email, String displayName, String avatarUrl) {
        return userService.findOrCreateByOAuth2(provider, providerUserId, email, displayName, avatarUrl);
    }
}
