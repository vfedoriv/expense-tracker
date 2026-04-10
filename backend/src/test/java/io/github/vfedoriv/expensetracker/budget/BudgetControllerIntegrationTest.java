package io.github.vfedoriv.expensetracker.budget;

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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
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
class BudgetControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void setBudget_shouldCreateAndReturnSummary() throws Exception {
        String body = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("1000.00")));

        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetSet", is(true)))
                .andExpect(jsonPath("$.budgetAmount", is(1000.00)))
                .andExpect(jsonPath("$.totalSpent", is(0)))
                .andExpect(jsonPath("$.remaining", is(1000.00)))
                .andExpect(jsonPath("$.percentage", is(0)))
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(3)));
    }

    @Test
    void setBudget_shouldUpdateExistingBudget() throws Exception {
        // Create initial budget
        String body1 = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("1000.00")));
        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isOk());

        // Update it
        String body2 = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("2000.00")));
        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetAmount", is(2000.00)));
    }

    @Test
    void setBudget_withTransactions_shouldReflectSpending() throws Exception {
        // Create a category and a transaction in March 2026
        Long categoryId = createCategoryViaApi("Food");
        createTransactionViaApi("Lunch", "150.00", categoryId, "2026-03-15");

        // Set budget
        String budgetBody = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("1000.00")));
        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetSet", is(true)))
                .andExpect(jsonPath("$.budgetAmount", is(1000.00)))
                .andExpect(jsonPath("$.totalSpent", is(150.00)))
                .andExpect(jsonPath("$.remaining", is(850.00)))
                .andExpect(jsonPath("$.percentage", is(15)));
    }

    @Test
    void setBudget_invalidAmount_shouldReturn400() throws Exception {
        String body = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("0.00")));

        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSummary_withBudgetSet_shouldReturnSummary() throws Exception {
        // Set budget first
        String budgetBody = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("500.00")));
        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/budgets/2026/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetSet", is(true)))
                .andExpect(jsonPath("$.budgetAmount", is(500.00)))
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(3)));
    }

    @Test
    void getSummary_noBudgetSet_shouldReturnBudgetSetFalse() throws Exception {
        mockMvc.perform(get("/api/budgets/2026/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetSet", is(false)))
                .andExpect(jsonPath("$.budgetAmount").doesNotExist())
                .andExpect(jsonPath("$.totalSpent", is(0)))
                .andExpect(jsonPath("$.remaining").doesNotExist())
                .andExpect(jsonPath("$.percentage").doesNotExist())
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(3)));
    }

    @Test
    void getSummary_noBudget_withTransactions_shouldShowSpending() throws Exception {
        Long categoryId = createCategoryViaApi("Food");
        createTransactionViaApi("Lunch", "50.00", categoryId, "2026-03-15");

        mockMvc.perform(get("/api/budgets/2026/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetSet", is(false)))
                .andExpect(jsonPath("$.totalSpent", is(50.00)));
    }

    @Test
    void deleteBudget_shouldReturn204() throws Exception {
        // Set budget first
        String budgetBody = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("500.00")));
        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody))
                .andExpect(status().isOk());

        // Delete it
        mockMvc.perform(delete("/api/budgets/2026/3"))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/budgets/2026/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetSet", is(false)));
    }

    @Test
    void deleteBudget_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/budgets/2026/6"))
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

    private Long createTransactionViaApi(String title, String amount, Long catId, String date)
            throws Exception {
        String body = """
                {
                    "title": "%s",
                    "amount": %s,
                    "currency": "USD",
                    "categoryId": %d,
                    "transactionDate": "%s"
                }
                """.formatted(title, amount, catId, date);

        MvcResult result = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }
}
