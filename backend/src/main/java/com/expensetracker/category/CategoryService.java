package com.expensetracker.category;

import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<Category> findAllByUser(Long userId) {
        return categoryRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public Category findByIdAndUser(Long id, Long userId) {
        return categoryRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    public Category create(Long userId, String name) {
        if (categoryRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException("Category with name '" + name + "' already exists");
        }
        return categoryRepository.save(
            Category.builder().userId(userId).name(name).build()
        );
    }

    public Category rename(Long id, Long userId, String newName) {
        Category category = findByIdAndUser(id, userId);
        if (categoryRepository.existsByUserIdAndNameAndIdNot(userId, newName, id)) {
            throw new ConflictException("Category with name '" + newName + "' already exists");
        }
        category.setName(newName);
        return categoryRepository.save(category);
    }

    public void delete(Long id, Long userId) {
        Category category = findByIdAndUser(id, userId);
        if (transactionRepository.existsByCategoryId(id)) {
            throw new ConflictException("Cannot delete category: it has associated transactions");
        }
        categoryRepository.delete(category);
    }
}
