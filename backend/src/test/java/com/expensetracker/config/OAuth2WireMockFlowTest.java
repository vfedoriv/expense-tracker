package com.expensetracker.config;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

/**
 * WireMock-based OAuth2 integration tests that mock the full provider callback flow (token exchange
 * + userinfo endpoints). Uses a real embedded server and real HTTP client to simulate the full
 * OAuth2 authorization code flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock({
    @ConfigureWireMock(name = "oauth-provider", baseUrlProperties = "wiremock.oauth.url")
})
@ActiveProfiles("oauth-test")
@Import(WireMockOAuth2TestConfig.class)
@DisplayName("OAuth2 WireMock Flow Tests")
class OAuth2WireMockFlowTest {

    @InjectWireMock("oauth-provider")
    WireMockServer wireMock;

    @Value("${wiremock.oauth.url}")
    String wireMockUrl;

    @Value("${local.server.port}")
    int serverPort;

    @Autowired UserRepository userRepository;

    private String baseUrl;

    // RSA key pair for signing Google OIDC id_tokens
    private RSAPublicKey rsaPublicKey;
    private RSAPrivateKey rsaPrivateKey;
    private String rsaKeyId;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + serverPort;

        // Generate RSA key pair for OIDC id_token signing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
        rsaKeyId = UUID.randomUUID().toString();

        // Clean up test users
        cleanupTestUser("github", "67890");
        cleanupTestUser("google", "google-12345");
        cleanupTestUser("github", "99901");
        cleanupTestUser("google", "shared-google-user");

        // Reset WireMock stubs
        wireMock.resetAll();
    }

    private void cleanupTestUser(String provider, String providerUserId) {
        userRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName(
            "GitHub full OAuth2 callback flow: token exchange, userinfo, user provisioned in DB")
    void githubFullOAuth2CallbackFlow() throws Exception {
        // Stub GitHub token endpoint
        wireMock.stubFor(
                post(urlPathEqualTo("/login/oauth/access_token"))
                        .willReturn(
                                okJson(
                                        """
                {
                    "access_token": "gho_mock_access_token_12345",
                    "token_type": "bearer",
                    "scope": "read:user,user:email"
                }
                """)));

        // Stub GitHub user info endpoint
        wireMock.stubFor(
                get(urlPathEqualTo("/api/user"))
                        .willReturn(
                                okJson(
                                        """
                {
                    "id": 67890,
                    "login": "wiremock-gh-user",
                    "name": "WireMock GitHub User",
                    "email": "wm@github.com",
                    "avatar_url": "https://example.com/gh.jpg"
                }
                """)));

        // No user should exist initially
        assertTrue(
                userRepository.findByProviderAndProviderUserId("github", "67890").isEmpty(),
                "No GitHub user should exist before the flow");

        // Execute the full OAuth2 callback flow
        HttpClient client = createHttpClient();
        String sessionCookie = executeOAuth2Flow(client, "github");

        // Verify user was provisioned in the DB
        Optional<User> dbUser = userRepository.findByProviderAndProviderUserId("github", "67890");
        assertTrue(dbUser.isPresent(), "GitHub user should be created in DB after callback flow");
        assertEquals("github", dbUser.get().getProvider());
        assertEquals("67890", dbUser.get().getProviderUserId());
        assertEquals("WireMock GitHub User", dbUser.get().getDisplayName());
        assertEquals("wm@github.com", dbUser.get().getEmail());
        assertEquals("https://example.com/gh.jpg", dbUser.get().getAvatarUrl());

        // Verify /api/users/me returns the provisioned user using the session
        assertUserMeEndpoint(
                client, sessionCookie, "github", "WireMock GitHub User", "wm@github.com");

        // Verify WireMock stubs were called
        wireMock.verify(postRequestedFor(urlPathEqualTo("/login/oauth/access_token")));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/user")));
    }

    @Test
    @DisplayName(
            "Google full OIDC callback flow: token exchange with id_token, userinfo, user provisioned in DB")
    void googleFullOidcCallbackFlow() throws Exception {
        // No user should exist initially
        assertTrue(
                userRepository.findByProviderAndProviderUserId("google", "google-12345").isEmpty(),
                "No Google user should exist before the flow");

        // Execute Google OIDC flow using helper
        HttpClient client = createHttpClient();
        String sessionCookie =
                executeGoogleOidcFlow(
                        client,
                        "google-12345",
                        "wm@google.com",
                        "WireMock Google User",
                        "https://example.com/g.jpg");

        // Verify user was provisioned in the DB
        Optional<User> dbUser =
                userRepository.findByProviderAndProviderUserId("google", "google-12345");
        assertTrue(dbUser.isPresent(), "Google user should be created in DB after callback flow");
        assertEquals("google", dbUser.get().getProvider());
        assertEquals("google-12345", dbUser.get().getProviderUserId());
        assertEquals("WireMock Google User", dbUser.get().getDisplayName());
        assertEquals("wm@google.com", dbUser.get().getEmail());

        // Verify /api/users/me returns the provisioned user using the session
        assertUserMeEndpoint(
                client, sessionCookie, "google", "WireMock Google User", "wm@google.com");

        // Verify WireMock stubs were called
        wireMock.verify(postRequestedFor(urlPathEqualTo("/oauth2/v4/token")));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/oauth2/v3/userinfo")));
    }

    @Test
    @DisplayName("First login creates user, second login reuses same record (GitHub)")
    void firstLoginCreatesUser_secondLoginReusesSameRecord() throws Exception {
        // Stub GitHub endpoints
        stubGitHubEndpoints(
                67890,
                "wiremock-gh-user",
                "WireMock GitHub User",
                "wm@github.com",
                "https://example.com/gh.jpg");

        // First login
        HttpClient client1 = createHttpClient();
        String session1 = executeOAuth2Flow(client1, "github");

        Optional<User> userAfterFirst =
                userRepository.findByProviderAndProviderUserId("github", "67890");
        assertTrue(userAfterFirst.isPresent(), "User should exist after first login");
        Long firstUserId = userAfterFirst.get().getId();

        // Verify first login via /api/users/me
        String meResponse1 = getUserMe(client1, session1);
        assertTrue(meResponse1.contains("\"provider\":\"github\""));

        // Reset WireMock for second login (re-stub)
        wireMock.resetAll();
        stubGitHubEndpoints(
                67890,
                "wiremock-gh-user",
                "WireMock GitHub User",
                "wm@github.com",
                "https://example.com/gh.jpg");

        // Second login
        HttpClient client2 = createHttpClient();
        String session2 = executeOAuth2Flow(client2, "github");

        Optional<User> userAfterSecond =
                userRepository.findByProviderAndProviderUserId("github", "67890");
        assertTrue(userAfterSecond.isPresent(), "User should still exist after second login");
        Long secondUserId = userAfterSecond.get().getId();

        // Verify same user ID
        assertEquals(
                firstUserId,
                secondUserId,
                "Second login should reuse the same user record, not create a duplicate");

        // Verify only one user record with this providerUserId
        long count =
                userRepository.findAll().stream()
                        .filter(
                                u ->
                                        "github".equals(u.getProvider())
                                                && "67890".equals(u.getProviderUserId()))
                        .count();
        assertEquals(
                1,
                count,
                "DB should have exactly one user record with provider=github, providerUserId=67890");

        // Verify second login via /api/users/me
        String meResponse2 = getUserMe(client2, session2);
        assertTrue(meResponse2.contains("\"provider\":\"github\""));
    }

    @Test
    @DisplayName("Different providers with same email create separate accounts")
    void differentProviders_sameEmail_createSeparateAccounts() throws Exception {
        // Step 1: GitHub flow with shared email (use numeric ID as GitHub returns numeric ids)
        stubGitHubEndpoints(
                99901,
                "shared-gh-login",
                "Shared GitHub User",
                "shared@example.com",
                "https://example.com/gh-shared.jpg");

        HttpClient ghClient = createHttpClient();
        String ghSession = executeOAuth2Flow(ghClient, "github");

        Optional<User> githubUser =
                userRepository.findByProviderAndProviderUserId("github", "99901");
        assertTrue(githubUser.isPresent(), "GitHub user should be created");
        assertEquals("shared@example.com", githubUser.get().getEmail());

        // Step 2: Reset WireMock and do Google flow with same email
        wireMock.resetAll();

        HttpClient googleClient = createHttpClient();
        String googleSession =
                executeGoogleOidcFlow(
                        googleClient,
                        "shared-google-user",
                        "shared@example.com",
                        "Shared Google User",
                        "https://example.com/g-shared.jpg");

        Optional<User> googleUser =
                userRepository.findByProviderAndProviderUserId("google", "shared-google-user");
        assertTrue(googleUser.isPresent(), "Google user should be created");
        assertEquals("shared@example.com", googleUser.get().getEmail());

        // Verify two distinct user records exist
        assertNotEquals(
                githubUser.get().getId(),
                googleUser.get().getId(),
                "GitHub and Google users with same email should be separate accounts");

        // Verify each session sees the correct user
        String ghMe = getUserMe(ghClient, ghSession);
        assertTrue(ghMe.contains("\"provider\":\"github\""));

        String googleMe = getUserMe(googleClient, googleSession);
        assertTrue(googleMe.contains("\"provider\":\"google\""));
    }

    // ========== Helper Methods ==========

    /**
     * Executes the full Google OIDC flow with proper nonce handling. Must be called with a fresh
     * (no stubs) or reset WireMock. Sets up stubs after capturing the nonce from the authorization
     * redirect.
     */
    private String executeGoogleOidcFlow(
            HttpClient client, String subject, String email, String name, String picture)
            throws Exception {
        // Step 1: Stub only the JWKS endpoint (needed during token validation)
        wireMock.stubFor(
                get(urlPathEqualTo("/oauth2/v3/certs")).willReturn(okJson(createJwksJson())));

        // Stub userinfo endpoint
        wireMock.stubFor(
                get(urlPathEqualTo("/oauth2/v3/userinfo"))
                        .willReturn(
                                okJson(
                                        String.format(
                                                """
                {
                    "sub": "%s",
                    "email": "%s",
                    "name": "%s",
                    "picture": "%s"
                }
                """,
                                                subject, email, name, picture))));

        // Step 2: Initiate the auth flow to capture state and nonce
        HttpRequest authRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/oauth2/authorization/google"))
                        .GET()
                        .build();

        HttpResponse<String> authResponse =
                client.send(authRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(
                302,
                authResponse.statusCode(),
                "Google OAuth2 authorization should redirect (302)");

        String location =
                authResponse
                        .headers()
                        .firstValue("Location")
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "No Location header on Google authorization redirect"));

        String state = extractQueryParam(location, "state");
        assertNotNull(state, "State parameter should be present");

        String nonce = extractQueryParam(location, "nonce");
        // nonce may or may not be present depending on Spring Security config

        // Step 3: Now create the id_token with the captured nonce and stub the token endpoint
        String idToken = createSignedIdToken(subject, email, name, picture, nonce);

        wireMock.stubFor(
                post(urlPathEqualTo("/oauth2/v4/token"))
                        .willReturn(
                                okJson(
                                        String.format(
                                                """
                {
                    "access_token": "ya29.mock_access_token_%s",
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "scope": "openid profile email",
                    "id_token": "%s"
                }
                """,
                                                subject, idToken))));

        // Step 4: Send the callback with the authorization code
        HttpRequest callbackRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        baseUrl
                                                + "/login/oauth2/code/google"
                                                + "?code=mock-auth-code&state="
                                                + state))
                        .GET()
                        .build();

        HttpResponse<String> callbackResponse =
                client.send(callbackRequest, HttpResponse.BodyHandlers.ofString());
        assertTrue(
                callbackResponse.statusCode() == 302 || callbackResponse.statusCode() == 200,
                "Google callback should succeed, got: "
                        + callbackResponse.statusCode()
                        + " body: "
                        + callbackResponse.body());

        return extractSessionCookie(client);
    }

    /** Creates an HttpClient with cookie management but no automatic redirect following. */
    private HttpClient createHttpClient() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cookieManager)
                .build();
    }

    /**
     * Executes the full OAuth2 authorization code flow for the given provider. Returns the session
     * cookie value for subsequent authenticated requests.
     */
    private String executeOAuth2Flow(HttpClient client, String provider) throws Exception {
        return executeOAuth2FlowInternal(client, provider, null);
    }

    /**
     * Executes the full OAuth2 authorization code flow with optional nonce consumer. For OIDC
     * providers (Google), captures the nonce from the auth redirect to include in the WireMock
     * token stub.
     */
    private String executeOAuth2FlowInternal(
            HttpClient client, String provider, java.util.function.Consumer<String> nonceConsumer)
            throws Exception {
        // Step 1: GET /oauth2/authorization/{provider} -> 302 redirect to provider auth URL
        HttpRequest authRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/oauth2/authorization/" + provider))
                        .GET()
                        .build();

        HttpResponse<String> authResponse =
                client.send(authRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(
                302,
                authResponse.statusCode(),
                "OAuth2 authorization should redirect (302) to provider");

        String location =
                authResponse
                        .headers()
                        .firstValue("Location")
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "No Location header on authorization redirect"));

        // Extract state parameter from the redirect URL
        String state = extractQueryParam(location, "state");
        assertNotNull(state, "State parameter should be present in authorization redirect URL");

        // Extract nonce for OIDC providers (needed in id_token)
        String nonce = extractQueryParam(location, "nonce");
        if (nonceConsumer != null && nonce != null) {
            nonceConsumer.accept(nonce);
        }

        // Step 2: GET /login/oauth2/code/{provider}?code=mock-auth-code&state=<captured>
        // This simulates the OAuth2 provider redirecting back with an authorization code
        HttpRequest callbackRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        baseUrl
                                                + "/login/oauth2/code/"
                                                + provider
                                                + "?code=mock-auth-code&state="
                                                + state))
                        .GET()
                        .build();

        HttpResponse<String> callbackResponse =
                client.send(callbackRequest, HttpResponse.BodyHandlers.ofString());

        // The callback should redirect (302) to the success URL
        assertTrue(
                callbackResponse.statusCode() == 302 || callbackResponse.statusCode() == 200,
                "Callback should succeed with redirect or OK, got: "
                        + callbackResponse.statusCode());

        // Extract session cookie from the cookie manager
        return extractSessionCookie(client);
    }

    /** Extracts JSESSIONID from the HttpClient's cookie manager. */
    private String extractSessionCookie(HttpClient client) throws Exception {
        CookieManager cm = (CookieManager) client.cookieHandler().orElseThrow();
        return cm.getCookieStore().getCookies().stream()
                .filter(c -> "JSESSIONID".equalsIgnoreCase(c.getName()))
                .findFirst()
                .map(c -> c.getValue())
                .orElseThrow(
                        () -> new AssertionError("No JSESSIONID cookie found after OAuth2 flow"));
    }

    /** Calls GET /api/users/me with the session cookie and verifies the response. */
    private void assertUserMeEndpoint(
            HttpClient client,
            String sessionCookie,
            String expectedProvider,
            String expectedDisplayName,
            String expectedEmail)
            throws Exception {
        String meResponse = getUserMe(client, sessionCookie);
        assertTrue(
                meResponse.contains("\"provider\":\"" + expectedProvider + "\""),
                "Response should contain provider=" + expectedProvider + ", got: " + meResponse);
        assertTrue(
                meResponse.contains("\"displayName\":\"" + expectedDisplayName + "\""),
                "Response should contain displayName="
                        + expectedDisplayName
                        + ", got: "
                        + meResponse);
        assertTrue(
                meResponse.contains("\"email\":\"" + expectedEmail + "\""),
                "Response should contain email=" + expectedEmail + ", got: " + meResponse);
    }

    /** Calls GET /api/users/me and returns the response body. */
    private String getUserMe(HttpClient client, String sessionCookie) throws Exception {
        HttpRequest meRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/users/me"))
                        .header("Cookie", "JSESSIONID=" + sessionCookie)
                        .GET()
                        .build();

        HttpResponse<String> meResponse =
                client.send(meRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(
                200,
                meResponse.statusCode(),
                "GET /api/users/me should return 200 with valid session, got: "
                        + meResponse.statusCode()
                        + " body: "
                        + meResponse.body());
        return meResponse.body();
    }

    /** Extracts a query parameter value from a URL. */
    private String extractQueryParam(String url, String param) {
        Pattern pattern = Pattern.compile("[?&]" + param + "=([^&]+)");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Stubs GitHub token and user info endpoints with a numeric user ID. */
    private void stubGitHubEndpoints(
            int userId, String login, String name, String email, String avatarUrl) {
        wireMock.stubFor(
                post(urlPathEqualTo("/login/oauth/access_token"))
                        .willReturn(
                                okJson(
                                        String.format(
                                                """
                {
                    "access_token": "gho_mock_access_token_%d",
                    "token_type": "bearer",
                    "scope": "read:user,user:email"
                }
                """,
                                                userId))));

        wireMock.stubFor(
                get(urlPathEqualTo("/api/user"))
                        .willReturn(
                                okJson(
                                        String.format(
                                                """
                {
                    "id": %d,
                    "login": "%s",
                    "name": "%s",
                    "email": "%s",
                    "avatar_url": "%s"
                }
                """,
                                                userId, login, name, email, avatarUrl))));
    }

    /**
     * Creates a signed JWT id_token for Google OIDC. Includes the nonce if provided (required for
     * OIDC nonce validation).
     */
    private String createSignedIdToken(
            String subject, String email, String name, String picture, String nonce)
            throws Exception {
        JWTClaimsSet.Builder claimsBuilder =
                new JWTClaimsSet.Builder()
                        .issuer(wireMockUrl)
                        .subject(subject)
                        .audience("test-google-client-id")
                        .claim("email", email)
                        .claim("name", name)
                        .claim("picture", picture)
                        .claim("at_hash", "mock_at_hash")
                        .issueTime(Date.from(Instant.now()))
                        .expirationTime(Date.from(Instant.now().plusSeconds(3600)));

        if (nonce != null) {
            claimsBuilder.claim("nonce", nonce);
        }

        JWTClaimsSet claims = claimsBuilder.build();

        RSAKey rsaKey =
                new RSAKey.Builder(rsaPublicKey).privateKey(rsaPrivateKey).keyID(rsaKeyId).build();

        JWSSigner signer = new RSASSASigner(rsaKey);
        SignedJWT signedJWT =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKeyId).build(), claims);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    /** Creates JWKS JSON containing the RSA public key for id_token verification. */
    private String createJwksJson() {
        RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey).keyID(rsaKeyId).build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return jwkSet.toString();
    }
}
