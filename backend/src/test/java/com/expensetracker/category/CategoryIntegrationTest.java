package com.expensetracker.category;

import com.expensetracker.category.dto.CategoryRequest;
import com.expensetracker.category.dto.CategoryResponse;
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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryIntegrationTest {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private RestClient user1Client;
    private RestClient user2Client;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void createCategory_returnsCreatedCategory() {
        CategoryResponse response = user1Client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("Food"))
            .retrieve()
            .body(CategoryResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Food");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void createCategory_duplicateName_returnsConflict() {
        user1Client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("Food"))
            .retrieve()
            .body(CategoryResponse.class);

        assertThatThrownBy(() -> user1Client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("Food"))
            .retrieve()
            .body(CategoryResponse.class))
            .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    void getCategories_returnsOnlyUserCategories() {
        user1Client.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("User1 Category")).retrieve().body(CategoryResponse.class);
        user2Client.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("User2 Category")).retrieve().body(CategoryResponse.class);

        ResponseEntity<List> response = user1Client.get()
            .uri("/api/categories")
            .retrieve()
            .toEntity(List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void renameCategory_updatesName() {
        CategoryResponse created = user1Client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("Old Name"))
            .retrieve()
            .body(CategoryResponse.class);

        CategoryResponse renamed = user1Client.put()
            .uri("/api/categories/" + created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("New Name"))
            .retrieve()
            .body(CategoryResponse.class);

        assertThat(renamed.name()).isEqualTo("New Name");
    }

    @Test
    void deleteCategory_removesIt() {
        CategoryResponse created = user1Client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("ToDelete"))
            .retrieve()
            .body(CategoryResponse.class);

        ResponseEntity<Void> deleteResponse = user1Client.delete()
            .uri("/api/categories/" + created.id())
            .retrieve()
            .toEntity(Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> listResponse = user1Client.get()
            .uri("/api/categories")
            .retrieve()
            .toEntity(List.class);
        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void userIsolation_cannotAccessOtherUserCategory() {
        CategoryResponse user1Cat = user1Client.post()
            .uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CategoryRequest("User1 Only"))
            .retrieve()
            .body(CategoryResponse.class);

        assertThatThrownBy(() -> user2Client.delete()
            .uri("/api/categories/" + user1Cat.id())
            .retrieve()
            .toEntity(Void.class))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
