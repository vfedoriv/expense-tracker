package com.expensetracker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthInterceptor")
class WebSocketAuthInterceptorTest {

    @Mock private UserRepository userRepository;

    @Mock private ServerHttpResponse response;

    @Mock private WebSocketHandler wsHandler;

    private WebSocketAuthInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(userRepository);
        attributes = new HashMap<>();
    }

    /**
     * Creates a servlet request with X-User-Id header and no query params. Uses a mocked
     * ServletServerHttpRequest to control getURI() and getServletRequest().
     */
    private ServletServerHttpRequest createServletRequestWithHeader(String userIdHeader) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        if (userIdHeader != null) {
            when(servletRequest.getHeader("X-User-Id")).thenReturn(userIdHeader);
        }
        ServletServerHttpRequest request = mock(ServletServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws"));
        when(request.getServletRequest()).thenReturn(servletRequest);
        return request;
    }

    /**
     * Creates a servlet request with userId query parameter and no X-User-Id header. Uses lenient
     * stubbing for getServletRequest() since it may not be accessed when the query parameter is
     * found first.
     */
    private ServletServerHttpRequest createServletRequestWithQueryParam(String queryString) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        ServletServerHttpRequest request = mock(ServletServerHttpRequest.class);
        when(request.getURI())
                .thenReturn(
                        URI.create(
                                "http://localhost:8080/ws"
                                        + (queryString != null ? "?" + queryString : "")));
        lenient().when(request.getServletRequest()).thenReturn(servletRequest);
        return request;
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

    @Nested
    @DisplayName("Header-based authentication (legacy/fallback)")
    class HeaderAuth {

        @Test
        @DisplayName("Rejects handshake when X-User-Id header is missing")
        void rejectsHandshake_whenUserIdHeaderMissing() {
            ServletServerHttpRequest request = createServletRequestWithHeader(null);

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Rejects handshake when X-User-Id header is blank")
        void rejectsHandshake_whenUserIdHeaderBlank() {
            ServletServerHttpRequest request = createServletRequestWithHeader("  ");

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Rejects handshake when X-User-Id header is empty string")
        void rejectsHandshake_whenUserIdHeaderEmpty() {
            ServletServerHttpRequest request = createServletRequestWithHeader("");

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Rejects handshake when X-User-Id is not a valid number")
        void rejectsHandshake_whenUserIdHeaderInvalid() {
            ServletServerHttpRequest request = createServletRequestWithHeader("abc");

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Rejects handshake when X-User-Id references non-existent user")
        void rejectsHandshake_whenUserDoesNotExist() {
            ServletServerHttpRequest request = createServletRequestWithHeader("999");
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Accepts handshake when X-User-Id references a valid user")
        void acceptsHandshake_whenValidUser() {
            User user = createUser(1L, "admin@test.com", "Test User");
            ServletServerHttpRequest request = createServletRequestWithHeader("1");
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
        @DisplayName("Accepts handshake with valid user ID that has leading/trailing whitespace")
        void acceptsHandshake_whenUserIdHasWhitespace() {
            User user = createUser(2L, "user2@test.com", "User 2");
            ServletServerHttpRequest request = createServletRequestWithHeader(" 2 ");
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isTrue();
            UserPrincipal principal = (UserPrincipal) attributes.get("user");
            assertThat(principal.getUserId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Does not fall back to default user when header is missing")
        void doesNotFallBackToDefaultUser_whenHeaderMissing() {
            ServletServerHttpRequest request = createServletRequestWithHeader(null);

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            verify(userRepository, never()).findById(1L);
        }

        @Test
        @DisplayName("Does not fall back to default user when header is invalid")
        void doesNotFallBackToDefaultUser_whenHeaderInvalid() {
            ServletServerHttpRequest request = createServletRequestWithHeader("not-a-number");

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            verify(userRepository, never()).findById(1L);
        }
    }

    @Nested
    @DisplayName("Query parameter authentication (SockJS compatible)")
    class QueryParamAuth {

        @Test
        @DisplayName("Accepts handshake when userId query parameter is present with valid user")
        void acceptsHandshake_whenQueryParamHasValidUser() {
            User user = createUser(1L, "admin@test.com", "Test User");
            ServletServerHttpRequest request = createServletRequestWithQueryParam("userId=1");
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
        @DisplayName("Rejects handshake when userId query parameter references non-existent user")
        void rejectsHandshake_whenQueryParamUserDoesNotExist() {
            ServletServerHttpRequest request = createServletRequestWithQueryParam("userId=999");
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Rejects handshake when userId query parameter is not a valid number")
        void rejectsHandshake_whenQueryParamIsInvalid() {
            ServletServerHttpRequest request = createServletRequestWithQueryParam("userId=abc");

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Rejects handshake when no userId query parameter and no header")
        void rejectsHandshake_whenNoQueryParamAndNoHeader() {
            ServletServerHttpRequest request = createServletRequestWithQueryParam(null);

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("user");
        }

        @Test
        @DisplayName("Accepts handshake when userId among multiple query parameters")
        void acceptsHandshake_whenUserIdAmongMultipleParams() {
            User user = createUser(3L, "user3@test.com", "User 3");
            ServletServerHttpRequest request =
                    createServletRequestWithQueryParam("foo=bar&userId=3&baz=qux");
            when(userRepository.findById(3L)).thenReturn(Optional.of(user));

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isTrue();
            UserPrincipal principal = (UserPrincipal) attributes.get("user");
            assertThat(principal.getUserId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("Query parameter takes precedence over header")
        void queryParamTakesPrecedenceOverHeader() {
            User user = createUser(2L, "user2@test.com", "User 2");
            // Create a request that has both query param and header
            HttpServletRequest servletRequest = mock(HttpServletRequest.class);
            lenient().when(servletRequest.getHeader("X-User-Id")).thenReturn("1");
            ServletServerHttpRequest request = mock(ServletServerHttpRequest.class);
            when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws?userId=2"));
            lenient().when(request.getServletRequest()).thenReturn(servletRequest);
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));

            boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

            assertThat(result).isTrue();
            UserPrincipal principal = (UserPrincipal) attributes.get("user");
            assertThat(principal.getUserId()).isEqualTo(2L);
            // Verify that user 1 (from header) was never looked up — query param won
            verify(userRepository, never()).findById(1L);
        }
    }

    @Test
    @DisplayName("Rejects handshake for non-servlet request without query param")
    void rejectsHandshake_whenNonServletRequestWithoutQueryParam() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey("user");
    }

    @Test
    @DisplayName("Accepts non-servlet request when query param has valid user")
    void acceptsHandshake_whenNonServletRequestWithQueryParam() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws?userId=1"));
        User user = createUser(1L, "admin@test.com", "Test User");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        UserPrincipal principal = (UserPrincipal) attributes.get("user");
        assertThat(principal.getUserId()).isEqualTo(1L);
    }
}
