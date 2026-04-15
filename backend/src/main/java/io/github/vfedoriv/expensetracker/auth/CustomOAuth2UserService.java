package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String providerUserId = extractProviderUserId(oAuth2User, registrationId);

        User user = userRepository.findByProviderAndProviderUserId(registrationId, providerUserId)
                .orElseGet(() -> createUser(registrationId, providerUserId, oAuth2User));

        updateUserProfile(user, oAuth2User, registrationId);

        return new OAuth2UserPrincipal(oAuth2User, user);
    }

    private String extractProviderUserId(OAuth2User oAuth2User, String registrationId) {
        if ("github".equals(registrationId)) {
            return String.valueOf(oAuth2User.getAttributes().get("id"));
        }
        return oAuth2User.getAttribute("sub");
    }

    private User createUser(String provider, String providerUserId, OAuth2User oAuth2User) {
        User user = User.builder()
                .provider(provider)
                .providerUserId(providerUserId)
                .email(oAuth2User.getAttribute("email"))
                .displayName(extractDisplayName(oAuth2User))
                .avatarUrl(extractAvatarUrl(oAuth2User))
                .build();
        return userRepository.save(user);
    }

    private void updateUserProfile(User user, OAuth2User oAuth2User, String provider) {
        boolean updated = false;
        String email = oAuth2User.getAttribute("email");
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            updated = true;
        }
        String name = extractDisplayName(oAuth2User);
        if (!name.equals(user.getDisplayName())) {
            user.setDisplayName(name);
            updated = true;
        }
        String avatar = extractAvatarUrl(oAuth2User);
        if (avatar != null && !avatar.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(avatar);
            updated = true;
        }
        if (updated) {
            userRepository.save(user);
        }
    }

    private String extractDisplayName(OAuth2User oAuth2User) {
        String name = oAuth2User.getAttribute("name");
        if (name != null) return name;
        String login = oAuth2User.getAttribute("login");
        if (login != null) return login;
        return "Unknown";
    }

    private String extractAvatarUrl(OAuth2User oAuth2User) {
        String url = oAuth2User.getAttribute("picture");
        if (url != null) return url;
        return oAuth2User.getAttribute("avatar_url");
    }
}
