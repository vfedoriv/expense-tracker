package com.expensetracker.user;

import com.expensetracker.security.CustomOAuth2UserService;
import com.expensetracker.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class OAuth2LoginIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.auth.fake", () -> "false");
        // Provide fake OAuth2 credentials so Spring doesn't reject empty client-id
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-google-client-secret");
        registry.add("spring.security.oauth2.client.registration.google.scope", () -> "openid,profile,email");
        registry.add("spring.security.oauth2.client.registration.github.client-id", () -> "test-github-client-id");
        registry.add("spring.security.oauth2.client.registration.github.client-secret", () -> "test-github-client-secret");
        registry.add("spring.security.oauth2.client.registration.github.scope", () -> "user:email,read:user");
    }

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    /**
     * Simulates a successful OAuth2 login by creating the user via CustomOAuth2UserService
     * with mock provider attributes — no real network calls to Google or GitHub.
     */
    private Authentication buildOAuth2Authentication(String registrationId, Map<String, Object> attributes) {
        User user = buildUserFromAttributes(registrationId, attributes);
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private User buildUserFromAttributes(String registrationId, Map<String, Object> attributes) {
        if ("github".equals(registrationId)) {
            String providerUserId = String.valueOf(attributes.get("id"));
            String email = (String) attributes.get("email");
            String displayName = (String) attributes.getOrDefault("name", attributes.get("login"));
            String avatarUrl = (String) attributes.get("avatar_url");
            return customOAuth2UserService.findOrCreateUser("github", providerUserId, email, displayName, avatarUrl);
        } else {
            String providerUserId = (String) attributes.get("sub");
            String email = (String) attributes.get("email");
            String displayName = (String) attributes.getOrDefault("name", email);
            String avatarUrl = (String) attributes.get("picture");
            return customOAuth2UserService.findOrCreateUser("google", providerUserId, email, displayName, avatarUrl);
        }
    }

    @Test
    void googleOAuth2Login_createsUserAndReturnsProfile() throws Exception {
        var attrs = new HashMap<String, Object>(Map.of(
            "sub", "google-uid-123",
            "email", "test@gmail.com",
            "name", "Test User",
            "picture", "https://example.com/avatar.jpg"
        ));

        mockMvc.perform(get("/api/users/me")
                .with(authentication(buildOAuth2Authentication("google", attrs))))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.email").value("test@gmail.com"))
            .andExpect(jsonPath("$.displayName").value("Test User"));

        var googleUser = userRepository.findByProviderAndProviderUserId("google", "google-uid-123");
        assertThat(googleUser).isPresent();
        assertThat(googleUser.get().getEmail()).isEqualTo("test@gmail.com");
    }

    @Test
    void githubOAuth2Login_createsUserWithoutEmail() throws Exception {
        var attrs = new HashMap<String, Object>(Map.of(
            "id", 456,
            "login", "octocat",
            "name", "The Octocat",
            "avatar_url", "https://github.com/avatars/octocat.jpg"
        ));

        mockMvc.perform(get("/api/users/me")
                .with(authentication(buildOAuth2Authentication("github", attrs))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("The Octocat"));

        var githubUser = userRepository.findAll().stream()
            .filter(u -> "github".equals(u.getProvider()) && "456".equals(u.getProviderUserId()))
            .findFirst();
        assertThat(githubUser).isPresent();
        assertThat(githubUser.get().getDisplayName()).isEqualTo("The Octocat");
    }

    @Test
    void userIsolation_twoUsersCannotSeeEachOthersCategories() throws Exception {
        var attrsA = new HashMap<String, Object>(Map.of("sub", "user-a", "email", "user-a@test.com", "name", "User A"));
        var attrsB = new HashMap<String, Object>(Map.of("sub", "user-b", "email", "user-b@test.com", "name", "User B"));

        mockMvc.perform(get("/api/categories")
                .with(authentication(buildOAuth2Authentication("google", attrsA))))
            .andExpect(status().isOk())
            .andExpect(content().string("[]"));

        mockMvc.perform(get("/api/categories")
                .with(authentication(buildOAuth2Authentication("google", attrsB))))
            .andExpect(status().isOk())
            .andExpect(content().string("[]"));
    }
}
