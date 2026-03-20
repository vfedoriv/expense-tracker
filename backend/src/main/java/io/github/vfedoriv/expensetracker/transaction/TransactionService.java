package io.github.vfedoriv.expensetracker.transaction;

import io.github.vfedoriv.expensetracker.category.Category;
import io.github.vfedoriv.expensetracker.category.CategoryRepository;
import io.github.vfedoriv.expensetracker.exception.ResourceNotFoundException;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Page<TransactionResponse> search(Long userId,
                                             String query,
                                             Long categoryId,
                                             LocalDate dateFrom,
                                             LocalDate dateTo,
                                             BigDecimal amountMin,
                                             BigDecimal amountMax,
                                             Pageable pageable) {

        Specification<Transaction> spec = TransactionSpecification.build(
                userId, query, categoryId, dateFrom, dateTo, amountMin, amountMax);

        return transactionRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.categoryId()));

        Transaction transaction = Transaction.builder()
                .user(user)
                .category(category)
                .title(request.title())
                .amount(request.amount())
                .currency(request.currency())
                .transactionDate(request.transactionDate())
                .notes(request.notes())
                .build();

        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId));
        return toResponse(saved);
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        Transaction transaction = findByIdAndUserId(id, userId);

        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.categoryId()));

        transaction.setCategory(category);
        transaction.setTitle(request.title());
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setNotes(request.notes());

        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId));
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transaction transaction = findByIdAndUserId(id, userId);
        transactionRepository.delete(transaction);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId));
    }

    private Transaction findByIdAndUserId(Long id, Long userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with id: " + id));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTitle(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCategory().getId(),
                transaction.getCategory().getName(),
                transaction.getTransactionDate(),
                transaction.getNotes(),
                transaction.getCreatedAt()
        );
    }
}
