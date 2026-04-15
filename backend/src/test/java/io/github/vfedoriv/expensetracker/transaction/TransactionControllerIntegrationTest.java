package io.github.vfedoriv.expensetracker.transaction;

import tools.jackson.databind.ObjectMapper;
import io.github.vfedoriv.expensetracker.TestcontainersConfig;
import io.github.vfedoriv.expensetracker.category.CategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransactionControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        categoryId = createCategoryViaApi("Food");
    }

    // --- CRUD Tests ---

    @Test
    void createTransaction_shouldReturn201() throws Exception {
        String body = transactionJson("Lunch", "12.50", categoryId, "2026-03-15");

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Lunch")))
                .andExpect(jsonPath("$.amount", is(12.50)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.categoryId", is(categoryId.intValue())))
                .andExpect(jsonPath("$.categoryName", is("Food")))
                .andExpect(jsonPath("$.transactionDate", is("2026-03-15")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    void getTransactions_shouldReturnPaginatedResults() throws Exception {
        // Create 3 transactions
        createTransactionViaApi("Lunch", "12.50", categoryId, "2026-03-15");
        createTransactionViaApi("Dinner", "25.00", categoryId, "2026-03-15");
        createTransactionViaApi("Coffee", "5.00", categoryId, "2026-03-16");

        mockMvc.perform(get("/api/transactions")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void getTransactions_defaultPagination_shouldReturnAll() throws Exception {
        createTransactionViaApi("Lunch", "12.50", categoryId, "2026-03-15");
        createTransactionViaApi("Dinner", "25.00", categoryId, "2026-03-15");

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void updateTransaction_shouldUpdateFields() throws Exception {
        Long txId = createTransactionViaApi("Lunch", "12.50", categoryId, "2026-03-15");

        Long newCategoryId = createCategoryViaApi("Dining");
        String updateBody = transactionJson("Business Lunch", "35.00", newCategoryId, "2026-03-16");

        mockMvc.perform(put("/api/transactions/{id}", txId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(txId.intValue())))
                .andExpect(jsonPath("$.title", is("Business Lunch")))
                .andExpect(jsonPath("$.amount", is(35.00)))
                .andExpect(jsonPath("$.categoryId", is(newCategoryId.intValue())))
                .andExpect(jsonPath("$.categoryName", is("Dining")))
                .andExpect(jsonPath("$.transactionDate", is("2026-03-16")));
    }

    @Test
    void updateTransaction_notFound_shouldReturn404() throws Exception {
        String body = transactionJson("Lunch", "12.50", categoryId, "2026-03-15");

        mockMvc.perform(put("/api/transactions/{id}", 99999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTransaction_shouldReturn204() throws Exception {
        Long txId = createTransactionViaApi("Lunch", "12.50", categoryId, "2026-03-15");

        mockMvc.perform(delete("/api/transactions/{id}", txId))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void deleteTransaction_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/transactions/{id}", 99999))
                .andExpect(status().isNotFound());
    }

    // --- Search / Filter Tests ---

    @Test
    void searchByQuery_shouldFilterByTitleOrNotes() throws Exception {
        createTransactionViaApi("Lunch at restaurant", "12.50", categoryId, "2026-03-15");
        createTransactionViaApi("Coffee beans", "5.00", categoryId, "2026-03-15");
        createTransactionViaApi("Dinner", "25.00", categoryId, "2026-03-15");

        mockMvc.perform(get("/api/transactions")
                        .param("q", "lunch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Lunch at restaurant")));
    }

    @Test
    void searchByCategoryId_shouldFilterByCategory() throws Exception {
        Long transportId = createCategoryViaApi("Transport");
        createTransactionViaApi("Lunch", "12.50", categoryId, "2026-03-15");
        createTransactionViaApi("Bus fare", "3.00", transportId, "2026-03-15");

        mockMvc.perform(get("/api/transactions")
                        .param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Lunch")));
    }

    @Test
    void searchByDateRange_shouldFilterByDates() throws Exception {
        createTransactionViaApi("Early", "10.00", categoryId, "2026-03-01");
        createTransactionViaApi("Mid", "20.00", categoryId, "2026-03-15");
        createTransactionViaApi("Late", "30.00", categoryId, "2026-03-28");

        mockMvc.perform(get("/api/transactions")
                        .param("dateFrom", "2026-03-10")
                        .param("dateTo", "2026-03-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Mid")));
    }

    @Test
    void searchByAmountRange_shouldFilter() throws Exception {
        createTransactionViaApi("Small", "5.00", categoryId, "2026-03-15");
        createTransactionViaApi("Medium", "50.00", categoryId, "2026-03-15");
        createTransactionViaApi("Large", "500.00", categoryId, "2026-03-15");

        mockMvc.perform(get("/api/transactions")
                        .param("amountMin", "10")
                        .param("amountMax", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Medium")));
    }

    @Test
    void searchCombinedFilters_shouldApplyAll() throws Exception {
        Long transportId = createCategoryViaApi("Transport");
        createTransactionViaApi("Lunch", "12.50", categoryId, "2026-03-15");
        createTransactionViaApi("Bus fare", "3.00", transportId, "2026-03-15");
        createTransactionViaApi("Expensive dinner", "120.00", categoryId, "2026-03-20");

        mockMvc.perform(get("/api/transactions")
                        .param("categoryId", categoryId.toString())
                        .param("amountMax", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Lunch")));
    }

    // --- Validation Tests ---

    @Test
    void createTransaction_missingTitle_shouldReturn400() throws Exception {
        String body = """
                {
                    "title": "",
                    "amount": 12.50,
                    "currency": "USD",
                    "categoryId": %d,
                    "transactionDate": "2026-03-15"
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void createTransaction_zeroAmount_shouldReturn400() throws Exception {
        String body = """
                {
                    "title": "Lunch",
                    "amount": 0,
                    "currency": "USD",
                    "categoryId": %d,
                    "transactionDate": "2026-03-15"
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')]", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void createTransaction_negativeAmount_shouldReturn400() throws Exception {
        String body = """
                {
                    "title": "Refund",
                    "amount": -10.00,
                    "currency": "USD",
                    "categoryId": %d,
                    "transactionDate": "2026-03-15"
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')]", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void createTransaction_missingCategory_shouldReturn400() throws Exception {
        String body = """
                {
                    "title": "Lunch",
                    "amount": 12.50,
                    "currency": "USD",
                    "transactionDate": "2026-03-15"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'categoryId')]", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void createTransaction_nonExistentCategory_shouldReturn404() throws Exception {
        String body = transactionJson("Lunch", "12.50", 99999L, "2026-03-15");

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---

    private Long createCategoryViaApi(String name) throws Exception {
        String body = objectMapper.writeValueAsString(new CategoryRequest(name));

        MvcResult result = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private Long createTransactionViaApi(String title, String amount, Long catId, String date) throws Exception {
        String body = transactionJson(title, amount, catId, date);

        MvcResult result = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private String transactionJson(String title, String amount, Long catId, String date) {
        return """
                {
                    "title": "%s",
                    "amount": %s,
                    "currency": "USD",
                    "categoryId": %d,
                    "transactionDate": "%s"
                }
                """.formatted(title, amount, catId, date);
    }
}
