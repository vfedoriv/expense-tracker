package com.expensetracker.transaction;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> forUser(Long userId, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));

            if (filter != null) {
                if (filter.search() != null && !filter.search().isBlank()) {
                    String pattern = "%" + filter.search().toLowerCase() + "%";
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("notes")), pattern)
                    ));
                }
                if (filter.categoryId() != null) {
                    predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
                }
                if (filter.dateFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.dateFrom()));
                }
                if (filter.dateTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), filter.dateTo()));
                }
                if (filter.amountMin() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.amountMin()));
                }
                if (filter.amountMax() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.amountMax()));
                }
            }

            query.orderBy(cb.desc(root.get("transactionDate")), cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
