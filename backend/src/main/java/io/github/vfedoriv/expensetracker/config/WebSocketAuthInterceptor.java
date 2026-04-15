package io.github.vfedoriv.expensetracker.config;

import io.github.vfedoriv.expensetracker.auth.AppUser;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Intercepts inbound STOMP CONNECT frames and sets a {@link Principal}
 * derived from the current Spring Security authentication context.
 * <p>
 * This bridges the HTTP-level fake-auth filter with the WebSocket
 * messaging layer so that {@code @MessageMapping} handlers receive
 * a non-null {@link Principal} whose {@code getName()} returns the
 * user's database ID as a string.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof AppUser appUser) {
                accessor.setUser(new StompPrincipal(appUser.id().toString()));
            }
        }

        return message;
    }

    /**
     * Minimal {@link Principal} implementation that carries only the
     * user's database ID.
     */
    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
