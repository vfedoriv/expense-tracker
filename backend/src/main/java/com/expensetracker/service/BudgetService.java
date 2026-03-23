package com.expensetracker.service;

import com.expensetracker.dto.request.BudgetRequest;
import com.expensetracker.dto.response.BudgetResponse;
import com.expensetracker.entity.MonthlyBudget;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.MonthlyBudgetRepository;
import com.expensetracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class BudgetService {

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final UserRepository userRepository;

    public BudgetService(MonthlyBudgetRepository monthlyBudgetRepository,
                         UserRepository userRepository) {
        this.monthlyBudgetRepository = monthlyBudgetRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates or updates a budget for the given user+year+month (upsert).
     * Returns the response and whether it was created (true) or updated (false).
     */
    @Transactional
    public UpsertResult createOrUpdateBudget(Long userId, BudgetRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Short year = request.year().shortValue();
        Short month = request.month().shortValue();

        Optional<MonthlyBudget> existing = monthlyBudgetRepository.findByUserIdAndYearAndMonth(userId, year, month);

        if (existing.isPresent()) {
            MonthlyBudget budget = existing.get();
            budget.setAmount(request.amount());
            budget = monthlyBudgetRepository.save(budget);
            return new UpsertResult(toResponse(budget), false);
        } else {
            MonthlyBudget budget = new MonthlyBudget();
            budget.setUser(user);
            budget.setYear(year);
            budget.setMonth(month);
            budget.setAmount(request.amount());
            budget = monthlyBudgetRepository.save(budget);
            return new UpsertResult(toResponse(budget), true);
        }
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudget(Long userId, int year, int month) {
        MonthlyBudget budget = monthlyBudgetRepository
            .findByUserIdAndYearAndMonth(userId, (short) year, (short) month)
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found for " + year + "-" + month));

        return toResponse(budget);
    }

    private BudgetResponse toResponse(MonthlyBudget budget) {
        return new BudgetResponse(
            budget.getId(),
            budget.getYear().intValue(),
            budget.getMonth().intValue(),
            budget.getAmount(),
            budget.getCreatedAt(),
            budget.getUpdatedAt()
        );
    }

    public record UpsertResult(BudgetResponse response, boolean created) {
    }
}
