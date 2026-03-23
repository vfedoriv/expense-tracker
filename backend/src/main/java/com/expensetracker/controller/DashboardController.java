package com.expensetracker.controller;

import com.expensetracker.config.UserPrincipal;
import com.expensetracker.dto.response.DashboardResponse;
import com.expensetracker.service.DashboardService;
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
            @RequestParam int year,
            @RequestParam int month) {
        DashboardResponse response = dashboardService.getDashboard(principal.getUserId(), year, month);
        return ResponseEntity.ok(response);
    }
}
