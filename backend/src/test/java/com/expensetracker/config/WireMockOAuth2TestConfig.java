package com.expensetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Test configuration that overrides the production ClientRegistrationRepository to point all OAuth2
 * URLs at the WireMock server.
 */
@TestConfiguration
public class WireMockOAuth2TestConfig {

    @Value("${wiremock.oauth.url}")
    private String wireMockUrl;

    @Bean("wireMockClientRegistrationRepository")
    @Primary
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(
                googleClientRegistration(), githubClientRegistration());
    }

    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-google-client-id")
                .clientSecret("test-google-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(wireMockUrl + "/o/oauth2/v2/auth")
                .tokenUri(wireMockUrl + "/oauth2/v4/token")
                .userInfoUri(wireMockUrl + "/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri(wireMockUrl + "/oauth2/v3/certs")
                .clientName("Google")
                .build();
    }

    private ClientRegistration githubClientRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId("test-github-client-id")
                .clientSecret("test-github-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri(wireMockUrl + "/login/oauth/authorize")
                .tokenUri(wireMockUrl + "/login/oauth/access_token")
                .userInfoUri(wireMockUrl + "/api/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
    }
}
