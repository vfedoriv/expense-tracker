package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Custom OIDC UserService that handles Google login (OpenID Connect). Extracts provider +
 * provider_user_id (sub claim), checks if user exists in DB, and creates a new user record if not.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    public CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String providerUserId = oidcUser.getSubject();
        String fullName = oidcUser.getFullName();
        String displayName =
                (fullName != null && !fullName.isBlank())
                        ? fullName
                        : (oidcUser.getEmail() != null ? oidcUser.getEmail() : "Unknown User");
        String email = oidcUser.getEmail();
        String avatarUrl = oidcUser.getPicture();

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
                                    newUser.setAvatarUrl(
                                            avatarUrl != null ? avatarUrl.toString() : null);
                                    return userRepository.save(newUser);
                                });

        return new OidcUserWithLocalUser(oidcUser, user);
    }
}
