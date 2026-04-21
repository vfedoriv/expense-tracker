package com.expensetracker.service;

import com.expensetracker.dto.request.TransactionRequest;
import com.expensetracker.dto.response.TransactionResponse;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BudgetAlertService budgetAlertService;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setProvider("fake");
        testUser.setProviderUserId("fake-user-1");
        testUser.setEmail("admin@test.com");
        testUser.setDisplayName("Test User");

        testCategory = new Category();
        testCategory.setId(10L);
        testCategory.setName("Food");
        testCategory.setUser(testUser);
        testCategory.setCreatedAt(OffsetDateTime.now());
        testCategory.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    void createTransaction_success() {
        TransactionRequest request = new TransactionRequest(
            "Grocery shopping", new BigDecimal("52.75"), LocalDate.of(2026, 3, 15), 10L, "Weekly groceries"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testCategory));

        Transaction savedTransaction = createTransactionEntity(1L, "Grocery shopping", new BigDecimal("52.75"),
            LocalDate.of(2026, 3, 15), "Weekly groceries");
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);

        TransactionResponse response = transactionService.createTransaction(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Grocery shopping");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("52.75"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.transactionDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(response.categoryId()).isEqualTo(10L);
        assertThat(response.categoryName()).isEqualTo("Food");
        assertThat(response.notes()).isEqualTo("Weekly groceries");
        assertThat(response.createdAt()).isNotNull();
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_categoryNotOwned_throwsNotFound() {
        TransactionRequest request = new TransactionRequest(
            "Grocery shopping", new BigDecimal("52.75"), LocalDate.of(2026, 3, 15), 99L, null
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(1L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Category not found");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_userNotFound_throwsNotFound() {
        TransactionRequest request = new TransactionRequest(
            "Grocery shopping", new BigDecimal("52.75"), LocalDate.of(2026, 3, 15), 10L, null
        );

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(99L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User not found");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void updateTransaction_success() {
        TransactionRequest request = new TransactionRequest(
            "Updated title", new BigDecimal("100.00"), LocalDate.of(2026, 3, 20), 10L, "Updated notes"
        );

        Transaction existingTransaction = createTransactionEntity(1L, "Old title", new BigDecimal("50.00"),
            LocalDate.of(2026, 3, 10), "Old notes");
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existingTransaction));
        when(categoryRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testCategory));

        Transaction savedTransaction = createTransactionEntity(1L, "Updated title", new BigDecimal("100.00"),
            LocalDate.of(2026, 3, 20), "Updated notes");
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);

        TransactionResponse response = transactionService.updateTransaction(1L, 1L, request);

        assertThat(response.title()).isEqualTo("Updated title");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.transactionDate()).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(response.notes()).isEqualTo("Updated notes");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void updateTransaction_notOwned_throwsNotFound() {
        TransactionRequest request = new TransactionRequest(
            "Updated", new BigDecimal("100.00"), LocalDate.of(2026, 3, 20), 10L, null
        );

        when(transactionRepository.findByIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.updateTransaction(2L, 1L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Transaction not found");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void updateTransaction_categoryNotOwned_throwsNotFound() {
        TransactionRequest request = new TransactionRequest(
            "Updated", new BigDecimal("100.00"), LocalDate.of(2026, 3, 20), 99L, null
        );

        Transaction existingTransaction = createTransactionEntity(1L, "Old title", new BigDecimal("50.00"),
            LocalDate.of(2026, 3, 10), null);
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existingTransaction));
        when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.updateTransaction(1L, 1L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Category not found");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deleteTransaction_success() {
        Transaction existingTransaction = createTransactionEntity(1L, "Test", new BigDecimal("50.00"),
            LocalDate.of(2026, 3, 15), null);
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existingTransaction));

        transactionService.deleteTransaction(1L, 1L);

        verify(transactionRepository).delete(existingTransaction);
    }

    @Test
    void deleteTransaction_notOwned_throwsNotFound() {
        when(transactionRepository.findByIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.deleteTransaction(2L, 1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Transaction not found");

        verify(transactionRepository, never()).delete(any());
    }

    @Test
    void listTransactions_noFilters() {
        Transaction txn1 = createTransactionEntity(1L, "Groceries", new BigDecimal("52.75"),
            LocalDate.of(2026, 3, 15), "Weekly");
        Transaction txn2 = createTransactionEntity(2L, "Gas", new BigDecimal("40.00"),
            LocalDate.of(2026, 3, 14), null);

        when(transactionRepository.findAllByUserIdWithFilters(eq(1L), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null)))
            .thenReturn(List.of(txn1, txn2));

        List<TransactionResponse> result = transactionService.listTransactions(1L, null, null, null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Groceries");
        assertThat(result.get(1).title()).isEqualTo("Gas");
    }

    @Test
    void listTransactions_withSearch() {
        Transaction txn1 = createTransactionEntity(1L, "Groceries", new BigDecimal("52.75"),
            LocalDate.of(2026, 3, 15), "Weekly trip");

        when(transactionRepository.findAllByUserIdWithFilters(eq(1L), eq("groc"), eq(null), eq(null), eq(null), eq(null), eq(null)))
            .thenReturn(List.of(txn1));

        List<TransactionResponse> result = transactionService.listTransactions(1L, "groc", null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Groceries");
    }

    @Test
    void listTransactions_withCategoryFilter() {
        Transaction txn1 = createTransactionEntity(1L, "Groceries", new BigDecimal("52.75"),
            LocalDate.of(2026, 3, 15), null);

        when(transactionRepository.findAllByUserIdWithFilters(eq(1L), eq(null), eq(10L), eq(null), eq(null), eq(null), eq(null)))
            .thenReturn(List.of(txn1));

        List<TransactionResponse> result = transactionService.listTransactions(1L, null, 10L, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryId()).isEqualTo(10L);
    }

    @Test
    void listTransactions_withDateRange() {
        LocalDate dateFrom = LocalDate.of(2026, 3, 1);
        LocalDate dateTo = LocalDate.of(2026, 3, 31);

        Transaction txn1 = createTransactionEntity(1L, "Groceries", new BigDecimal("52.75"),
            LocalDate.of(2026, 3, 15), null);

        when(transactionRepository.findAllByUserIdWithFilters(eq(1L), eq(null), eq(null), eq(dateFrom), eq(dateTo), eq(null), eq(null)))
            .thenReturn(List.of(txn1));

        List<TransactionResponse> result = transactionService.listTransactions(1L, null, null, dateFrom, dateTo, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void listTransactions_withAmountRange() {
        BigDecimal amountMin = new BigDecimal("10.00");
        BigDecimal amountMax = new BigDecimal("100.00");

        Transaction txn1 = createTransactionEntity(1L, "Groceries", new BigDecimal("52.75"),
            LocalDate.of(2026, 3, 15), null);

        when(transactionRepository.findAllByUserIdWithFilters(eq(1L), eq(null), eq(null), eq(null), eq(null), eq(amountMin), eq(amountMax)))
            .thenReturn(List.of(txn1));

        List<TransactionResponse> result = transactionService.listTransactions(1L, null, null, null, null, amountMin, amountMax);

        assertThat(result).hasSize(1);
    }

    @Test
    void createTransaction_pastMonth_evaluatesAlertsForThatMonth() {
        // Transaction date is in January (a past month)
        LocalDate pastDate = LocalDate.of(2026, 1, 15);
        TransactionRequest request = new TransactionRequest(
            "Past purchase", new BigDecimal("500.00"), pastDate, 10L, null
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testCategory));

        Transaction savedTransaction = createTransactionEntity(1L, "Past purchase", new BigDecimal("500.00"),
            pastDate, null);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);

        transactionService.createTransaction(1L, request);

        // Verify evaluateAlerts is called with the transaction's date (January), not current month
        verify(budgetAlertService).evaluateAlerts(1L, pastDate);
    }

    @Test
    void updateTransaction_evaluatesAlertsForTransactionDate() {
        LocalDate transactionDate = LocalDate.of(2026, 2, 10);
        TransactionRequest request = new TransactionRequest(
            "Updated", new BigDecimal("100.00"), transactionDate, 10L, null
        );

        Transaction existingTransaction = createTransactionEntity(1L, "Old title", new BigDecimal("50.00"),
            transactionDate, null);
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existingTransaction));
        when(categoryRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testCategory));

        Transaction savedTransaction = createTransactionEntity(1L, "Updated", new BigDecimal("100.00"),
            transactionDate, null);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);

        transactionService.updateTransaction(1L, 1L, request);

        verify(budgetAlertService).evaluateAlerts(1L, transactionDate);
    }

    @Test
    void deleteTransaction_evaluatesAlertsForDeletedTransactionDate() {
        LocalDate transactionDate = LocalDate.of(2026, 1, 20);
        Transaction existingTransaction = createTransactionEntity(1L, "Test", new BigDecimal("50.00"),
            transactionDate, null);
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existingTransaction));

        transactionService.deleteTransaction(1L, 1L);

        verify(transactionRepository).delete(existingTransaction);
        // Verify evaluateAlerts is called with the DELETED transaction's date
        verify(budgetAlertService).evaluateAlerts(1L, transactionDate);
    }

    private Transaction createTransactionEntity(Long id, String title, BigDecimal amount,
                                                 LocalDate transactionDate, String notes) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setTitle(title);
        transaction.setAmount(amount);
        transaction.setCurrency("USD");
        transaction.setTransactionDate(transactionDate);
        transaction.setNotes(notes);
        transaction.setCategory(testCategory);
        transaction.setUser(testUser);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setUpdatedAt(OffsetDateTime.now());
        return transaction;
    }
}
