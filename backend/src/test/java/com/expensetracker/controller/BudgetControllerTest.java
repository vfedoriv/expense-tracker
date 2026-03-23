package com.expensetracker.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private javax.sql.DataSource dataSource;

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Clean up budget test data
        jdbc.execute("DELETE FROM monthly_budgets WHERE year = 2099");
    }

    private String budgetJson(int year, int month, String amount) {
        return "{\"year\": " + year + ", \"month\": " + month + ", \"amount\": " + amount + "}";
    }

    // === CREATE BUDGET TESTS ===

    @Test
    void createBudget_newBudget_returns201() throws Exception {
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 1, "1000.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.year", is(2099)))
            .andExpect(jsonPath("$.month", is(1)))
            .andExpect(jsonPath("$.amount", is(1000.00)))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.createdAt", notNullValue()))
            .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void createBudget_existingBudget_updatesAndReturns200() throws Exception {
        // Create initial budget
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 2, "1000.00")))
            .andExpect(status().isCreated());

        // Update same year+month
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 2, "2000.00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount", is(2000.00)))
            .andExpect(jsonPath("$.year", is(2099)))
            .andExpect(jsonPath("$.month", is(2)));
    }

    @Test
    void createBudget_invalidMonth_returns400() throws Exception {
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 13, "1000.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createBudget_zeroMonth_returns400() throws Exception {
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 0, "1000.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createBudget_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 3, "-500.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createBudget_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 3, "0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createBudget_missingAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\": 2099, \"month\": 3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // === GET BUDGET TESTS ===

    @Test
    void getBudget_exists_returns200() throws Exception {
        // Create budget first
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 4, "1500.00")))
            .andExpect(status().isCreated());

        // Get it
        mockMvc.perform(get("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .param("year", "2099")
                .param("month", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.year", is(2099)))
            .andExpect(jsonPath("$.month", is(4)))
            .andExpect(jsonPath("$.amount", is(1500.00)));
    }

    @Test
    void getBudget_notExists_returns404() throws Exception {
        mockMvc.perform(get("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .param("year", "2099")
                .param("month", "12"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // === CROSS-USER ISOLATION ===

    @Test
    void getBudget_crossUser_returns404() throws Exception {
        // User 1 creates a budget
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 5, "1000.00")))
            .andExpect(status().isCreated());

        // User 2 cannot see user 1's budget
        mockMvc.perform(get("/api/budgets")
                .header(USER_ID_HEADER, "2")
                .param("year", "2099")
                .param("month", "5"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createBudget_sameMonthDifferentUsers_bothSucceed() throws Exception {
        // User 1 creates budget for month 6
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 6, "1000.00")))
            .andExpect(status().isCreated());

        // User 2 creates budget for same month
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(budgetJson(2099, 6, "2000.00")))
            .andExpect(status().isCreated());

        // Verify each user sees their own budget
        mockMvc.perform(get("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .param("year", "2099")
                .param("month", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount", is(1000.00)));

        mockMvc.perform(get("/api/budgets")
                .header(USER_ID_HEADER, "2")
                .param("year", "2099")
                .param("month", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount", is(2000.00)));
    }
}
