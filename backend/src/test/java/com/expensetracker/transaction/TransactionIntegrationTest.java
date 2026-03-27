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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionIntegrationTest {

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

    private RestClient user1Client;
    private RestClient user2Client;
    private Long user1CategoryId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        user1Client = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("X-User-Email", "user1@test.com")
            .build();

        user2Client = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("X-User-Email", "user2@test.com")
            .build();

        CategoryResponse cat = user1Client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("Food"))
            .retrieve()
            .body(CategoryResponse.class);
        user1CategoryId = cat.id();
    }

    private TransactionRequest txRequest() {
        return new TransactionRequest("Coffee", new BigDecimal("3.50"), "USD", LocalDate.now(), user1CategoryId, "Test note");
    }

    @Test
    void createTransaction_returnsCreatedTransaction() {
        ResponseEntity<TransactionResponse> response = user1Client.post()
            .uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(txRequest())
            .retrieve()
            .toEntity(TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().title()).isEqualTo("Coffee");
        assertThat(response.getBody().amount()).isEqualByComparingTo("3.50");
        assertThat(response.getBody().currency()).isEqualTo("USD");
    }

    @Test
    void createTransaction_withInvalidCategory_returns404() {
        TransactionRequest badReq = new TransactionRequest("Coffee", new BigDecimal("3.50"), "USD",
            LocalDate.now(), 99999L, null);

        assertThatThrownBy(() -> user1Client.post()
            .uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(badReq)
            .retrieve()
            .body(TransactionResponse.class))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void updateTransaction_updatesFields() {
        TransactionResponse created = user1Client.post()
            .uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(txRequest())
            .retrieve()
            .body(TransactionResponse.class);

        TransactionRequest updateReq = new TransactionRequest("Tea", new BigDecimal("2.00"), "USD",
            LocalDate.now(), user1CategoryId, "Updated note");

        TransactionResponse updated = user1Client.put()
            .uri("/api/transactions/" + created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .body(updateReq)
            .retrieve()
            .body(TransactionResponse.class);

        assertThat(updated.title()).isEqualTo("Tea");
        assertThat(updated.amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void deleteTransaction_removesIt() {
        TransactionResponse created = user1Client.post()
            .uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(txRequest())
            .retrieve()
            .body(TransactionResponse.class);

        ResponseEntity<Void> deleteResponse = user1Client.delete()
            .uri("/api/transactions/" + created.id())
            .retrieve()
            .toEntity(Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void crossUserAccess_denied() {
        TransactionResponse user1Tx = user1Client.post()
            .uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(txRequest())
            .retrieve()
            .body(TransactionResponse.class);

        assertThatThrownBy(() -> user2Client.delete()
            .uri("/api/transactions/" + user1Tx.id())
            .retrieve()
            .toEntity(Void.class))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void deleteCategory_withTransactions_blockedWith409() {
        user1Client.post()
            .uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(txRequest())
            .retrieve()
            .body(TransactionResponse.class);

        assertThatThrownBy(() -> user1Client.delete()
            .uri("/api/categories/" + user1CategoryId)
            .retrieve()
            .toEntity(Void.class))
            .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    void getTransactions_searchByTitle() {
        user1Client.post().uri("/api/transactions").contentType(MediaType.APPLICATION_JSON)
            .body(new TransactionRequest("Morning Coffee", new BigDecimal("3.50"), "USD", LocalDate.now(), user1CategoryId, null))
            .retrieve().body(TransactionResponse.class);
        user1Client.post().uri("/api/transactions").contentType(MediaType.APPLICATION_JSON)
            .body(new TransactionRequest("Lunch", new BigDecimal("12.00"), "USD", LocalDate.now(), user1CategoryId, null))
            .retrieve().body(TransactionResponse.class);

        ResponseEntity<List> response = user1Client.get()
            .uri("/api/transactions?search=coffee")
            .retrieve()
            .toEntity(List.class);

        assertThat(response.getBody()).hasSize(1);
    }
}
