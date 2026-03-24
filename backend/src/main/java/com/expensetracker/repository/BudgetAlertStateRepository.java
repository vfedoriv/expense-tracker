package com.expensetracker.repository;

import com.expensetracker.entity.BudgetAlertState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetAlertStateRepository extends JpaRepository<BudgetAlertState, Long> {

    Optional<BudgetAlertState> findByUserIdAndYearAndMonth(Long userId, Short year, Short month);
}
