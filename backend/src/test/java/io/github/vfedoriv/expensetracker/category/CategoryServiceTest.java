package io.github.vfedoriv.expensetracker.category;

import io.github.vfedoriv.expensetracker.exception.DeletionBlockedException;
import io.github.vfedoriv.expensetracker.exception.DuplicateResourceException;
import io.github.vfedoriv.expensetracker.exception.ResourceNotFoundException;
import io.github.vfedoriv.expensetracker.transaction.TransactionRepository;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CategoryService categoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .provider("fake")
                .providerUserId("fake-user-1")
                .email("admin@test.com")
                .displayName("Admin User")
                .build();
    }

    @Nested
    @DisplayName("getAllByUser")
    class GetAllByUser {

        @Test
        @DisplayName("Should return all categories for a user")
        void getAllByUser_returnsCategoryList() {
            Category cat1 = Category.builder()
                    .id(1L).user(testUser).name("Food").createdAt(LocalDateTime.now()).build();
            Category cat2 = Category.builder()
                    .id(2L).user(testUser).name("Transport").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.findAllByUserId(1L)).thenReturn(List.of(cat1, cat2));

            List<CategoryResponse> result = categoryService.getAllByUser(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("Food");
            assertThat(result.get(1).name()).isEqualTo("Transport");
            verify(categoryRepository).findAllByUserId(1L);
        }

        @Test
        @DisplayName("Should return empty list when user has no categories")
        void getAllByUser_emptyList() {
            when(categoryRepository.findAllByUserId(1L)).thenReturn(List.of());

            List<CategoryResponse> result = categoryService.getAllByUser(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("Should create category successfully")
        void create_happyPath() {
            CategoryRequest request = new CategoryRequest("Food");
            Category savedCategory = Category.builder()
                    .id(1L).user(testUser).name("Food").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.existsByUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

            CategoryResponse result = categoryService.create(1L, request);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Food");
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when name already exists")
        void create_duplicateName_throws() {
            CategoryRequest request = new CategoryRequest("Food");

            when(categoryRepository.existsByUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.create(1L, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Food");

            verify(categoryRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void create_userNotFound_throws() {
            CategoryRequest request = new CategoryRequest("Food");

            when(categoryRepository.existsByUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.create(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("Should update category successfully")
        void update_happyPath() {
            CategoryRequest request = new CategoryRequest("Groceries");
            Category existingCategory = Category.builder()
                    .id(1L).user(testUser).name("Food").createdAt(LocalDateTime.now()).build();
            Category updatedCategory = Category.builder()
                    .id(1L).user(testUser).name("Groceries").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existingCategory));
            when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(1L, "Groceries", 1L))
                    .thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

            CategoryResponse result = categoryService.update(1L, 1L, request);

            assertThat(result.name()).isEqualTo("Groceries");
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when category not found")
        void update_notFound_throws() {
            CategoryRequest request = new CategoryRequest("Groceries");

            when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.update(1L, 99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when new name conflicts with another category")
        void update_duplicateName_throws() {
            CategoryRequest request = new CategoryRequest("Transport");
            Category existingCategory = Category.builder()
                    .id(1L).user(testUser).name("Food").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existingCategory));
            when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(1L, "Transport", 1L))
                    .thenReturn(true);

            assertThatThrownBy(() -> categoryService.update(1L, 1L, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Transport");

            verify(categoryRepository, never()).save(any(Category.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("Should delete category successfully when no transactions reference it")
        void delete_happyPath() {
            Category category = Category.builder()
                    .id(1L).user(testUser).name("Food").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(category));
            when(transactionRepository.existsByCategoryId(1L)).thenReturn(false);

            categoryService.delete(1L, 1L);

            verify(categoryRepository).delete(category);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when category not found")
        void delete_notFound_throws() {
            when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.delete(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(categoryRepository, never()).delete(any(Category.class));
        }

        @Test
        @DisplayName("Should throw DeletionBlockedException when transactions reference the category")
        void delete_blockedByTransactions_throws() {
            Category category = Category.builder()
                    .id(1L).user(testUser).name("Food").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(category));
            when(transactionRepository.existsByCategoryId(1L)).thenReturn(true);

            assertThatThrownBy(() -> categoryService.delete(1L, 1L))
                    .isInstanceOf(DeletionBlockedException.class)
                    .hasMessageContaining("Food")
                    .hasMessageContaining("associated transactions");

            verify(categoryRepository, never()).delete(any(Category.class));
        }
    }
}
