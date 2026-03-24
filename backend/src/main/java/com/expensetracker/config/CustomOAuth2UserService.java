package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Custom OAuth2UserService that handles GitHub login. Extracts provider + provider_user_id, checks
 * if user exists in DB, and creates a new user record if not.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String providerUserId = extractProviderUserId(registrationId, oAuth2User);
        String displayName = extractDisplayName(registrationId, oAuth2User);
        String email = extractEmail(registrationId, oAuth2User);
        String avatarUrl = extractAvatarUrl(registrationId, oAuth2User);

        User user =
                userRepository
                        .findByProviderAndProviderUserId(registrationId, providerUserId)
                        .orElseGet(
                                () -> {
                                    User newUser = new User();
                                    newUser.setProvider(registrationId);
                                    newUser.setProviderUserId(providerUserId);
                                    newUser.setDisplayName(displayName);
                                    newUser.setEmail(email);
                                    newUser.setAvatarUrl(avatarUrl);
                                    return userRepository.save(newUser);
                                });

        return new OAuth2UserWithLocalUser(oAuth2User, user);
    }

    private String extractProviderUserId(String registrationId, OAuth2User oAuth2User) {
        if ("github".equals(registrationId)) {
            Object id = oAuth2User.getAttribute("id");
            return id != null ? String.valueOf(id) : oAuth2User.getName();
        }
        // Google uses 'sub' claim, which is the default name attribute
        String sub = oAuth2User.getAttribute("sub");
        return sub != null ? sub : oAuth2User.getName();
    }

    private String extractDisplayName(String registrationId, OAuth2User oAuth2User) {
        String name = oAuth2User.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        if ("github".equals(registrationId)) {
            String login = oAuth2User.getAttribute("login");
            if (login != null) {
                return login;
            }
        }
        return "Unknown User";
    }

    private String extractEmail(String registrationId, OAuth2User oAuth2User) {
        return oAuth2User.getAttribute("email");
    }

    private String extractAvatarUrl(String registrationId, OAuth2User oAuth2User) {
        if ("github".equals(registrationId)) {
            return oAuth2User.getAttribute("avatar_url");
        }
        return oAuth2User.getAttribute("picture");
    }
}
