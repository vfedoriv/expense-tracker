package com.expensetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmatic OAuth2 client registration configuration.
 * Only creates registrations when OAuth environment variables are set.
 * This avoids Spring Boot's auto-configuration failing with empty client IDs.
 */
@Configuration
public class OAuth2ClientConfig {

    private final Environment environment;

    public OAuth2ClientConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        List<ClientRegistration> registrations = new ArrayList<>();

        String googleClientId = environment.getProperty("GOOGLE_CLIENT_ID", "");
        String googleClientSecret = environment.getProperty("GOOGLE_CLIENT_SECRET", "");
        if (!googleClientId.isBlank()) {
            registrations.add(googleClientRegistration(googleClientId, googleClientSecret));
        }

        String githubClientId = environment.getProperty("GITHUB_CLIENT_ID", "");
        String githubClientSecret = environment.getProperty("GITHUB_CLIENT_SECRET", "");
        if (!githubClientId.isBlank()) {
            registrations.add(githubClientRegistration(githubClientId, githubClientSecret));
        }

        if (registrations.isEmpty()) {
            // Return a repository with a placeholder to avoid NPE
            // This won't be used because SecurityConfig uses fake auth when no OAuth is configured
            return new InMemoryClientRegistrationRepository(placeholderRegistration());
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration googleClientRegistration(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://www.googleapis.com/oauth2/v4/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName("sub")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .clientName("Google")
            .build();
    }

    private ClientRegistration githubClientRegistration(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId("github")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("read:user", "user:email")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user")
            .userNameAttributeName("id")
            .clientName("GitHub")
            .build();
    }

    private ClientRegistration placeholderRegistration() {
        return ClientRegistration.withRegistrationId("placeholder")
            .clientId("placeholder")
            .clientSecret("placeholder")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://placeholder.example.com/auth")
            .tokenUri("https://placeholder.example.com/token")
            .clientName("Placeholder")
            .build();
    }
}
