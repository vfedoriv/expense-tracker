package com.expensetracker.repository;

import com.expensetracker.entity.MonthlyBudget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {

    Optional<MonthlyBudget> findByUserIdAndYearAndMonth(Long userId, Short year, Short month);

    List<MonthlyBudget> findAllByUserId(Long userId);
}
