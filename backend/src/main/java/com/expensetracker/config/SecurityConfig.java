package com.expensetracker.config;

import com.expensetracker.security.CustomOAuth2UserService;
import com.expensetracker.security.CustomOidcUserService;
import com.expensetracker.security.FakeAuthFilter;
import com.expensetracker.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.auth.fake:true}")
    private boolean fakeAuth;

    @Autowired(required = false)
    private FakeAuthFilter fakeAuthFilter;

    @Autowired(required = false)
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired(required = false)
    private CustomOidcUserService customOidcUserService;

    @Autowired(required = false)
    private OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws/**", "/login/**", "/oauth2/**", "/error").permitAll()
                .anyRequest().authenticated()
            );

        if (fakeAuth || fakeAuthFilter != null) {
            http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(fakeAuthFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService)
                        .oidcUserService(customOidcUserService)
                    )
                    .successHandler(oauth2SuccessHandler)
                )
                .logout(logout -> logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessUrl("/login")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                );
        }

        return http.build();
    }
}
