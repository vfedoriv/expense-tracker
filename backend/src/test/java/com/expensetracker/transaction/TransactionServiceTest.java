package com.expensetracker.transaction;

import com.expensetracker.category.CategoryRepository;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.transaction.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransactionService transactionService;

    private TransactionRequest validRequest() {
        return new TransactionRequest("Coffee", new BigDecimal("3.50"), "USD", LocalDate.now(), 1L, "Morning coffee");
    }

    @Test
    void create_withValidRequest_createsTransaction() {
        when(categoryRepository.existsByIdAndUserId(1L, 1L)).thenReturn(true);
        Transaction saved = Transaction.builder()
            .id(1L).userId(1L).categoryId(1L)
            .title("Coffee").amount(new BigDecimal("3.50")).currency("USD")
            .transactionDate(LocalDate.now()).notes("Morning coffee")
            .build();
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

        Transaction result = transactionService.create(1L, validRequest());

        assertThat(result.getTitle()).isEqualTo("Coffee");
        assertThat(result.getAmount()).isEqualByComparingTo("3.50");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void create_withInvalidCategory_throwsNotFound() {
        when(categoryRepository.existsByIdAndUserId(1L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.create(1L, validRequest()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Category");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void update_withValidRequest_updatesTransaction() {
        Long userId = 1L;
        Long txId = 10L;
        Transaction existing = Transaction.builder()
            .id(txId).userId(userId).categoryId(1L)
            .title("Old").amount(new BigDecimal("5.00")).currency("USD")
            .transactionDate(LocalDate.now()).build();
        when(transactionRepository.findByIdAndUserId(txId, userId)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByIdAndUserId(1L, userId)).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.update(txId, userId, validRequest());

        assertThat(result.getTitle()).isEqualTo("Coffee");
    }

    @Test
    void delete_whenNotOwner_throwsNotFound() {
        when(transactionRepository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(1L, 99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByIdAndUser_whenNotFound_throwsResourceNotFound() {
        when(transactionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findByIdAndUser(99L, 1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
