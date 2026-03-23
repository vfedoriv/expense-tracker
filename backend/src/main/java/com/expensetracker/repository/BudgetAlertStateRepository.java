package com.expensetracker.repository;

import com.expensetracker.entity.BudgetAlertState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BudgetAlertStateRepository extends JpaRepository<BudgetAlertState, Long> {

    Optional<BudgetAlertState> findByUserIdAndYearAndMonth(Long userId, Short year, Short month);
}
