package io.github.vfedoriv.expensetracker.auth;

/**
 * Abstraction for the current authenticated user.
 * During Phase 1 (fake auth), this is backed by {@link FakeUserContext}.
 * During Phase 2 (real SSO), this will be backed by Spring Security's principal.
 */
public interface UserContext {

    Long getUserId();

    String getEmail();

    String getDisplayName();

    AppUser getCurrentUser();
}
