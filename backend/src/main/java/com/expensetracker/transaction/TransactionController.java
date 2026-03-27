package com.expensetracker.transaction;

import com.expensetracker.security.UserPrincipal;
import com.expensetracker.transaction.dto.TransactionRequest;
import com.expensetracker.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public List<TransactionResponse> getAll(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) BigDecimal amountMin,
        @RequestParam(required = false) BigDecimal amountMax
    ) {
        TransactionFilter filter = new TransactionFilter(search, categoryId, dateFrom, dateTo, amountMin, amountMax);
        return transactionService.findAll(principal.getId(), filter)
            .stream()
            .map(TransactionResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return TransactionResponse.from(transactionService.findByIdAndUser(id, principal.getId()));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody TransactionRequest request
    ) {
        Transaction transaction = transactionService.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody TransactionRequest request
    ) {
        Transaction transaction = transactionService.update(id, principal.getId(), request);
        return TransactionResponse.from(transaction);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        transactionService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
