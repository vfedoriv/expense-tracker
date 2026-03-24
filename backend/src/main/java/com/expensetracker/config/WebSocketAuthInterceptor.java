package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import java.net.URI;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Intercepts WebSocket handshake to authenticate the user. Extracts the user ID from the query
 * parameter {@code userId} (primary) or the {@code X-User-Id} HTTP header (fallback) and stores the
 * UserPrincipal in the WebSocket session attributes. Rejects the handshake if the user ID is
 * missing, invalid, or references a non-existent user.
 *
 * <p>SockJS clients cannot set custom HTTP headers on the handshake request, so the recommended
 * approach is to pass the user ID as a query parameter on the connection URL (e.g., {@code
 * /ws?userId=1}).
 */
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final String USER_ID_QUERY_PARAM = "userId";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserRepository userRepository;

    public WebSocketAuthInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        // Try query parameter first (works with SockJS), then fall back to header
        String userIdValue = extractUserIdFromQuery(request.getURI());

        if ((userIdValue == null || userIdValue.isBlank())
                && request instanceof ServletServerHttpRequest servletRequest) {
            userIdValue = servletRequest.getServletRequest().getHeader(USER_ID_HEADER);
        }

        if (userIdValue == null || userIdValue.isBlank()) {
            return false; // Reject: missing user ID
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdValue.trim());
        } catch (NumberFormatException e) {
            return false; // Reject: invalid user ID
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false; // Reject: non-existent user
        }

        UserPrincipal principal =
                new UserPrincipal(user.getId(), user.getEmail(), user.getDisplayName());
        attributes.put("user", principal);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }

    /** Extracts the {@code userId} parameter from the URI query string. */
    private String extractUserIdFromQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && USER_ID_QUERY_PARAM.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}
