package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that converts OAuth2AuthenticationToken to UsernamePasswordAuthenticationToken with a
 * UserPrincipal, so that all existing controllers can continue using
 * {@code @AuthenticationPrincipal UserPrincipal}.
 */
public class OAuth2UserPrincipalFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof OAuth2AuthenticationToken oauthToken) {
            Object principal = oauthToken.getPrincipal();
            UserPrincipal userPrincipal = null;

            if (principal instanceof OidcUserWithLocalUser oidcUser) {
                userPrincipal =
                        new UserPrincipal(
                                oidcUser.getLocalUserId(),
                                oidcUser.getLocalUser().getEmail(),
                                oidcUser.getLocalUser().getDisplayName());
            } else if (principal instanceof OAuth2UserWithLocalUser oauthUser) {
                userPrincipal =
                        new UserPrincipal(
                                oauthUser.getLocalUserId(),
                                oauthUser.getLocalUser().getEmail(),
                                oauthUser.getLocalUser().getDisplayName());
            }

            if (userPrincipal != null) {
                UsernamePasswordAuthenticationToken newAuth =
                        new UsernamePasswordAuthenticationToken(
                                userPrincipal, null, oauthToken.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
