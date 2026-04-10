package io.github.vfedoriv.expensetracker.auth;

import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "fake", matchIfMissing = true)
@RequiredArgsConstructor
public class FakeAuthFilter extends OncePerRequestFilter {

    private static final String FAKE_PROVIDER = "fake";
    private static final String FAKE_PROVIDER_USER_ID = "fake-user-001";
    private static final String FAKE_EMAIL = "admin@test.com";
    private static final String FAKE_DISPLAY_NAME = "Admin User";

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        User user = userRepository.findByProviderAndProviderUserId(FAKE_PROVIDER, FAKE_PROVIDER_USER_ID)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .provider(FAKE_PROVIDER)
                            .providerUserId(FAKE_PROVIDER_USER_ID)
                            .email(FAKE_EMAIL)
                            .displayName(FAKE_DISPLAY_NAME)
                            .build();
                    return userRepository.save(newUser);
                });

        AppUser appUser = new AppUser(
                user.getId(),
                user.getProvider(),
                user.getProviderUserId(),
                user.getEmail(),
                user.getDisplayName()
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        appUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
