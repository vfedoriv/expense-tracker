package io.github.vfedoriv.expensetracker.category;

import io.github.vfedoriv.expensetracker.exception.DeletionBlockedException;
import io.github.vfedoriv.expensetracker.exception.DuplicateResourceException;
import io.github.vfedoriv.expensetracker.exception.ResourceNotFoundException;
import io.github.vfedoriv.expensetracker.transaction.TransactionRepository;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public List<CategoryResponse> getAllByUser(Long userId) {
        return categoryRepository.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new DuplicateResourceException(
                    "Category with name '" + request.name() + "' already exists");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = Category.builder()
                .user(user)
                .name(request.name())
                .build();

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));

        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, request.name(), id)) {
            throw new DuplicateResourceException(
                    "Category with name '" + request.name() + "' already exists");
        }

        category.setName(request.name());
        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));

        if (transactionRepository.existsByCategoryId(category.getId())) {
            throw new DeletionBlockedException(
                    "Cannot delete category '" + category.getName()
                    + "' because it has associated transactions");
        }

        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getCreatedAt()
        );
    }
}
