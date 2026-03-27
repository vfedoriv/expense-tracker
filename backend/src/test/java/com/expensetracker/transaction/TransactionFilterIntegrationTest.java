package com.expensetracker.transaction;

import com.expensetracker.category.CategoryRepository;
import com.expensetracker.category.dto.CategoryRequest;
import com.expensetracker.category.dto.CategoryResponse;
import com.expensetracker.transaction.dto.TransactionRequest;
import com.expensetracker.transaction.dto.TransactionResponse;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionFilterIntegrationTest {

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
    private TransactionRepository transactionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;

    private RestClient client;
    private Long foodCategoryId;
    private Long travelCategoryId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        client = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("X-User-Email", "filter@test.com")
            .build();

        foodCategoryId = createCategory("Food");
        travelCategoryId = createCategory("Travel");

        createTx("Morning Coffee", "5.00", "2026-03-01", foodCategoryId, "coffee notes");
        createTx("Taxi", "15.00", "2026-03-05", travelCategoryId, null);
        createTx("Lunch", "12.50", "2026-03-10", foodCategoryId, null);
        createTx("Flight", "250.00", "2026-02-20", travelCategoryId, "holiday trip");
    }

    @Test
    void filterBySearch_title() {
        ResponseEntity<List> response = client.get()
            .uri("/api/transactions?search=coffee")
            .retrieve().toEntity(List.class);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void filterBySearch_notes() {
        ResponseEntity<List> response = client.get()
            .uri("/api/transactions?search=holiday")
            .retrieve().toEntity(List.class);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void filterByCategory() {
        ResponseEntity<List> response = client.get()
            .uri("/api/transactions?categoryId=" + foodCategoryId)
            .retrieve().toEntity(List.class);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void filterByDateRange_thisMonth() {
        ResponseEntity<List> response = client.get()
            .uri("/api/transactions?dateFrom=2026-03-01&dateTo=2026-03-31")
            .retrieve().toEntity(List.class);
        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void filterByAmountRange() {
        ResponseEntity<List> response = client.get()
            .uri("/api/transactions?amountMin=10&amountMax=20")
            .retrieve().toEntity(List.class);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void combinedFilters() {
        ResponseEntity<List> response = client.get()
            .uri("/api/transactions?categoryId=" + travelCategoryId + "&amountMin=100")
            .retrieve().toEntity(List.class);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void noFilters_returnsAll() {
        ResponseEntity<List> response = client.get()
            .uri("/api/transactions")
            .retrieve().toEntity(List.class);
        assertThat(response.getBody()).hasSize(4);
    }

    private Long createCategory(String name) {
        CategoryResponse cat = client.post().uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON).body(new CategoryRequest(name))
            .retrieve().body(CategoryResponse.class);
        return cat.id();
    }

    private void createTx(String title, String amount, String date, Long catId, String notes) {
        client.post().uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TransactionRequest(title, new BigDecimal(amount), "USD", LocalDate.parse(date), catId, notes))
            .retrieve().body(TransactionResponse.class);
    }
}
