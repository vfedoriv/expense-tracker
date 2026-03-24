package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FakeAuthFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long DEFAULT_USER_ID = 1L;

    private final UserRepository userRepository;

    public FakeAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader(USER_ID_HEADER);
        Long userId = DEFAULT_USER_ID;

        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                userId = Long.parseLong(userIdHeader.trim());
            } catch (NumberFormatException e) {
                userId = DEFAULT_USER_ID;
            }
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            UserPrincipal principal =
                    new UserPrincipal(user.getId(), user.getEmail(), user.getDisplayName());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
