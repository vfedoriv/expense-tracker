package com.expensetracker.category;

import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void findAllByUser_returnsUserCategories() {
        Long userId = 1L;
        List<Category> categories = List.of(
            Category.builder().id(1L).userId(userId).name("Food").build(),
            Category.builder().id(2L).userId(userId).name("Transport").build()
        );
        when(categoryRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(categories);

        List<Category> result = categoryService.findAllByUser(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Food");
    }

    @Test
    void create_whenNameUnique_createsCategory() {
        Long userId = 1L;
        String name = "Groceries";
        Category saved = Category.builder().id(1L).userId(userId).name(name).build();
        when(categoryRepository.existsByUserIdAndName(userId, name)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        Category result = categoryService.create(userId, name);

        assertThat(result.getName()).isEqualTo(name);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_whenNameDuplicate_throwsConflict() {
        Long userId = 1L;
        String name = "Groceries";
        when(categoryRepository.existsByUserIdAndName(userId, name)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(userId, name))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Groceries");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void rename_whenNameUnique_renames() {
        Long userId = 1L;
        Long categoryId = 1L;
        Category existing = Category.builder().id(categoryId).userId(userId).name("Old").build();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndNameAndIdNot(userId, "New", categoryId)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.rename(categoryId, userId, "New");

        assertThat(result.getName()).isEqualTo("New");
    }

    @Test
    void rename_whenNewNameDuplicate_throwsConflict() {
        Long userId = 1L;
        Long categoryId = 1L;
        Category existing = Category.builder().id(categoryId).userId(userId).name("Old").build();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndNameAndIdNot(userId, "Taken", categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.rename(categoryId, userId, "Taken"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void findByIdAndUser_whenNotFound_throwsResourceNotFound() {
        when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findByIdAndUser(99L, 1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_whenExists_deletesCategory() {
        Long userId = 1L;
        Long categoryId = 1L;
        Category existing = Category.builder().id(categoryId).userId(userId).name("ToDelete").build();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCategoryId(categoryId)).thenReturn(false);

        categoryService.delete(categoryId, userId);

        verify(categoryRepository).delete(existing);
    }
}
