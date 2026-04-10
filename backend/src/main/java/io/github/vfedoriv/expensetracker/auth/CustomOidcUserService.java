package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String providerUserId = oidcUser.getSubject();

        User user = userRepository.findByProviderAndProviderUserId(registrationId, providerUserId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .provider(registrationId)
                            .providerUserId(providerUserId)
                            .email(oidcUser.getEmail())
                            .displayName(oidcUser.getFullName() != null ? oidcUser.getFullName() : "Unknown")
                            .avatarUrl(oidcUser.getPicture())
                            .build();
                    return userRepository.save(newUser);
                });

        // Update profile on each login
        boolean updated = false;
        if (oidcUser.getEmail() != null && !oidcUser.getEmail().equals(user.getEmail())) {
            user.setEmail(oidcUser.getEmail());
            updated = true;
        }
        if (oidcUser.getFullName() != null && !oidcUser.getFullName().equals(user.getDisplayName())) {
            user.setDisplayName(oidcUser.getFullName());
            updated = true;
        }
        if (oidcUser.getPicture() != null && !oidcUser.getPicture().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(oidcUser.getPicture());
            updated = true;
        }
        if (updated) {
            userRepository.save(user);
        }

        return new OidcUserPrincipal(oidcUser, user);
    }
}
