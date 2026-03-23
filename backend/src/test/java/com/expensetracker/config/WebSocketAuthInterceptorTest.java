package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthInterceptor")
class WebSocketAuthInterceptorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private WebSocketAuthInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(userRepository);
        attributes = new HashMap<>();
    }

    private ServletServerHttpRequest createServletRequest(String userIdHeader) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-User-Id")).thenReturn(userIdHeader);
        return new ServletServerHttpRequest(servletRequest);
    }

    private User createUser(Long id, String email, String displayName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setProvider("fake");
        user.setProviderUserId("fake-" + id);
        return user;
    }

    @Test
    @DisplayName("Rejects handshake when X-User-Id header is missing")
    void rejectsHandshake_whenUserIdHeaderMissing() {
        ServletServerHttpRequest request = createServletRequest(null);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey("user");
    }

    @Test
    @DisplayName("Rejects handshake when X-User-Id header is blank")
    void rejectsHandshake_whenUserIdHeaderBlank() {
        ServletServerHttpRequest request = createServletRequest("  ");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey("user");
    }

    @Test
    @DisplayName("Rejects handshake when X-User-Id header is empty string")
    void rejectsHandshake_whenUserIdHeaderEmpty() {
        ServletServerHttpRequest request = createServletRequest("");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey("user");
    }

    @Test
    @DisplayName("Rejects handshake when X-User-Id is not a valid number")
    void rejectsHandshake_whenUserIdHeaderInvalid() {
        ServletServerHttpRequest request = createServletRequest("abc");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey("user");
    }

    @Test
    @DisplayName("Rejects handshake when X-User-Id references non-existent user")
    void rejectsHandshake_whenUserDoesNotExist() {
        ServletServerHttpRequest request = createServletRequest("999");
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey("user");
    }

    @Test
    @DisplayName("Accepts handshake when X-User-Id references a valid user")
    void acceptsHandshake_whenValidUser() {
        User user = createUser(1L, "admin@test.com", "Test User");
        ServletServerHttpRequest request = createServletRequest("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).containsKey("user");
        UserPrincipal principal = (UserPrincipal) attributes.get("user");
        assertThat(principal.getUserId()).isEqualTo(1L);
        assertThat(principal.getEmail()).isEqualTo("admin@test.com");
        assertThat(principal.getDisplayName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Rejects handshake for non-servlet request")
    void rejectsHandshake_whenNonServletRequest() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey("user");
    }

    @Test
    @DisplayName("Does not fall back to default user when header is missing")
    void doesNotFallBackToDefaultUser_whenHeaderMissing() {
        ServletServerHttpRequest request = createServletRequest(null);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        // Verify that userRepository.findById was never called (no fallback to user 1)
        verify(userRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("Does not fall back to default user when header is invalid")
    void doesNotFallBackToDefaultUser_whenHeaderInvalid() {
        ServletServerHttpRequest request = createServletRequest("not-a-number");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        // Verify that userRepository.findById was never called (no fallback to user 1)
        verify(userRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("Accepts handshake with valid user ID that has leading/trailing whitespace")
    void acceptsHandshake_whenUserIdHasWhitespace() {
        User user = createUser(2L, "user2@test.com", "User 2");
        ServletServerHttpRequest request = createServletRequest(" 2 ");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        UserPrincipal principal = (UserPrincipal) attributes.get("user");
        assertThat(principal.getUserId()).isEqualTo(2L);
    }
}
