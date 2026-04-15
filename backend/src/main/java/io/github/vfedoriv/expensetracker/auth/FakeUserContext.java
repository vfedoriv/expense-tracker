package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fake auth implementation for Phase 1 development.
 * Automatically creates/reuses a hardcoded user on startup.
 * Will be replaced by real SSO-backed UserContext in Phase 2.
 */
@Component
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "fake", matchIfMissing = true)
public class FakeUserContext implements UserContext {

    private static final String FAKE_PROVIDER = "fake";
    private static final String FAKE_PROVIDER_USER_ID = "fake-user-001";
    private static final String FAKE_EMAIL = "admin@test.com";
    private static final String FAKE_DISPLAY_NAME = "Test User";

    private final UserRepository userRepository;
    private Long userId;

    public FakeUserContext(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    void init() {
        User user = userRepository.findByProviderAndProviderUserId(FAKE_PROVIDER, FAKE_PROVIDER_USER_ID)
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(FAKE_PROVIDER)
                        .providerUserId(FAKE_PROVIDER_USER_ID)
                        .email(FAKE_EMAIL)
                        .displayName(FAKE_DISPLAY_NAME)
                        .build()));
        this.userId = user.getId();
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getEmail() {
        return FAKE_EMAIL;
    }

    @Override
    public String getDisplayName() {
        return FAKE_DISPLAY_NAME;
    }

    @Override
    public AppUser getCurrentUser() {
        return new AppUser(userId, FAKE_PROVIDER, FAKE_PROVIDER_USER_ID, FAKE_EMAIL, FAKE_DISPLAY_NAME);
    }
}
