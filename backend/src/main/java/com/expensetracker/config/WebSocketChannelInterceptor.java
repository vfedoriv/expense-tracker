package com.expensetracker.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Channel interceptor that extracts the UserPrincipal from the WebSocket session attributes (set
 * during handshake) and assigns it as the message's user principal. This enables /user/topic/*
 * destinations to route to the correct user.
 */
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            var sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                UserPrincipal principal = (UserPrincipal) sessionAttributes.get("user");
                if (principal != null) {
                    accessor.setUser(principal);
                }
            }
        }
        return message;
    }
}
