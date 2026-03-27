package com.expensetracker.budget;

import com.expensetracker.budget.dto.BudgetRequest;
import com.expensetracker.budget.dto.BudgetSummaryResponse;
import com.expensetracker.category.CategoryRepository;
import com.expensetracker.category.dto.CategoryRequest;
import com.expensetracker.category.dto.CategoryResponse;
import com.expensetracker.transaction.TransactionRepository;
import com.expensetracker.transaction.dto.TransactionRequest;
import com.expensetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BudgetIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    private MonthlyBudgetRepository budgetRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;

    private RestClient client;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        client = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("X-User-Email", "budget@test.com")
            .build();

        CategoryResponse cat = client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("Food"))
            .retrieve()
            .body(CategoryResponse.class);
        categoryId = cat.id();
    }

    @Test
    void getSummary_withNoBudgetAndNoTransactions_returnsNoBudgetState() {
        BudgetSummaryResponse response = client.get()
            .uri("/api/budgets/2026/3")
            .retrieve()
            .body(BudgetSummaryResponse.class);

        assertThat(response.budgetSet()).isFalse();
        assertThat(response.totalSpent()).isEqualByComparingTo("0");
        assertThat(response.budget()).isNull();
    }

    @Test
    void setSummaryAndGetSummary_calculatesCorrectly() {
        // Set budget
        ResponseEntity<BudgetSummaryResponse> setBudgetResponse = client.put()
            .uri("/api/budgets/2026/3")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new BudgetRequest(new BigDecimal("500.00")))
            .retrieve()
            .toEntity(BudgetSummaryResponse.class);

        assertThat(setBudgetResponse.getBody().budgetSet()).isTrue();
        assertThat(setBudgetResponse.getBody().budget()).isEqualByComparingTo("500.00");
        assertThat(setBudgetResponse.getBody().totalSpent()).isEqualByComparingTo("0");

        // Add transaction
        client.post()
            .uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TransactionRequest("Groceries", new BigDecimal("200.00"), "USD",
                LocalDate.of(2026, 3, 15), categoryId, null))
            .retrieve()
            .body(Void.class);

        // Get updated summary
        BudgetSummaryResponse summary = client.get()
            .uri("/api/budgets/2026/3")
            .retrieve()
            .body(BudgetSummaryResponse.class);

        assertThat(summary.totalSpent()).isEqualByComparingTo("200.00");
        assertThat(summary.remaining()).isEqualByComparingTo("300.00");
        assertThat(summary.usagePercent()).isEqualTo(40.0);
    }

    @Test
    void updateBudget_updatesExistingEntry() {
        client.put()
            .uri("/api/budgets/2026/3")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new BudgetRequest(new BigDecimal("500.00")))
            .retrieve()
            .body(BudgetSummaryResponse.class);

        BudgetSummaryResponse updated = client.put()
            .uri("/api/budgets/2026/3")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new BudgetRequest(new BigDecimal("800.00")))
            .retrieve()
            .body(BudgetSummaryResponse.class);

        assertThat(updated.budget()).isEqualByComparingTo("800.00");
        assertThat(budgetRepository.count()).isEqualTo(1);
    }
}
