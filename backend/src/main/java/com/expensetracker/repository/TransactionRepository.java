package com.expensetracker.repository;

import com.expensetracker.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findAllByUserId(Long userId);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);

    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId "
                    + "AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate")
    BigDecimal sumAmountByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(
            value =
                    "SELECT t.*, c.name AS cat_name FROM transactions t"
                            + " JOIN categories c ON t.category_id = c.id"
                            + " WHERE t.user_id = :userId"
                            + " AND (:search IS NULL"
                            + " OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))"
                            + " OR (t.notes IS NOT NULL"
                            + " AND LOWER(t.notes::text) LIKE LOWER(CONCAT('%', :search, '%'))))"
                            + " AND (:categoryId IS NULL OR t.category_id = :categoryId)"
                            + " AND (CAST(:dateFrom AS date) IS NULL OR t.transaction_date >= :dateFrom)"
                            + " AND (CAST(:dateTo AS date) IS NULL OR t.transaction_date <= :dateTo)"
                            + " AND (CAST(:amountMin AS numeric) IS NULL OR t.amount >= :amountMin)"
                            + " AND (CAST(:amountMax AS numeric) IS NULL OR t.amount <= :amountMax)"
                            + " ORDER BY t.transaction_date DESC, t.id DESC",
            nativeQuery = true)
    List<Transaction> findAllByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("amountMin") BigDecimal amountMin,
            @Param("amountMax") BigDecimal amountMax);
}
