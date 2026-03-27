package com.expensetracker.websocket;

import com.expensetracker.budget.MonthlyBudgetRepository;
import com.expensetracker.budget.dto.BudgetRequest;
import com.expensetracker.category.CategoryRepository;
import com.expensetracker.category.dto.CategoryRequest;
import com.expensetracker.category.dto.CategoryResponse;
import com.expensetracker.transaction.TransactionRepository;
import com.expensetracker.transaction.dto.TransactionRequest;
import com.expensetracker.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BudgetAlertWebSocketIntegrationTest {

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
    private MonthlyBudgetRepository budgetRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BudgetThresholdTracker thresholdTracker;

    private RestClient restClient;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        restClient = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("X-User-Email", "ws@test.com")
            .build();

        CategoryResponse cat = restClient.post().uri("/api/categories")
            .contentType(MediaType.APPLICATION_JSON).body(new CategoryRequest("Test"))
            .retrieve().body(CategoryResponse.class);
        categoryId = cat.id();
    }

    @Test
    void budgetAlerts_50_80_100_firedOnceEach() throws Exception {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        // Set budget of 100
        restClient.put().uri("/api/budgets/" + year + "/" + month)
            .contentType(MediaType.APPLICATION_JSON).body(new BudgetRequest(new BigDecimal("100.00")))
            .retrieve().body(Void.class);

        BlockingQueue<BudgetAlertMessage> alerts = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient = buildStompClient();
        StompSession session = connectStompSession(stompClient, alerts, "ws@test.com");

        try {
            // Subscribe first with no spending - no alert expected
            sendSubscribe(session, year, month);
            BudgetAlertMessage noInitial = alerts.poll(1, TimeUnit.SECONDS);
            assertThat(noInitial).isNull();

            // Add 50% transaction - async event fires 50% alert automatically
            addTransaction("50.00");
            BudgetAlertMessage alert50 = alerts.poll(5, TimeUnit.SECONDS);
            assertThat(alert50).isNotNull();
            assertThat(alert50.threshold()).isEqualTo(50);

            // Add to 80% - async event fires 80% alert
            addTransaction("30.00");
            BudgetAlertMessage alert80 = alerts.poll(5, TimeUnit.SECONDS);
            assertThat(alert80).isNotNull();
            assertThat(alert80.threshold()).isEqualTo(80);

            // Add to 100% - async event fires 100% alert
            addTransaction("20.00");
            BudgetAlertMessage alert100 = alerts.poll(5, TimeUnit.SECONDS);
            assertThat(alert100).isNotNull();
            assertThat(alert100.threshold()).isEqualTo(100);

            // No more alerts expected after all thresholds consumed
            BudgetAlertMessage noAlert = alerts.poll(1, TimeUnit.SECONDS);
            assertThat(noAlert).isNull();
        } finally {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private void sendSubscribe(StompSession session, int year, int month) {
        String monthStr = year + "-" + String.format("%02d", month);
        session.send("/app/budget-alerts/subscribe",
            new BudgetSubscribeMessage("subscribe", monthStr));
    }

    private WebSocketStompClient buildStompClient() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);

        SockJsClient sockJsClient = new SockJsClient(List.of(
            new WebSocketTransport(new StandardWebSocketClient()),
            new RestTemplateXhrTransport()
        ));
        WebSocketStompClient client = new WebSocketStompClient(sockJsClient);
        client.setMessageConverter(converter);
        return client;
    }

    private StompSession connectStompSession(WebSocketStompClient client,
                                              BlockingQueue<BudgetAlertMessage> alerts,
                                              String email) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("X-User-Email", email);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("X-User-Email", email);

        StompSessionHandler handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/user/topic/budget-alerts", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return BudgetAlertMessage.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        alerts.offer((BudgetAlertMessage) payload);
                    }
                });
            }
        };

        return client.connectAsync(
            "http://localhost:" + port + "/ws",
            headers, connectHeaders, handler
        ).get(10, TimeUnit.SECONDS);
    }

    private void addTransaction(String amount) {
        restClient.post().uri("/api/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TransactionRequest("Test", new BigDecimal(amount), "USD",
                LocalDate.now(), categoryId, null))
            .retrieve().body(Void.class);
    }
}
