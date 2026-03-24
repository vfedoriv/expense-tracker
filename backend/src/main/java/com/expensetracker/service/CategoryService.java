package com.expensetracker.service;

import com.expensetracker.dto.request.CategoryRequest;
import com.expensetracker.dto.response.CategoryResponse;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;
import com.expensetracker.exception.DuplicateResourceException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Long userId) {
        return categoryRepository.findAllByUserIdOrderByName(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(Long userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndName(userId, request.name())) {
            throw new DuplicateResourceException(
                    "Category with name '" + request.name() + "' already exists");
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = new Category();
        category.setUser(user);
        category.setName(request.name());

        try {
            category = categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(
                    "Category with name '" + request.name() + "' already exists");
        }

        return toResponse(category);
    }

    @Transactional
    public CategoryResponse renameCategory(Long userId, Long categoryId, CategoryRequest request) {
        Category category =
                categoryRepository
                        .findByIdAndUserId(categoryId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getName().equals(request.name())
                && categoryRepository.existsByUserIdAndName(userId, request.name())) {
            throw new DuplicateResourceException(
                    "Category with name '" + request.name() + "' already exists");
        }

        category.setName(request.name());

        try {
            category = categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(
                    "Category with name '" + request.name() + "' already exists");
        }

        return toResponse(category);
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category =
                categoryRepository
                        .findByIdAndUserId(categoryId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (transactionRepository.existsByCategoryId(category.getId())) {
            throw new IllegalStateException("Cannot delete category with existing transactions");
        }

        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
    }
}
