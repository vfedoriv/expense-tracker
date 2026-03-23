package com.expensetracker.config;

import com.expensetracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserRepository userRepository;
    private final Environment environment;
    private final CustomOAuth2UserService oauthUserService;
    private final CustomOidcUserService oidcUserService;

    public SecurityConfig(UserRepository userRepository,
                          Environment environment,
                          CustomOAuth2UserService oauthUserSvc,
                          CustomOidcUserService oidcUserSvc) {
        this.userRepository = userRepository;
        this.environment = environment;
        this.oauthUserService = oauthUserSvc;
        this.oidcUserService = oidcUserSvc;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        if (isFakeAuthActive()) {
            // Dev mode with fake auth: stateless, no CSRF, FakeAuthFilter
            http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
                )
                .addFilterBefore(new FakeAuthFilter(userRepository), UsernamePasswordAuthenticationFilter.class);
        } else {
            // OAuth2 mode: session-based, CSRF with cookie, OAuth2 login
            CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
            requestHandler.setCsrfRequestAttributeName(null);

            http
                .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler)
                )
                .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/health", "/oauth2/**", "/login/**", "/error").permitAll()
                    .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo
                        .userService(oauthUserService)
                        .oidcUserService(oidcUserService)
                    )
                    .successHandler(oAuth2AuthenticationSuccessHandler())
                )
                .logout(logout -> logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessHandler((request, response, authentication) -> {
                        response.setStatus(HttpServletResponse.SC_OK);
                    })
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                )
                .addFilterAfter(new OAuth2UserPrincipalFilter(),
                    org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class);
        }

        return http.build();
    }

    /**
     * Determines if fake auth should be active.
     * Active when no OAuth client IDs are configured (env vars not set).
     * In dev profile without OAuth env vars: fake auth.
     * In default/test profile without OAuth config: fake auth.
     * When OAuth client IDs are present: OAuth2 mode.
     */
    private boolean isFakeAuthActive() {
        String googleClientId = environment.getProperty("GOOGLE_CLIENT_ID", "");
        String githubClientId = environment.getProperty("GITHUB_CLIENT_ID", "");
        boolean hasOAuthEnvVars = !googleClientId.isBlank() || !githubClientId.isBlank();

        return !hasOAuthEnvVars;
    }

    /**
     * Prevent FakeAuthFilter from being auto-registered as a servlet filter.
     * It should only run when explicitly added to the SecurityFilterChain.
     */
    @Bean
    public FilterRegistrationBean<FakeAuthFilter> fakeAuthFilterRegistration() {
        FilterRegistrationBean<FakeAuthFilter> registration = new FilterRegistrationBean<>(new FakeAuthFilter(userRepository));
        registration.setEnabled(false);
        return registration;
    }

    private AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("http://localhost:5173");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
