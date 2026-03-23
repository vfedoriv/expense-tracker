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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BudgetAlertService budgetAlertService;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              UserRepository userRepository,
                              BudgetAlertService budgetAlertService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.budgetAlertService = budgetAlertService;
    }

    @Transactional
    public TransactionResponse createTransaction(Long userId, TransactionRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setTitle(request.title());
        transaction.setAmount(request.amount());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setNotes(request.notes());

        transaction = transactionRepository.save(transaction);

        budgetAlertService.evaluateAlerts(userId);

        return toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(Long userId, String search, Long categoryId,
                                                       LocalDate dateFrom, LocalDate dateTo,
                                                       BigDecimal amountMin, BigDecimal amountMax) {
        return transactionRepository.findAllByUserIdWithFilters(
                userId, search, categoryId, dateFrom, dateTo, amountMin, amountMax)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public TransactionResponse updateTransaction(Long userId, Long transactionId, TransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        transaction.setTitle(request.title());
        transaction.setAmount(request.amount());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setCategory(category);
        transaction.setNotes(request.notes());

        transaction = transactionRepository.save(transaction);

        budgetAlertService.evaluateAlerts(userId);

        return toResponse(transaction);
    }

    @Transactional
    public void deleteTransaction(Long userId, Long transactionId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        transactionRepository.delete(transaction);

        budgetAlertService.evaluateAlerts(userId);
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getTitle(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getTransactionDate(),
            transaction.getCategory().getId(),
            transaction.getCategory().getName(),
            transaction.getNotes(),
            transaction.getCreatedAt()
        );
    }
}
