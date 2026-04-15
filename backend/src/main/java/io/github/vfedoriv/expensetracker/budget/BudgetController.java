package io.github.vfedoriv.expensetracker.budget;

import io.github.vfedoriv.expensetracker.auth.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final UserContext userContext;

    @GetMapping("/{year}/{month}")
    public BudgetSummaryResponse getSummary(@PathVariable int year,
                                            @PathVariable int month) {
        return budgetService.getSummary(userContext.getUserId(), year, month);
    }

    @PutMapping("/{year}/{month}")
    public BudgetSummaryResponse setBudget(@PathVariable int year,
                                           @PathVariable int month,
                                           @Valid @RequestBody BudgetRequest request) {
        return budgetService.setBudget(userContext.getUserId(), year, month, request);
    }

    @DeleteMapping("/{year}/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBudget(@PathVariable int year,
                             @PathVariable int month) {
        budgetService.deleteBudget(userContext.getUserId(), year, month);
    }
}
