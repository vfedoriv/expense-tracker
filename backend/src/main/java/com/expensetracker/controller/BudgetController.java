package com.expensetracker.controller;

import com.expensetracker.config.UserPrincipal;
import com.expensetracker.dto.request.BudgetRequest;
import com.expensetracker.dto.response.BudgetResponse;
import com.expensetracker.service.BudgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> createOrUpdateBudget(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BudgetRequest request) {
        BudgetService.UpsertResult result = budgetService.createOrUpdateBudget(principal.getUserId(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping
    public ResponseEntity<BudgetResponse> getBudget(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @Min(value = 2000, message = "Year must be between 2000 and 2100") @Max(value = 2100, message = "Year must be between 2000 and 2100") int year,
            @RequestParam @Min(value = 1, message = "Month must be between 1 and 12") @Max(value = 12, message = "Month must be between 1 and 12") int month) {
        BudgetResponse response = budgetService.getBudget(principal.getUserId(), year, month);
        return ResponseEntity.ok(response);
    }
}
