package com.expensetracker.service;

import com.expensetracker.dto.request.BudgetRequest;
import com.expensetracker.dto.response.BudgetResponse;
import com.expensetracker.entity.MonthlyBudget;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.MonthlyBudgetRepository;
import com.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BudgetService budgetService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setProvider("fake");
        testUser.setProviderUserId("fake-user-1");
        testUser.setEmail("admin@test.com");
        testUser.setDisplayName("Test User");
    }

    @Test
    void createBudget_newBudget_returnsCreatedTrue() {
        BudgetRequest request = new BudgetRequest(2026, 3, new BigDecimal("1000.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.empty());

        MonthlyBudget saved = createBudgetEntity(1L, (short) 2026, (short) 3, new BigDecimal("1000.00"));
        when(monthlyBudgetRepository.save(any(MonthlyBudget.class))).thenReturn(saved);

        BudgetService.UpsertResult result = budgetService.createOrUpdateBudget(1L, request);

        assertThat(result.created()).isTrue();
        assertThat(result.response().year()).isEqualTo(2026);
        assertThat(result.response().month()).isEqualTo(3);
        assertThat(result.response().amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        verify(monthlyBudgetRepository).save(any(MonthlyBudget.class));
    }

    @Test
    void createBudget_existingBudget_returnsCreatedFalse() {
        BudgetRequest request = new BudgetRequest(2026, 3, new BigDecimal("2000.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        MonthlyBudget existing = createBudgetEntity(1L, (short) 2026, (short) 3, new BigDecimal("1000.00"));
        existing.setUser(testUser);
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(existing));

        MonthlyBudget updated = createBudgetEntity(1L, (short) 2026, (short) 3, new BigDecimal("2000.00"));
        when(monthlyBudgetRepository.save(any(MonthlyBudget.class))).thenReturn(updated);

        BudgetService.UpsertResult result = budgetService.createOrUpdateBudget(1L, request);

        assertThat(result.created()).isFalse();
        assertThat(result.response().amount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        verify(monthlyBudgetRepository).save(any(MonthlyBudget.class));
    }

    @Test
    void createBudget_userNotFound_throwsNotFound() {
        BudgetRequest request = new BudgetRequest(2026, 3, new BigDecimal("1000.00"));

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.createOrUpdateBudget(99L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    void getBudget_exists_returnsBudget() {
        MonthlyBudget budget = createBudgetEntity(1L, (short) 2026, (short) 3, new BigDecimal("1500.00"));
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.of(budget));

        BudgetResponse response = budgetService.getBudget(1L, 2026, 3);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(3);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void getBudget_invalidMonth_throwsIllegalArgument() {
        assertThatThrownBy(() -> budgetService.getBudget(1L, 2026, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Month must be between 1 and 12");
    }

    @Test
    void getBudget_invalidMonth13_throwsIllegalArgument() {
        assertThatThrownBy(() -> budgetService.getBudget(1L, 2026, 13))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Month must be between 1 and 12");
    }

    @Test
    void getBudget_yearTooLow_throwsIllegalArgument() {
        assertThatThrownBy(() -> budgetService.getBudget(1L, 1999, 6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Year must be between 2000 and 2100");
    }

    @Test
    void getBudget_yearTooHigh_throwsIllegalArgument() {
        assertThatThrownBy(() -> budgetService.getBudget(1L, 2101, 6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Year must be between 2000 and 2100");
    }

    @Test
    void createBudget_invalidMonth_throwsIllegalArgument() {
        BudgetRequest request = new BudgetRequest(2026, 0, new BigDecimal("1000.00"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> budgetService.createOrUpdateBudget(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Month must be between 1 and 12");
    }

    @Test
    void createBudget_invalidYear_throwsIllegalArgument() {
        BudgetRequest request = new BudgetRequest(1999, 6, new BigDecimal("1000.00"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> budgetService.createOrUpdateBudget(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Year must be between 2000 and 2100");
    }

    @Test
    void getBudget_notFound_throwsNotFound() {
        when(monthlyBudgetRepository.findByUserIdAndYearAndMonth(1L, (short) 2026, (short) 3))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getBudget(1L, 2026, 3))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Budget not found");
    }

    private MonthlyBudget createBudgetEntity(Long id, Short year, Short month, BigDecimal amount) {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setId(id);
        budget.setYear(year);
        budget.setMonth(month);
        budget.setAmount(amount);
        budget.setCreatedAt(OffsetDateTime.now());
        budget.setUpdatedAt(OffsetDateTime.now());
        return budget;
    }
}
