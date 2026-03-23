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
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private javax.sql.DataSource dataSource;

    private static final String USER_ID_HEADER = "X-User-Id";

    private Long user1CategoryId;

    @BeforeEach
    void setUp() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Clean up test data
        jdbc.execute("DELETE FROM transactions WHERE title LIKE 'DashTest%'");
        jdbc.execute("DELETE FROM monthly_budgets WHERE year = 2098");
        jdbc.execute("DELETE FROM categories WHERE name LIKE 'DashTest%'");

        // Create category for user 1
        MvcResult catResult = mockMvc.perform(post("/api/categories")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"DashTest Category\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        user1CategoryId = ((Number) JsonPath.read(catResult.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String transactionJson(String title, String amount, String date) {
        return "{\"title\": \"" + title + "\", \"amount\": " + amount +
               ", \"transactionDate\": \"" + date + "\", \"categoryId\": " + user1CategoryId + "}";
    }

    // === DASHBOARD WITHOUT BUDGET ===

    @Test
    void getDashboard_noBudget_noTransactions_returnsZeroSpentAndNulls() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2098")
                .param("month", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", is(0)))
            .andExpect(jsonPath("$.budgetAmount").value(nullValue()))
            .andExpect(jsonPath("$.remaining").value(nullValue()))
            .andExpect(jsonPath("$.usagePercentage").value(nullValue()));
    }

    @Test
    void getDashboard_noBudget_withTransactions_returnsTotalSpentAndNullBudget() throws Exception {
        // Create transactions for March 2098
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest Groceries", "50.00", "2098-03-15")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest Coffee", "5.50", "2098-03-20")))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2098")
                .param("month", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", closeTo(55.50, 0.01)))
            .andExpect(jsonPath("$.budgetAmount").value(nullValue()))
            .andExpect(jsonPath("$.remaining").value(nullValue()))
            .andExpect(jsonPath("$.usagePercentage").value(nullValue()));
    }

    // === DASHBOARD WITH BUDGET ===

    @Test
    void getDashboard_withBudget_noTransactions_returnsFullBudgetRemaining() throws Exception {
        // Set budget
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\": 2098, \"month\": 4, \"amount\": 1000.00}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2098")
                .param("month", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", is(0)))
            .andExpect(jsonPath("$.budgetAmount", closeTo(1000.00, 0.01)))
            .andExpect(jsonPath("$.remaining", closeTo(1000.00, 0.01)))
            .andExpect(jsonPath("$.usagePercentage", closeTo(0.00, 0.01)));
    }

    @Test
    void getDashboard_withBudgetAndTransactions_returnsCorrectCalculations() throws Exception {
        // Set budget for May 2098
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\": 2098, \"month\": 5, \"amount\": 500.00}"))
            .andExpect(status().isCreated());

        // Create transactions for May 2098
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest Lunch", "150.00", "2098-05-10")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest Dinner", "100.00", "2098-05-15")))
            .andExpect(status().isCreated());

        // totalSpent = 250, budgetAmount = 500, remaining = 250, usage = 50%
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2098")
                .param("month", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", closeTo(250.00, 0.01)))
            .andExpect(jsonPath("$.budgetAmount", closeTo(500.00, 0.01)))
            .andExpect(jsonPath("$.remaining", closeTo(250.00, 0.01)))
            .andExpect(jsonPath("$.usagePercentage", closeTo(50.00, 0.01)));
    }

    @Test
    void getDashboard_overspent_returnsNegativeRemaining() throws Exception {
        // Set budget for June 2098
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\": 2098, \"month\": 6, \"amount\": 200.00}"))
            .andExpect(status().isCreated());

        // Create transactions totaling 300
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest Big Expense", "300.00", "2098-06-10")))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2098")
                .param("month", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", closeTo(300.00, 0.01)))
            .andExpect(jsonPath("$.budgetAmount", closeTo(200.00, 0.01)))
            .andExpect(jsonPath("$.remaining", closeTo(-100.00, 0.01)))
            .andExpect(jsonPath("$.usagePercentage", closeTo(150.00, 0.01)));
    }

    // === VALIDATION TESTS ===

    @Test
    void getDashboard_monthZero_returns400() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2026")
                .param("month", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void getDashboard_month13_returns400() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2026")
                .param("month", "13"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void getDashboard_negativeMonth_returns400() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2026")
                .param("month", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void getDashboard_yearTooLow_returns400() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "1999")
                .param("month", "6"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void getDashboard_yearTooHigh_returns400() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2101")
                .param("month", "6"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // === CROSS-USER ISOLATION ===

    @Test
    void getDashboard_crossUserIsolation_transactionsNotShared() throws Exception {
        // Create category for user 2
        MvcResult cat2Result = mockMvc.perform(post("/api/categories")
                .header(USER_ID_HEADER, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"DashTest Cat2\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        Long user2CategoryId = ((Number) JsonPath.read(cat2Result.getResponse().getContentAsString(), "$.id")).longValue();

        // User 1 creates transaction in July 2098
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest User1 Item", "100.00", "2098-07-15")))
            .andExpect(status().isCreated());

        // User 2 creates transaction in July 2098
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"DashTest User2 Item\", \"amount\": 200.00, \"transactionDate\": \"2098-07-15\", \"categoryId\": " + user2CategoryId + "}"))
            .andExpect(status().isCreated());

        // User 1 dashboard should show only $100 spent
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2098")
                .param("month", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", closeTo(100.00, 0.01)));

        // User 2 dashboard should show only $200 spent
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "2")
                .param("year", "2098")
                .param("month", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", closeTo(200.00, 0.01)));
    }

    @Test
    void getDashboard_transactionsInDifferentMonth_notCounted() throws Exception {
        // Set budget for Aug 2098
        mockMvc.perform(post("/api/budgets")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\": 2098, \"month\": 8, \"amount\": 1000.00}"))
            .andExpect(status().isCreated());

        // Create transaction in August
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest Aug Item", "100.00", "2098-08-15")))
            .andExpect(status().isCreated());

        // Create transaction in September (different month)
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("DashTest Sep Item", "200.00", "2098-09-15")))
            .andExpect(status().isCreated());

        // Dashboard for August should only count August transaction
        mockMvc.perform(get("/api/dashboard")
                .header(USER_ID_HEADER, "1")
                .param("year", "2098")
                .param("month", "8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent", closeTo(100.00, 0.01)))
            .andExpect(jsonPath("$.remaining", closeTo(900.00, 0.01)));
    }
}
