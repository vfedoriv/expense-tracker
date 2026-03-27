package com.expensetracker.security;

import com.expensetracker.user.User;
import com.expensetracker.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.auth.fake", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class FakeAuthFilter extends OncePerRequestFilter {

    static final String DEFAULT_USER_EMAIL = "admin@test.com";
    static final String HEADER_NAME = "X-User-Email";

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String email = request.getHeader(HEADER_NAME);
        if (email == null || email.isBlank()) {
            email = DEFAULT_USER_EMAIL;
        }

        User user = userService.findOrCreateByFakeEmail(email);
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
