package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.TestcontainersConfig;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the OAuth login user-lookup path.
 *
 * <p>Since the app runs in {@code fake} auth mode during tests (per application-test.yml),
 * these tests exercise the {@link UserRepository} layer that both fake auth and real SSO
 * share: creating users with provider identity, looking them up by
 * {@code (provider, provider_user_id)}, and enforcing the UNIQUE constraint.
 *
 * <p>When {@code CustomOAuth2UserService} (GitHub) and {@code CustomOidcUserService} (Google)
 * are implemented in Phase 2, they will delegate to the same repository methods tested here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OAuthLoginIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        // DirtiesContext resets the context, but we also ensure a clean user table
        // (the FakeAuthFilter/FakeUserContext may have inserted the fake user on startup).
        userRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // GitHub provider path
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GitHub provider user lookup")
    class GitHubProvider {

        @Test
        @DisplayName("Should persist a new GitHub user and retrieve it by provider identity")
        void createAndFindGitHubUser() {
            User githubUser = User.builder()
                    .provider("github")
                    .providerUserId("gh-12345")
                    .email("octocat@github.com")
                    .displayName("Octocat")
                    .avatarUrl("https://avatars.githubusercontent.com/u/12345")
                    .build();

            User saved = userRepository.save(githubUser);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();

            Optional<User> found = userRepository.findByProviderAndProviderUserId("github", "gh-12345");

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
            assertThat(found.get().getProvider()).isEqualTo("github");
            assertThat(found.get().getProviderUserId()).isEqualTo("gh-12345");
            assertThat(found.get().getEmail()).isEqualTo("octocat@github.com");
            assertThat(found.get().getDisplayName()).isEqualTo("Octocat");
            assertThat(found.get().getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345");
        }

        @Test
        @DisplayName("Should allow GitHub user with null email (GitHub may not provide email)")
        void createGitHubUserWithoutEmail() {
            User githubUser = User.builder()
                    .provider("github")
                    .providerUserId("gh-99999")
                    .email(null)
                    .displayName("NoEmail User")
                    .build();

            User saved = userRepository.save(githubUser);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getEmail()).isNull();

            Optional<User> found = userRepository.findByProviderAndProviderUserId("github", "gh-99999");
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isNull();
        }

        @Test
        @DisplayName("Should return empty when GitHub provider_user_id does not exist")
        void findNonExistentGitHubUser() {
            Optional<User> found = userRepository.findByProviderAndProviderUserId("github", "gh-does-not-exist");
            assertThat(found).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Google provider path
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Google provider user lookup")
    class GoogleProvider {

        @Test
        @DisplayName("Should persist a new Google user and retrieve it by provider identity")
        void createAndFindGoogleUser() {
            User googleUser = User.builder()
                    .provider("google")
                    .providerUserId("google-sub-abc123")
                    .email("user@gmail.com")
                    .displayName("Google User")
                    .avatarUrl("https://lh3.googleusercontent.com/a/photo")
                    .build();

            User saved = userRepository.save(googleUser);

            assertThat(saved.getId()).isNotNull();

            Optional<User> found = userRepository.findByProviderAndProviderUserId("google", "google-sub-abc123");

            assertThat(found).isPresent();
            assertThat(found.get().getProvider()).isEqualTo("google");
            assertThat(found.get().getProviderUserId()).isEqualTo("google-sub-abc123");
            assertThat(found.get().getEmail()).isEqualTo("user@gmail.com");
            assertThat(found.get().getDisplayName()).isEqualTo("Google User");
        }

        @Test
        @DisplayName("Should return empty when Google provider_user_id does not exist")
        void findNonExistentGoogleUser() {
            Optional<User> found = userRepository.findByProviderAndProviderUserId("google", "google-does-not-exist");
            assertThat(found).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Cross-provider isolation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Cross-provider isolation")
    class CrossProviderIsolation {

        @Test
        @DisplayName("Same provider_user_id under different providers should be distinct users")
        void sameProviderUserIdDifferentProviders() {
            User githubUser = User.builder()
                    .provider("github")
                    .providerUserId("shared-id-42")
                    .email("github@example.com")
                    .displayName("GitHub User")
                    .build();
            userRepository.save(githubUser);

            User googleUser = User.builder()
                    .provider("google")
                    .providerUserId("shared-id-42")
                    .email("google@example.com")
                    .displayName("Google User")
                    .build();
            userRepository.save(googleUser);

            Optional<User> foundGithub = userRepository.findByProviderAndProviderUserId("github", "shared-id-42");
            Optional<User> foundGoogle = userRepository.findByProviderAndProviderUserId("google", "shared-id-42");

            assertThat(foundGithub).isPresent();
            assertThat(foundGoogle).isPresent();
            assertThat(foundGithub.get().getId()).isNotEqualTo(foundGoogle.get().getId());
            assertThat(foundGithub.get().getEmail()).isEqualTo("github@example.com");
            assertThat(foundGoogle.get().getEmail()).isEqualTo("google@example.com");
        }

        @Test
        @DisplayName("Lookup with wrong provider should not find user from another provider")
        void lookupWithWrongProvider() {
            User githubUser = User.builder()
                    .provider("github")
                    .providerUserId("only-on-github")
                    .email("dev@example.com")
                    .displayName("Dev")
                    .build();
            userRepository.save(githubUser);

            Optional<User> notFound = userRepository.findByProviderAndProviderUserId("google", "only-on-github");
            assertThat(notFound).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // UNIQUE constraint on (provider, provider_user_id)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Unique constraint enforcement")
    class UniqueConstraintEnforcement {

        @Test
        @DisplayName("Inserting duplicate (provider, provider_user_id) should throw DataIntegrityViolationException")
        void duplicateProviderAndProviderUserIdShouldFail() {
            User first = User.builder()
                    .provider("github")
                    .providerUserId("gh-duplicate")
                    .email("first@example.com")
                    .displayName("First User")
                    .build();
            userRepository.saveAndFlush(first);

            User duplicate = User.builder()
                    .provider("github")
                    .providerUserId("gh-duplicate")
                    .email("second@example.com")
                    .displayName("Second User")
                    .build();

            assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Same provider_user_id with different provider should not violate unique constraint")
        void sameProviderUserIdDifferentProviderShouldSucceed() {
            User githubUser = User.builder()
                    .provider("github")
                    .providerUserId("same-ext-id")
                    .email("gh@example.com")
                    .displayName("GH User")
                    .build();
            userRepository.saveAndFlush(githubUser);

            User googleUser = User.builder()
                    .provider("google")
                    .providerUserId("same-ext-id")
                    .email("ggl@example.com")
                    .displayName("Google User")
                    .build();
            User savedGoogle = userRepository.saveAndFlush(googleUser);

            assertThat(savedGoogle.getId()).isNotNull();
            assertThat(userRepository.count()).isEqualTo(2);
        }
    }

    // -----------------------------------------------------------------------
    // Upsert / find-or-create pattern (mirrors CustomOAuth2UserService logic)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Find-or-create pattern (OAuth login simulation)")
    class FindOrCreate {

        @Test
        @DisplayName("First login: user does not exist, should be created")
        void firstLoginCreatesUser() {
            String provider = "github";
            String providerUserId = "gh-first-login";

            Optional<User> existing = userRepository.findByProviderAndProviderUserId(provider, providerUserId);
            assertThat(existing).isEmpty();

            // Simulate what CustomOAuth2UserService.loadUser will do
            User newUser = existing.orElseGet(() -> userRepository.save(User.builder()
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .email("new@github.com")
                    .displayName("New GitHub User")
                    .build()));

            assertThat(newUser.getId()).isNotNull();
            assertThat(newUser.getProvider()).isEqualTo("github");
            assertThat(newUser.getProviderUserId()).isEqualTo("gh-first-login");

            // Verify it persisted
            assertThat(userRepository.findByProviderAndProviderUserId(provider, providerUserId)).isPresent();
        }

        @Test
        @DisplayName("Returning login: user already exists, should be reused (not duplicated)")
        void returningLoginReusesExistingUser() {
            String provider = "google";
            String providerUserId = "google-returning-user";

            // First login
            User created = userRepository.save(User.builder()
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .email("returning@gmail.com")
                    .displayName("Returning User")
                    .build());

            long countAfterFirstLogin = userRepository.count();

            // Second login - simulate what CustomOidcUserService.loadUser will do
            User reused = userRepository.findByProviderAndProviderUserId(provider, providerUserId)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .provider(provider)
                            .providerUserId(providerUserId)
                            .email("returning@gmail.com")
                            .displayName("Returning User")
                            .build()));

            assertThat(reused.getId()).isEqualTo(created.getId());
            assertThat(userRepository.count()).isEqualTo(countAfterFirstLogin);
        }

        @Test
        @DisplayName("Multiple providers for the same real person create separate user records")
        void samePersonDifferentProviders() {
            // Login via GitHub
            User githubLogin = userRepository.save(User.builder()
                    .provider("github")
                    .providerUserId("gh-person-1")
                    .email("person@example.com")
                    .displayName("Person")
                    .build());

            // Login via Google (same email, but different provider identity)
            User googleLogin = userRepository.save(User.builder()
                    .provider("google")
                    .providerUserId("google-person-1")
                    .email("person@example.com")
                    .displayName("Person")
                    .build());

            assertThat(githubLogin.getId()).isNotEqualTo(googleLogin.getId());
            assertThat(userRepository.count()).isEqualTo(2);

            // Each lookup returns the correct record
            assertThat(userRepository.findByProviderAndProviderUserId("github", "gh-person-1"))
                    .isPresent()
                    .hasValueSatisfying(u -> assertThat(u.getId()).isEqualTo(githubLogin.getId()));

            assertThat(userRepository.findByProviderAndProviderUserId("google", "google-person-1"))
                    .isPresent()
                    .hasValueSatisfying(u -> assertThat(u.getId()).isEqualTo(googleLogin.getId()));
        }
    }
}
