package io.github.vfedoriv.expensetracker.transaction;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TransactionSpecification {

    private TransactionSpecification() {
        // utility class
    }

    public static Specification<Transaction> build(Long userId,
                                                    String query,
                                                    Long categoryId,
                                                    LocalDate dateFrom,
                                                    LocalDate dateTo,
                                                    BigDecimal amountMin,
                                                    BigDecimal amountMax) {

        Specification<Transaction> spec = (root, cq, cb) ->
                cb.equal(root.get("user").get("id"), userId);

        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("notes")), pattern)
            ));
        }

        if (categoryId != null) {
            spec = spec.and((root, cq, cb) ->
                    cb.equal(root.get("category").get("id"), categoryId));
        }

        if (dateFrom != null) {
            spec = spec.and((root, cq, cb) ->
                    cb.greaterThanOrEqualTo(root.get("transactionDate"), dateFrom));
        }

        if (dateTo != null) {
            spec = spec.and((root, cq, cb) ->
                    cb.lessThanOrEqualTo(root.get("transactionDate"), dateTo));
        }

        if (amountMin != null) {
            spec = spec.and((root, cq, cb) ->
                    cb.greaterThanOrEqualTo(root.get("amount"), amountMin));
        }

        if (amountMax != null) {
            spec = spec.and((root, cq, cb) ->
                    cb.lessThanOrEqualTo(root.get("amount"), amountMax));
        }

        return spec;
    }
}
