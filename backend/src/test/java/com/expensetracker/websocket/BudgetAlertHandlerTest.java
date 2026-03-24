package com.expensetracker.websocket;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.expensetracker.config.UserPrincipal;
import com.expensetracker.service.BudgetAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@ExtendWith(MockitoExtension.class)
class BudgetAlertHandlerTest {

    @Mock private BudgetAlertService budgetAlertService;

    @InjectMocks private BudgetAlertHandler budgetAlertHandler;

    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        testPrincipal = new UserPrincipal(1L, "admin@test.com", "Test User");
    }

    @Test
    void handleSubscribe_budgetAlertsDestination_sendsInitialAlerts() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/topic/budget-alerts");
        accessor.setUser(testPrincipal);
        accessor.setSessionId("session-1");
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message, testPrincipal);

        budgetAlertHandler.handleSubscribe(event);

        verify(budgetAlertService).sendInitialAlerts(1L);
    }

    @Test
    void handleSubscribe_otherDestination_doesNotSendAlerts() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/other");
        accessor.setUser(testPrincipal);
        accessor.setSessionId("session-1");
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message, testPrincipal);

        budgetAlertHandler.handleSubscribe(event);

        verify(budgetAlertService, never()).sendInitialAlerts(1L);
    }

    @Test
    void handleSubscribe_nullDestination_doesNotSendAlerts() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-1");
        // No destination set
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

        budgetAlertHandler.handleSubscribe(event);

        verify(budgetAlertService, never()).sendInitialAlerts(1L);
    }

    @Test
    void handleSubscribe_noPrincipal_doesNotSendAlerts() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/topic/budget-alerts");
        accessor.setSessionId("session-1");
        // No user set
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

        budgetAlertHandler.handleSubscribe(event);

        verify(budgetAlertService, never()).sendInitialAlerts(1L);
    }
}
