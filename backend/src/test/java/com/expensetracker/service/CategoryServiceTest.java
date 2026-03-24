package com.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expensetracker.dto.request.CategoryRequest;
import com.expensetracker.dto.response.CategoryResponse;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;
import com.expensetracker.exception.DuplicateResourceException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    @Mock private TransactionRepository transactionRepository;

    @Mock private UserRepository userRepository;

    @InjectMocks private CategoryService categoryService;

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
    void createCategory_success() {
        CategoryRequest request = new CategoryRequest("Food");
        when(categoryRepository.existsByUserIdAndName(1L, "Food")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        Category savedCategory = createCategoryEntity(1L, "Food");
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        CategoryResponse response = categoryService.createCategory(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Food");
        assertThat(response.createdAt()).isNotNull();
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategory_duplicateName_throwsConflict() {
        CategoryRequest request = new CategoryRequest("Food");
        when(categoryRepository.existsByUserIdAndName(1L, "Food")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void renameCategory_success() {
        CategoryRequest request = new CategoryRequest("Groceries");
        Category existing = createCategoryEntity(1L, "Food");
        existing.setUser(testUser);

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndName(1L, "Groceries")).thenReturn(false);

        Category savedCategory = createCategoryEntity(1L, "Groceries");
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        CategoryResponse response = categoryService.renameCategory(1L, 1L, request);

        assertThat(response.name()).isEqualTo("Groceries");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void renameCategory_duplicateName_throwsConflict() {
        CategoryRequest request = new CategoryRequest("Transport");
        Category existing = createCategoryEntity(1L, "Food");
        existing.setUser(testUser);

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndName(1L, "Transport")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.renameCategory(1L, 1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void renameCategory_notFound_throwsNotFound() {
        CategoryRequest request = new CategoryRequest("Groceries");
        when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.renameCategory(1L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void deleteCategory_success() {
        Category existing = createCategoryEntity(1L, "Food");
        existing.setUser(testUser);

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCategoryId(1L)).thenReturn(false);

        categoryService.deleteCategory(1L, 1L);

        verify(categoryRepository).delete(existing);
    }

    @Test
    void deleteCategory_withTransactions_throwsConflict() {
        Category existing = createCategoryEntity(1L, "Food");
        existing.setUser(testUser);

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot delete category with existing transactions");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategory_notFound_throwsNotFound() {
        when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void listCategories_returnsOnlyOwn() {
        Category cat1 = createCategoryEntity(1L, "Aaa");
        Category cat2 = createCategoryEntity(2L, "Bbb");

        when(categoryRepository.findAllByUserIdOrderByName(1L)).thenReturn(List.of(cat1, cat2));

        List<CategoryResponse> result = categoryService.listCategories(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Aaa");
        assertThat(result.get(1).name()).isEqualTo("Bbb");
    }

    @Test
    void renameCategory_sameName_success() {
        // Renaming to the same name should not throw duplicate error
        CategoryRequest request = new CategoryRequest("Food");
        Category existing = createCategoryEntity(1L, "Food");
        existing.setUser(testUser);

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));

        Category savedCategory = createCategoryEntity(1L, "Food");
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        CategoryResponse response = categoryService.renameCategory(1L, 1L, request);

        assertThat(response.name()).isEqualTo("Food");
    }

    private Category createCategoryEntity(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setCreatedAt(OffsetDateTime.now());
        category.setUpdatedAt(OffsetDateTime.now());
        return category;
    }
}
