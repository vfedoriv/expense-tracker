package com.expensetracker.transaction;

import com.expensetracker.category.CategoryRepository;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.transaction.dto.TransactionRequest;
import com.expensetracker.websocket.TransactionChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Transaction> findAll(Long userId, TransactionFilter filter) {
        Specification<Transaction> spec = TransactionSpecification.forUser(userId, filter);
        return transactionRepository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public Transaction findByIdAndUser(Long id, Long userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    public Transaction create(Long userId, TransactionRequest request) {
        validateCategoryBelongsToUser(request.categoryId(), userId);
        Transaction transaction = Transaction.builder()
            .userId(userId)
            .categoryId(request.categoryId())
            .title(request.title())
            .amount(request.amount())
            .currency(request.currency())
            .transactionDate(request.transactionDate())
            .notes(request.notes())
            .build();
        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId, saved.getTransactionDate()));
        return saved;
    }

    public Transaction update(Long id, Long userId, TransactionRequest request) {
        Transaction transaction = findByIdAndUser(id, userId);
        LocalDate oldDate = transaction.getTransactionDate();
        validateCategoryBelongsToUser(request.categoryId(), userId);
        transaction.setTitle(request.title());
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setCategoryId(request.categoryId());
        transaction.setNotes(request.notes());
        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId, oldDate, saved.getTransactionDate()));
        return saved;
    }

    public void delete(Long id, Long userId) {
        Transaction transaction = findByIdAndUser(id, userId);
        transactionRepository.delete(transaction);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId, transaction.getTransactionDate()));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumForMonth(Long userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return transactionRepository.sumAmountByUserIdAndDateBetween(userId, from, to);
    }

    private void validateCategoryBelongsToUser(Long categoryId, Long userId) {
        if (!categoryRepository.existsByIdAndUserId(categoryId, userId)) {
            throw new ResourceNotFoundException("Category not found: " + categoryId);
        }
    }
}
