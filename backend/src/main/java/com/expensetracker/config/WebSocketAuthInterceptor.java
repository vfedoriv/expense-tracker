package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

/**
 * Intercepts WebSocket handshake to authenticate the user.
 * Extracts X-User-Id header (or defaults to user 1) and stores
 * the UserPrincipal in the WebSocket session attributes.
 * Rejects the handshake if no valid user is found.
 */
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long DEFAULT_USER_ID = 1L;

    private final UserRepository userRepository;

    public WebSocketAuthInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long userId = DEFAULT_USER_ID;

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String userIdHeader = servletRequest.getServletRequest().getHeader(USER_ID_HEADER);
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    userId = Long.parseLong(userIdHeader.trim());
                } catch (NumberFormatException e) {
                    userId = DEFAULT_USER_ID;
                }
            }
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false; // Reject handshake
        }

        UserPrincipal principal = new UserPrincipal(
            user.getId(),
            user.getEmail(),
            user.getDisplayName()
        );
        attributes.put("user", principal);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }
}
