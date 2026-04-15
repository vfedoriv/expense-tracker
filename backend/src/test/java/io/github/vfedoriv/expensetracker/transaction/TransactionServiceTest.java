package io.github.vfedoriv.expensetracker.transaction;

import io.github.vfedoriv.expensetracker.category.Category;
import io.github.vfedoriv.expensetracker.category.CategoryRepository;
import io.github.vfedoriv.expensetracker.exception.ResourceNotFoundException;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<TransactionChangedEvent> eventCaptor;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .provider("fake")
                .providerUserId("fake-user-1")
                .email("admin@test.com")
                .displayName("Admin User")
                .build();

        testCategory = Category.builder()
                .id(10L)
                .user(testUser)
                .name("Food")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("Should create transaction successfully")
        void create_happyPath() {
            TransactionRequest request = new TransactionRequest(
                    "Lunch", new BigDecimal("15.50"), "USD", 10L,
                    LocalDate.of(2026, 3, 20), "At the deli");

            Transaction savedTransaction = Transaction.builder()
                    .id(1L)
                    .user(testUser)
                    .category(testCategory)
                    .title("Lunch")
                    .amount(new BigDecimal("15.50"))
                    .currency("USD")
                    .transactionDate(LocalDate.of(2026, 3, 20))
                    .notes("At the deli")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(categoryRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testCategory));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);

            TransactionResponse result = transactionService.create(1L, request);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.title()).isEqualTo("Lunch");
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.50"));
            assertThat(result.currency()).isEqualTo("USD");
            assertThat(result.categoryId()).isEqualTo(10L);
            assertThat(result.categoryName()).isEqualTo("Food");
            assertThat(result.notes()).isEqualTo("At the deli");

            verify(transactionRepository).save(any(Transaction.class));
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void create_userNotFound_throws() {
            TransactionRequest request = new TransactionRequest(
                    "Lunch", new BigDecimal("15.50"), "USD", 10L,
                    LocalDate.of(2026, 3, 20), null);

            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.create(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(transactionRepository, never()).save(any(Transaction.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when category not found for user")
        void create_categoryNotFound_throws() {
            TransactionRequest request = new TransactionRequest(
                    "Lunch", new BigDecimal("15.50"), "USD", 99L,
                    LocalDate.of(2026, 3, 20), null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.create(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found")
                    .hasMessageContaining("99");

            verify(transactionRepository, never()).save(any(Transaction.class));
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("Should update transaction successfully")
        void update_happyPath() {
            TransactionRequest request = new TransactionRequest(
                    "Dinner", new BigDecimal("30.00"), "USD", 10L,
                    LocalDate.of(2026, 3, 20), "Updated notes");

            Transaction existingTransaction = Transaction.builder()
                    .id(1L)
                    .user(testUser)
                    .category(testCategory)
                    .title("Lunch")
                    .amount(new BigDecimal("15.50"))
                    .currency("USD")
                    .transactionDate(LocalDate.of(2026, 3, 20))
                    .notes("At the deli")
                    .createdAt(LocalDateTime.now())
                    .build();

            Transaction updatedTransaction = Transaction.builder()
                    .id(1L)
                    .user(testUser)
                    .category(testCategory)
                    .title("Dinner")
                    .amount(new BigDecimal("30.00"))
                    .currency("USD")
                    .transactionDate(LocalDate.of(2026, 3, 20))
                    .notes("Updated notes")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionRepository.findByIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(existingTransaction));
            when(categoryRepository.findByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(testCategory));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenReturn(updatedTransaction);

            TransactionResponse result = transactionService.update(1L, 1L, request);

            assertThat(result.title()).isEqualTo("Dinner");
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(result.notes()).isEqualTo("Updated notes");

            verify(transactionRepository).save(any(Transaction.class));
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when transaction not found")
        void update_transactionNotFound_throws() {
            TransactionRequest request = new TransactionRequest(
                    "Dinner", new BigDecimal("30.00"), "USD", 10L,
                    LocalDate.of(2026, 3, 20), null);

            when(transactionRepository.findByIdAndUserId(99L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.update(1L, 99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(transactionRepository, never()).save(any(Transaction.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when category not found during update")
        void update_categoryNotFound_throws() {
            TransactionRequest request = new TransactionRequest(
                    "Dinner", new BigDecimal("30.00"), "USD", 99L,
                    LocalDate.of(2026, 3, 20), null);

            Transaction existingTransaction = Transaction.builder()
                    .id(1L)
                    .user(testUser)
                    .category(testCategory)
                    .title("Lunch")
                    .amount(new BigDecimal("15.50"))
                    .currency("USD")
                    .transactionDate(LocalDate.of(2026, 3, 20))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionRepository.findByIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(existingTransaction));
            when(categoryRepository.findByIdAndUserId(99L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.update(1L, 1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found")
                    .hasMessageContaining("99");

            verify(transactionRepository, never()).save(any(Transaction.class));
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("Should delete transaction and publish TransactionChangedEvent")
        void delete_happyPath_publishesEvent() {
            Transaction existingTransaction = Transaction.builder()
                    .id(1L)
                    .user(testUser)
                    .category(testCategory)
                    .title("Lunch")
                    .amount(new BigDecimal("15.50"))
                    .currency("USD")
                    .transactionDate(LocalDate.of(2026, 3, 20))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionRepository.findByIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(existingTransaction));

            transactionService.delete(1L, 1L);

            verify(transactionRepository).delete(existingTransaction);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when transaction not found")
        void delete_notFound_throws() {
            when(transactionRepository.findByIdAndUserId(99L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.delete(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(transactionRepository, never()).delete(any(Transaction.class));
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("Should delegate to repository with specification and return mapped results")
        @SuppressWarnings("unchecked")
        void search_delegatesToRepositoryWithSpecification() {
            Pageable pageable = PageRequest.of(0, 20);

            Transaction transaction = Transaction.builder()
                    .id(1L)
                    .user(testUser)
                    .category(testCategory)
                    .title("Lunch")
                    .amount(new BigDecimal("15.50"))
                    .currency("USD")
                    .transactionDate(LocalDate.of(2026, 3, 20))
                    .notes("At the deli")
                    .createdAt(LocalDateTime.now())
                    .build();

            Page<Transaction> page = new PageImpl<>(List.of(transaction), pageable, 1);
            when(transactionRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(page);

            Page<TransactionResponse> result = transactionService.search(
                    1L, "lunch", null, null, null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).title()).isEqualTo("Lunch");
            assertThat(result.getContent().get(0).categoryId()).isEqualTo(10L);

            verify(transactionRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("Should return empty page when no transactions match")
        @SuppressWarnings("unchecked")
        void search_noResults_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Transaction> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(transactionRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(emptyPage);

            Page<TransactionResponse> result = transactionService.search(
                    1L, null, null, null, null, null, null, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}
