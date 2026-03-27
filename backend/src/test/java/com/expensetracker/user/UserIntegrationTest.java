package com.expensetracker.user;

import com.expensetracker.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserIntegrationTest {

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
    private UserRepository userRepository;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        userRepository.deleteAll();
    }

    @Test
    void getCurrentUser_withDefaultFakeAuth_returnsUserProfile() {
        ResponseEntity<UserResponse> response = restClient.get()
            .uri("/api/users/me")
            .retrieve()
            .toEntity(UserResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("admin@test.com");
        assertThat(response.getBody().provider()).isEqualTo("fake");
    }

    @Test
    void getCurrentUser_withCustomEmail_createsUserWithThatEmail() {
        ResponseEntity<UserResponse> response = restClient.get()
            .uri("/api/users/me")
            .header("X-User-Email", "custom@test.com")
            .retrieve()
            .toEntity(UserResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("custom@test.com");
        assertThat(response.getBody().provider()).isEqualTo("fake");
        assertThat(response.getBody().displayName()).isEqualTo("custom");
    }

    @Test
    void getCurrentUser_calledTwiceWithSameEmail_doesNotCreateDuplicates() {
        ResponseEntity<UserResponse> first = restClient.get()
            .uri("/api/users/me")
            .header("X-User-Email", "dedup@test.com")
            .retrieve()
            .toEntity(UserResponse.class);

        ResponseEntity<UserResponse> second = restClient.get()
            .uri("/api/users/me")
            .header("X-User-Email", "dedup@test.com")
            .retrieve()
            .toEntity(UserResponse.class);

        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(first.getBody().id()).isEqualTo(second.getBody().id());
    }
}
