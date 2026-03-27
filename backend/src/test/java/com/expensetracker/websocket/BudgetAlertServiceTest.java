package com.expensetracker.websocket;

import com.expensetracker.budget.BudgetService;
import com.expensetracker.budget.dto.BudgetSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetAlertServiceTest {

    @Mock
    private BudgetService budgetService;

    @Spy
    private BudgetThresholdTracker thresholdTracker = new BudgetThresholdTracker();

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BudgetAlertService budgetAlertService;

    @Test
    void checkAndSendAlerts_whenNoBudgetSet_noAlertsGenerated() {
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.noBudget(2026, 3, BigDecimal.ZERO));

        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void checkAndSendAlerts_at50Percent_sends50Alert() {
        BigDecimal budget = new BigDecimal("100.00");
        BigDecimal spent = new BigDecimal("50.00");
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, budget, spent));

        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        ArgumentCaptor<BudgetAlertMessage> alertCaptor = ArgumentCaptor.forClass(BudgetAlertMessage.class);
        verify(messagingTemplate).convertAndSendToUser(eq("1"), anyString(), alertCaptor.capture());
        assertThat(alertCaptor.getValue().threshold()).isEqualTo(50);
    }

    @Test
    void checkAndSendAlerts_at80Percent_sends50And80Alerts() {
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("80.00")));

        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        verify(messagingTemplate, times(2)).convertAndSendToUser(eq("1"), anyString(), any(BudgetAlertMessage.class));
    }

    @Test
    void checkAndSendAlerts_at100Percent_sendsAllThreeAlerts() {
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("100.00")));

        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        verify(messagingTemplate, times(3)).convertAndSendToUser(eq("1"), anyString(), any(BudgetAlertMessage.class));
    }

    @Test
    void checkAndSendAlerts_sameThresholdTwice_sendsOnlyOnce() {
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("50.00")));

        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);
        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("1"), anyString(), any(BudgetAlertMessage.class));
    }

    @Test
    void sendCurrentStatus_sendsHighestCrossedThresholdOnly() {
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("85.00")));

        budgetAlertService.sendCurrentStatus(1L, 2026, 3);

        // Should send only one alert (the highest crossed: 80%)
        ArgumentCaptor<BudgetAlertMessage> alertCaptor = ArgumentCaptor.forClass(BudgetAlertMessage.class);
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("1"), anyString(), alertCaptor.capture());
        assertThat(alertCaptor.getValue().threshold()).isEqualTo(80);
    }

    @Test
    void sendCurrentStatus_thenTransactionChange_firesNewThreshold() {
        // Subscribe at 50%
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("50.00")));
        budgetAlertService.sendCurrentStatus(1L, 2026, 3);

        // Transaction pushes to 80%
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("80.00")));
        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        // Subscribe sent 50% alert, transaction change sends 80% alert = 2 total
        verify(messagingTemplate, times(2)).convertAndSendToUser(eq("1"), anyString(), any(BudgetAlertMessage.class));
    }

    @Test
    void checkAndSendAlerts_afterDeletion_reCrossedThresholdFires() {
        // At 80%
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("80.00")));
        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        // Delete drops to 40%
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("40.00")));
        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        // Add back to 80%
        when(budgetService.getSummary(1L, 2026, 3))
            .thenReturn(BudgetSummaryResponse.withBudget(2026, 3, new BigDecimal("100.00"), new BigDecimal("80.00")));
        budgetAlertService.checkAndSendAlerts(1L, 2026, 3);

        // First call: 50% + 80% = 2, second call: no alerts (below 50%), third call: 50% + 80% again = 2 => total 4
        verify(messagingTemplate, times(4)).convertAndSendToUser(eq("1"), anyString(), any(BudgetAlertMessage.class));
    }
}
