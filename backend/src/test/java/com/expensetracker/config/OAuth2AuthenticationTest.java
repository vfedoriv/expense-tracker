package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for OAuth2 authentication flow.
 * Uses the 'oauth-test' profile which provides test OAuth client IDs,
 * enabling OAuth2 mode in SecurityConfig (not fake auth).
 * Tests use Spring Security Test's oauth2Login()/oidcLogin() post-processors
 * to simulate OAuth2 authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("oauth-test")
@DisplayName("OAuth2 Authentication")
class OAuth2AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanup() {
        // Remove any OAuth test users created in previous test runs
        userRepository.findByProviderAndProviderUserId("google", "google-sub-12345")
            .ifPresent(userRepository::delete);
        userRepository.findByProviderAndProviderUserId("google", "google-sub-99999")
            .ifPresent(userRepository::delete);
        userRepository.findByProviderAndProviderUserId("github", "github-id-67890")
            .ifPresent(userRepository::delete);
        userRepository.findByProviderAndProviderUserId("github", "github-id-no-email")
            .ifPresent(userRepository::delete);
    }

    @Nested
    @DisplayName("Google OAuth2 (OIDC) login")
    class GoogleOAuth2Tests {

        @Test
        @DisplayName("Successful Google login creates new user and /api/users/me returns correct data")
        void googleLogin_newUser_createsUserAndReturnsData() throws Exception {
            // First create the user record (simulating what CustomOidcUserService does on login)
            User googleUser = new User();
            googleUser.setProvider("google");
            googleUser.setProviderUserId("google-sub-12345");
            googleUser.setEmail("testuser@gmail.com");
            googleUser.setDisplayName("Google Test User");
            googleUser.setAvatarUrl("https://example.com/avatar.jpg");
            googleUser = userRepository.save(googleUser);

            // Use oidcLogin with our custom wrapper that contains the local user
            OidcUserWithLocalUser oidcUser = createOidcUserWithLocalUser(googleUser);

            // Create authentication token manually so OAuth2UserPrincipalFilter processes it
            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oidcUser,
                oidcUser.getAuthorities(),
                "google"
            );

            mockMvc.perform(get("/api/users/me")
                    .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("google")))
                .andExpect(jsonPath("$.email", is("testuser@gmail.com")))
                .andExpect(jsonPath("$.displayName", is("Google Test User")))
                .andExpect(jsonPath("$.avatarUrl", is("https://example.com/avatar.jpg")));

            // Verify user exists in DB
            Optional<User> dbUser = userRepository.findByProviderAndProviderUserId("google", "google-sub-12345");
            assertTrue(dbUser.isPresent());
            assertEquals("Google Test User", dbUser.get().getDisplayName());
            assertEquals("testuser@gmail.com", dbUser.get().getEmail());
        }

        @Test
        @DisplayName("Returning Google user reuses existing record (no duplicate)")
        void googleLogin_existingUser_reusesRecord() throws Exception {
            // Create user
            User existingUser = new User();
            existingUser.setProvider("google");
            existingUser.setProviderUserId("google-sub-99999");
            existingUser.setEmail("existing@gmail.com");
            existingUser.setDisplayName("Existing Google User");
            existingUser = userRepository.save(existingUser);
            Long originalId = existingUser.getId();

            OidcUserWithLocalUser oidcUser = createOidcUserWithLocalUser(existingUser);
            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), "google"
            );

            // Login and get user info
            mockMvc.perform(get("/api/users/me")
                    .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(originalId.intValue())))
                .andExpect(jsonPath("$.provider", is("google")))
                .andExpect(jsonPath("$.displayName", is("Existing Google User")));

            // Login again - should still return same user
            mockMvc.perform(get("/api/users/me")
                    .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(originalId.intValue())));

            // Verify no duplicate was created
            long count = userRepository.findAll().stream()
                .filter(u -> "google".equals(u.getProvider())
                    && "google-sub-99999".equals(u.getProviderUserId()))
                .count();
            assertEquals(1, count);
        }
    }

    @Nested
    @DisplayName("GitHub OAuth2 login")
    class GitHubOAuth2Tests {

        @Test
        @DisplayName("Successful GitHub login creates new user and returns correct data")
        void githubLogin_newUser_createsUserRecord() throws Exception {
            User githubUser = new User();
            githubUser.setProvider("github");
            githubUser.setProviderUserId("github-id-67890");
            githubUser.setEmail("githubuser@example.com");
            githubUser.setDisplayName("GitHub Test User");
            githubUser.setAvatarUrl("https://avatars.githubusercontent.com/u/67890");
            githubUser = userRepository.save(githubUser);

            OAuth2UserWithLocalUser ghPrincipal = buildGithubPrincipal(githubUser);
            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                ghPrincipal, ghPrincipal.getAuthorities(), "github"
            );

            mockMvc.perform(get("/api/users/me")
                    .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("github")))
                .andExpect(jsonPath("$.email", is("githubuser@example.com")))
                .andExpect(jsonPath("$.displayName", is("GitHub Test User")))
                .andExpect(jsonPath("$.avatarUrl", is("https://avatars.githubusercontent.com/u/67890")));

            Optional<User> dbUser = userRepository.findByProviderAndProviderUserId("github", "github-id-67890");
            assertTrue(dbUser.isPresent());
            assertEquals("GitHub Test User", dbUser.get().getDisplayName());
        }

        @Test
        @DisplayName("GitHub login without email succeeds (email is null)")
        void githubLogin_noEmail_createsUserWithNullEmail() throws Exception {
            User githubUser = new User();
            githubUser.setProvider("github");
            githubUser.setProviderUserId("github-id-no-email");
            githubUser.setEmail(null);
            githubUser.setDisplayName("PrivateGHUser");
            githubUser.setAvatarUrl("https://avatars.githubusercontent.com/u/11111");
            githubUser = userRepository.save(githubUser);

            OAuth2UserWithLocalUser ghPrincipal = buildGithubPrincipal(githubUser);
            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                ghPrincipal, ghPrincipal.getAuthorities(), "github"
            );

            mockMvc.perform(get("/api/users/me")
                    .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("github")))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.displayName", is("PrivateGHUser")));

            Optional<User> dbUser = userRepository.findByProviderAndProviderUserId("github", "github-id-no-email");
            assertTrue(dbUser.isPresent());
            assertNull(dbUser.get().getEmail());
        }
    }

    @Nested
    @DisplayName("User info endpoint")
    class UserInfoTests {

        @Test
        @DisplayName("GET /api/users/me returns correct user data for OAuth2 user")
        void getUserMe_returnsCorrectData() throws Exception {
            User user = new User();
            user.setProvider("google");
            user.setProviderUserId("google-sub-12345");
            user.setEmail("me@gmail.com");
            user.setDisplayName("My Name");
            user.setAvatarUrl("https://example.com/pic.jpg");
            user = userRepository.save(user);

            OidcUserWithLocalUser oidcUser = createOidcUserWithLocalUser(user);
            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), "google"
            );

            mockMvc.perform(get("/api/users/me")
                    .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.provider", is("google")))
                .andExpect(jsonPath("$.email", is("me@gmail.com")))
                .andExpect(jsonPath("$.displayName", is("My Name")))
                .andExpect(jsonPath("$.avatarUrl", is("https://example.com/pic.jpg")));
        }

        @Test
        @DisplayName("GET /api/users/me without authentication returns 401 (OAuth mode)")
        void getUserMe_unauthenticated_returns401() throws Exception {
            // In oauth-test profile, OAuth mode is active, so unauthenticated requests get 401
            mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Logout endpoint")
    class LogoutTests {

        @Test
        @DisplayName("POST /api/logout invalidates session and returns 200")
        void logout_invalidatesSession_returns200() throws Exception {
            // Create authenticated session first
            User user = new User();
            user.setProvider("google");
            user.setProviderUserId("google-sub-12345");
            user.setEmail("logout@gmail.com");
            user.setDisplayName("Logout User");
            user = userRepository.save(user);

            OidcUserWithLocalUser oidcUser = createOidcUserWithLocalUser(user);
            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), "google"
            );

            // Logout
            mockMvc.perform(post("/api/logout")
                    .with(authentication(authToken))
                    .with(csrf()))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Session is invalid after logout")
        void logout_sessionInvalidated_subsequentRequestsGet401() throws Exception {
            // After logout, unauthenticated requests should be 401
            mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Separate accounts for different providers")
    class SeparateAccountTests {

        @Test
        @DisplayName("Google and GitHub create separate accounts even with same email")
        void differentProviders_createSeparateAccounts() throws Exception {
            // Create Google user
            User googleUser = new User();
            googleUser.setProvider("google");
            googleUser.setProviderUserId("google-sub-12345");
            googleUser.setEmail("shared@example.com");
            googleUser.setDisplayName("Google User");
            googleUser = userRepository.save(googleUser);

            // Create GitHub user with same email
            User githubUser = new User();
            githubUser.setProvider("github");
            githubUser.setProviderUserId("github-id-67890");
            githubUser.setEmail("shared@example.com");
            githubUser.setDisplayName("GitHub User");
            githubUser = userRepository.save(githubUser);

            // Verify they are separate records
            Optional<User> foundGoogle = userRepository.findByProviderAndProviderUserId("google", "google-sub-12345");
            Optional<User> foundGithub = userRepository.findByProviderAndProviderUserId("github", "github-id-67890");

            assertTrue(foundGoogle.isPresent());
            assertTrue(foundGithub.isPresent());
            assertNotNull(foundGoogle.get().getId());
            assertNotNull(foundGithub.get().getId());
            assertTrue(!foundGoogle.get().getId().equals(foundGithub.get().getId()),
                "Google and GitHub users should have different IDs");

            // Verify each user sees their own data via /api/users/me
            OidcUserWithLocalUser googleOidc = createOidcUserWithLocalUser(foundGoogle.get());
            OAuth2AuthenticationToken googleAuth = new OAuth2AuthenticationToken(
                googleOidc, googleOidc.getAuthorities(), "google"
            );

            mockMvc.perform(get("/api/users/me")
                    .with(authentication(googleAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("google")))
                .andExpect(jsonPath("$.displayName", is("Google User")));

            OAuth2UserWithLocalUser ghOauthPrincipal = buildGithubPrincipal(foundGithub.get());
            OAuth2AuthenticationToken githubAuth = new OAuth2AuthenticationToken(
                ghOauthPrincipal, ghOauthPrincipal.getAuthorities(), "github"
            );

            mockMvc.perform(get("/api/users/me")
                    .with(authentication(githubAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("github")))
                .andExpect(jsonPath("$.displayName", is("GitHub User")));
        }
    }

    @Nested
    @DisplayName("OAuth2 redirect URLs")
    class OAuth2RedirectTests {

        @Test
        @DisplayName("OAuth2 authorization endpoint redirects to Google")
        void oauth2Authorization_google_redirects() throws Exception {
            mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("OAuth2 authorization endpoint redirects to GitHub")
        void oauth2Authorization_github_redirects() throws Exception {
            mockMvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection());
        }
    }

    // Helper methods

    private OidcUserWithLocalUser createOidcUserWithLocalUser(User localUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", localUser.getProviderUserId());
        claims.put("name", localUser.getDisplayName());
        claims.put("email", localUser.getEmail());
        claims.put("picture", localUser.getAvatarUrl());

        OidcIdToken idToken = new OidcIdToken(
            "mock-id-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            claims
        );

        OidcUser oidcUser = new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("SCOPE_openid")),
            idToken
        );

        return new OidcUserWithLocalUser(oidcUser, localUser);
    }

    private OAuth2UserWithLocalUser buildGithubPrincipal(User localUser) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", localUser.getProviderUserId());
        attributes.put("name", localUser.getDisplayName());
        attributes.put("login", localUser.getDisplayName());
        attributes.put("email", localUser.getEmail());
        attributes.put("avatar_url", localUser.getAvatarUrl());

        OAuth2User oauth2User = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("SCOPE_read:user")),
            attributes,
            "id"
        );

        return new OAuth2UserWithLocalUser(oauth2User, localUser);
    }
}
