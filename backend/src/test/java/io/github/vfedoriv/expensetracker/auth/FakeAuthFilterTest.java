package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FakeAuthFilterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private FakeAuthFilter fakeAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should reuse existing user when already present in repository")
    void doFilterInternal_userAlreadyExists_setsSecurityContext() throws Exception {
        User existingUser = User.builder()
                .id(1L)
                .provider("fake")
                .providerUserId("fake-user-1")
                .email("admin@test.com")
                .displayName("Admin User")
                .build();

        when(userRepository.findByProviderAndProviderUserId("fake", "fake-user-1"))
                .thenReturn(Optional.of(existingUser));

        fakeAuthFilter.doFilterInternal(request, response, filterChain);

        verify(userRepository).findByProviderAndProviderUserId("fake", "fake-user-1");
        verify(userRepository, never()).save(any(User.class));
        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();

        Object principal = authentication.getPrincipal();
        assertThat(principal).isInstanceOf(AppUser.class);

        AppUser appUser = (AppUser) principal;
        assertThat(appUser.id()).isEqualTo(1L);
        assertThat(appUser.provider()).isEqualTo("fake");
        assertThat(appUser.providerId()).isEqualTo("fake-user-1");
        assertThat(appUser.email()).isEqualTo("admin@test.com");
        assertThat(appUser.displayName()).isEqualTo("Admin User");
    }

    @Test
    @DisplayName("Should create new user when not found in repository")
    void doFilterInternal_userDoesNotExist_createsAndSetsSecurityContext() throws Exception {
        User newUser = User.builder()
                .id(1L)
                .provider("fake")
                .providerUserId("fake-user-1")
                .email("admin@test.com")
                .displayName("Admin User")
                .build();

        when(userRepository.findByProviderAndProviderUserId("fake", "fake-user-1"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        fakeAuthFilter.doFilterInternal(request, response, filterChain);

        verify(userRepository).findByProviderAndProviderUserId("fake", "fake-user-1");
        verify(userRepository).save(any(User.class));
        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();

        AppUser appUser = (AppUser) authentication.getPrincipal();
        assertThat(appUser.id()).isEqualTo(1L);
        assertThat(appUser.provider()).isEqualTo("fake");
        assertThat(appUser.providerId()).isEqualTo("fake-user-1");
        assertThat(appUser.email()).isEqualTo("admin@test.com");
        assertThat(appUser.displayName()).isEqualTo("Admin User");
    }

    @Test
    @DisplayName("Should set ROLE_USER authority on the authentication token")
    void doFilterInternal_setsRoleUserAuthority() throws Exception {
        User existingUser = User.builder()
                .id(1L)
                .provider("fake")
                .providerUserId("fake-user-1")
                .email("admin@test.com")
                .displayName("Admin User")
                .build();

        when(userRepository.findByProviderAndProviderUserId("fake", "fake-user-1"))
                .thenReturn(Optional.of(existingUser));

        fakeAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities())
                .hasSize(1)
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }
}
