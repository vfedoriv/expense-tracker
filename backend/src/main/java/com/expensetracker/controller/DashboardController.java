package com.expensetracker.controller;

import com.expensetracker.config.UserPrincipal;
import com.expensetracker.dto.response.DashboardResponse;
import com.expensetracker.service.DashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam
                    @Min(value = 2000, message = "Year must be between 2000 and 2100")
                    @Max(value = 2100, message = "Year must be between 2000 and 2100")
                    int year,
            @RequestParam
                    @Min(value = 1, message = "Month must be between 1 and 12")
                    @Max(value = 12, message = "Month must be between 1 and 12")
                    int month) {
        DashboardResponse response =
                dashboardService.getDashboard(principal.getUserId(), year, month);
        return ResponseEntity.ok(response);
    }
}
