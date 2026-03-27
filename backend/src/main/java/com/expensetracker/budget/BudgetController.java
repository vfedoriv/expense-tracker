package com.expensetracker.budget;

import com.expensetracker.budget.dto.BudgetRequest;
import com.expensetracker.budget.dto.BudgetSummaryResponse;
import com.expensetracker.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/{year}/{month}")
    public BudgetSummaryResponse getSummary(
        @PathVariable int year,
        @PathVariable int month,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return budgetService.getSummary(principal.getId(), year, month);
    }

    @PutMapping("/{year}/{month}")
    public ResponseEntity<BudgetSummaryResponse> setOrUpdate(
        @PathVariable int year,
        @PathVariable int month,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody BudgetRequest request
    ) {
        budgetService.setOrUpdate(principal.getId(), year, month, request.amount());
        BudgetSummaryResponse summary = budgetService.getSummary(principal.getId(), year, month);
        return ResponseEntity.ok(summary);
    }
}
