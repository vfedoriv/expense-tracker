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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private javax.sql.DataSource dataSource;

    private static final String USER_ID_HEADER = "X-User-Id";

    private Long user1CategoryId;
    private Long user2CategoryId;

    @BeforeEach
    void setUp() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Clean up test data from previous runs
        jdbc.execute("DELETE FROM transactions WHERE title LIKE 'TxnTest%'");
        jdbc.execute("DELETE FROM categories WHERE name LIKE 'TxnTest%'");

        // Create categories for user 1 and user 2
        MvcResult cat1Result = mockMvc.perform(post("/api/categories")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TxnTest Food\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        user1CategoryId = ((Number) JsonPath.read(cat1Result.getResponse().getContentAsString(), "$.id")).longValue();

        MvcResult cat2Result = mockMvc.perform(post("/api/categories")
                .header(USER_ID_HEADER, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TxnTest Transport\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        user2CategoryId = ((Number) JsonPath.read(cat2Result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String transactionJson(String title, String amount, String date, Long categoryId, String notes) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"title\": \"").append(title).append("\",");
        sb.append("\"amount\": ").append(amount).append(",");
        sb.append("\"transactionDate\": \"").append(date).append("\",");
        sb.append("\"categoryId\": ").append(categoryId);
        if (notes != null) {
            sb.append(",\"notes\": \"").append(notes).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private Long extractId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // === CREATE TESTS ===

    @Test
    void createTransaction_validData_returns201() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Groceries", "52.75", "2026-03-15", user1CategoryId, "Weekly groceries")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title", is("TxnTest Groceries")))
            .andExpect(jsonPath("$.amount", is(52.75)))
            .andExpect(jsonPath("$.currency", is("USD")))
            .andExpect(jsonPath("$.transactionDate", is("2026-03-15")))
            .andExpect(jsonPath("$.categoryId", is(user1CategoryId.intValue())))
            .andExpect(jsonPath("$.categoryName", is("TxnTest Food")))
            .andExpect(jsonPath("$.notes", is("Weekly groceries")))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createTransaction_withoutNotes_returns201() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest No Notes", "10.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title", is("TxnTest No Notes")))
            .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void createTransaction_emptyTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("   ", "52.75", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createTransaction_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Zero", "0", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createTransaction_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Negative", "-10.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createTransaction_missingDate_returns400() throws Exception {
        String json = "{\"title\": \"TxnTest No Date\", \"amount\": 50.00, \"categoryId\": " + user1CategoryId + "}";
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createTransaction_missingCategoryId_returns400() throws Exception {
        String json = "{\"title\": \"TxnTest No Cat\", \"amount\": 50.00, \"transactionDate\": \"2026-03-15\"}";
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createTransaction_otherUsersCategoryId_returns404() throws Exception {
        // User 1 tries to use User 2's category
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Cross Cat", "50.00", "2026-03-15", user2CategoryId, null)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // === LIST TESTS ===

    @Test
    void listTransactions_returnsOnlyAuthenticatedUsersTransactions() throws Exception {
        // Create transactions for user 1
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest User1 Item", "25.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        // Create transactions for user 2
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest User2 Item", "30.00", "2026-03-15", user2CategoryId, null)))
            .andExpect(status().isCreated());

        // User 1 should not see user 2's transactions
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest User2 Item')]").doesNotExist());

        // User 2 should not see user 1's transactions
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest User1 Item')]").doesNotExist());
    }

    @Test
    void listTransactions_searchByTitle() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Coffee Shop", "5.50", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Gas Station", "40.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        // Search by title "coffee" - case insensitive
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .param("search", "coffee"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Coffee Shop')]").exists())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Gas Station')]").doesNotExist());
    }

    @Test
    void listTransactions_searchByNotes() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Lunch", "15.00", "2026-03-15", user1CategoryId, "Special birthday lunch")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Dinner", "25.00", "2026-03-15", user1CategoryId, "Regular dinner")))
            .andExpect(status().isCreated());

        // Search by notes "birthday" - should find only the lunch
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .param("search", "birthday"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Lunch')]").exists())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Dinner')]").doesNotExist());
    }

    @Test
    void listTransactions_filterByCategory() throws Exception {
        // Create a second category for user 1
        MvcResult cat2Result = mockMvc.perform(post("/api/categories")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TxnTest Transport\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        Long user1Cat2Id = ((Number) JsonPath.read(cat2Result.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Food Item", "20.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Bus Ticket", "3.50", "2026-03-15", user1Cat2Id, null)))
            .andExpect(status().isCreated());

        // Filter by food category
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .param("categoryId", user1CategoryId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Food Item')]").exists())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Bus Ticket')]").doesNotExist());
    }

    @Test
    void listTransactions_filterByDateRange() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest March Item", "20.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Feb Item", "30.00", "2026-02-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        // Filter for March only
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .param("dateFrom", "2026-03-01")
                .param("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest March Item')]").exists())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Feb Item')]").doesNotExist());
    }

    @Test
    void listTransactions_filterByAmountRange() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Cheap Item", "5.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Expensive Item", "200.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        // Filter for items between 100 and 300
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .param("amountMin", "100")
                .param("amountMax", "300"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Expensive Item')]").exists())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Cheap Item')]").doesNotExist());
    }

    @Test
    void listTransactions_combinedFilters() throws Exception {
        // Create a second category for user 1
        MvcResult cat2Result = mockMvc.perform(post("/api/categories")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TxnTest Combined Cat\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        Long combinedCatId = ((Number) JsonPath.read(cat2Result.getResponse().getContentAsString(), "$.id")).longValue();

        // Create transactions
        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Combined Match", "50.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Combined WrongCat", "50.00", "2026-03-15", combinedCatId, null)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Combined WrongDate", "50.00", "2026-02-15", user1CategoryId, null)))
            .andExpect(status().isCreated());

        // Combine category + date range + search
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .param("search", "Combined Match")
                .param("categoryId", user1CategoryId.toString())
                .param("dateFrom", "2026-03-01")
                .param("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Combined Match')]").exists())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Combined WrongCat')]").doesNotExist())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Combined WrongDate')]").doesNotExist());
    }

    // === UPDATE TESTS ===

    @Test
    void updateTransaction_returns200() throws Exception {
        // Create a transaction
        MvcResult createResult = mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Original", "50.00", "2026-03-15", user1CategoryId, "Original notes")))
            .andExpect(status().isCreated())
            .andReturn();

        Long txnId = extractId(createResult);

        // Update
        mockMvc.perform(put("/api/transactions/" + txnId)
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Updated", "75.00", "2026-03-20", user1CategoryId, "Updated notes")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("TxnTest Updated")))
            .andExpect(jsonPath("$.amount", is(75.00)))
            .andExpect(jsonPath("$.transactionDate", is("2026-03-20")))
            .andExpect(jsonPath("$.notes", is("Updated notes")));
    }

    @Test
    void updateTransaction_crossUser_returns404() throws Exception {
        // User 1 creates a transaction
        MvcResult createResult = mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Auth Update", "50.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated())
            .andReturn();

        Long txnId = extractId(createResult);

        // User 2 tries to update user 1's transaction
        mockMvc.perform(put("/api/transactions/" + txnId)
                .header(USER_ID_HEADER, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Hacked", "999.00", "2026-03-20", user2CategoryId, null)))
            .andExpect(status().isNotFound());
    }

    // === DELETE TESTS ===

    @Test
    void deleteTransaction_returns204() throws Exception {
        // Create a transaction
        MvcResult createResult = mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Delete Me", "50.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated())
            .andReturn();

        Long txnId = extractId(createResult);

        // Delete
        mockMvc.perform(delete("/api/transactions/" + txnId)
                .header(USER_ID_HEADER, "1"))
            .andExpect(status().isNoContent());

        // Verify it's gone from the list
        mockMvc.perform(get("/api/transactions")
                .header(USER_ID_HEADER, "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.title == 'TxnTest Delete Me')]").doesNotExist());
    }

    @Test
    void deleteTransaction_crossUser_returns404() throws Exception {
        // User 1 creates a transaction
        MvcResult createResult = mockMvc.perform(post("/api/transactions")
                .header(USER_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson("TxnTest Auth Delete", "50.00", "2026-03-15", user1CategoryId, null)))
            .andExpect(status().isCreated())
            .andReturn();

        Long txnId = extractId(createResult);

        // User 2 tries to delete user 1's transaction
        mockMvc.perform(delete("/api/transactions/" + txnId)
                .header(USER_ID_HEADER, "2"))
            .andExpect(status().isNotFound());
    }
}
