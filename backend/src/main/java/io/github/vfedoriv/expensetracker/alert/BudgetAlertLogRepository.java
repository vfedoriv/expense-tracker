package io.github.vfedoriv.expensetracker.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetAlertLogRepository extends JpaRepository<BudgetAlertLog, Long> {

    boolean existsByUserIdAndYearAndMonthAndThreshold(Long userId, int year, int month, int threshold);

    Optional<BudgetAlertLog> findByUserIdAndYearAndMonthAndThreshold(Long userId, int year, int month, int threshold);

    List<BudgetAlertLog> findByUserIdAndYearAndMonthAndAcknowledgedFalse(Long userId, int year, int month);
}
