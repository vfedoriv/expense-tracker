package com.expensetracker.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.expensetracker.dto.response.BudgetAlertMessage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class BudgetAlertEventListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private BudgetAlertEventListener listener;

    @Captor private ArgumentCaptor<BudgetAlertMessage> alertCaptor;

    @Test
    void handleBudgetAlertEvent_sendsAllAlertsViaWebSocket() {
        BudgetAlertMessage alert1 =
                BudgetAlertMessage.of(
                        50, new BigDecimal("500.00"), new BigDecimal("1000.00"), "2026-03");
        BudgetAlertMessage alert2 =
                BudgetAlertMessage.of(
                        80, new BigDecimal("800.00"), new BigDecimal("1000.00"), "2026-03");
        BudgetAlertEvent event = new BudgetAlertEvent(1L, List.of(alert1, alert2));

        listener.handleBudgetAlertEvent(event);

        verify(messagingTemplate, times(2))
                .convertAndSendToUser(eq("1"), eq("/topic/budget-alerts"), alertCaptor.capture());

        List<BudgetAlertMessage> sentAlerts = alertCaptor.getAllValues();
        assertThat(sentAlerts).hasSize(2);
        assertThat(sentAlerts.get(0).threshold()).isEqualTo(50);
        assertThat(sentAlerts.get(1).threshold()).isEqualTo(80);
    }

    @Test
    void handleBudgetAlertEvent_emptyAlerts_sendsNothing() {
        BudgetAlertEvent event = new BudgetAlertEvent(1L, List.of());

        listener.handleBudgetAlertEvent(event);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void handleBudgetAlertEvent_usesCorrectUserIdAsDestination() {
        BudgetAlertMessage alert =
                BudgetAlertMessage.of(
                        100, new BigDecimal("1000.00"), new BigDecimal("1000.00"), "2026-02");
        BudgetAlertEvent event = new BudgetAlertEvent(42L, List.of(alert));

        listener.handleBudgetAlertEvent(event);

        verify(messagingTemplate)
                .convertAndSendToUser(eq("42"), eq("/topic/budget-alerts"), alertCaptor.capture());

        assertThat(alertCaptor.getValue().threshold()).isEqualTo(100);
    }
}
