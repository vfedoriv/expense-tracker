package com.expensetracker.repository;

import com.expensetracker.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findAllByUserId(Long userId);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);
}
