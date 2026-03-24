package com.expensetracker.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Comprehensive cross-user data isolation tests for Categories and Transactions.
 *
 * <p>Verifies that: - User A's categories are invisible to User B - User B cannot
 * access/modify/delete User A's categories by ID - User A's transactions are invisible to User B -
 * User B cannot access/modify/delete User A's transactions by ID - User B cannot create a
 * transaction using User A's category ID - The FakeAuthFilter correctly switches users via
 * X-User-Id header
 *
 * <p>Uses seeded fake users: User 1 (id=1) and User 2 (id=2) from migrations V100 and V101.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Cross-User Authorization & Data Isolation")
class CrossUserAuthorizationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private javax.sql.DataSource dataSource;

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_A = "1";
    private static final String USER_B = "2";

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Clean up test data from previous runs (transactions first due to FK constraints)
        jdbc.execute("DELETE FROM transactions WHERE title LIKE 'AuthTest%'");
        jdbc.execute("DELETE FROM categories WHERE name LIKE 'AuthTest%'");
    }

    private Long extractId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String categoryJson(String name) {
        return "{\"name\": \"" + name + "\"}";
    }

    private String transactionJson(
            String title, String amount, String date, Long categoryId, String notes) {
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

    @Nested
    @DisplayName("FakeAuthFilter multi-user support")
    class FakeAuthFilterTests {

        @Test
        @DisplayName("Default user is User 1 when no X-User-Id header")
        void defaultUserIsUser1() throws Exception {
            // Create a category as the default user (no header)
            MvcResult result =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest Default User Cat")))
                            .andExpect(status().isCreated())
                            .andReturn();

            Long catId = extractId(result);

            // Verify User 1 can see it
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Default User Cat')]").exists());

            // Verify User 2 cannot see it
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$[?(@.name == 'AuthTest Default User Cat')]").doesNotExist());
        }

        @Test
        @DisplayName("X-User-Id header switches authenticated user context")
        void headerSwitchesUserContext() throws Exception {
            // User A creates a category
            mockMvc.perform(
                            post("/api/categories")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest UserA Only")))
                    .andExpect(status().isCreated());

            // User B creates a different category
            mockMvc.perform(
                            post("/api/categories")
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest UserB Only")))
                    .andExpect(status().isCreated());

            // User A sees only their category
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest UserA Only')]").exists())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest UserB Only')]").doesNotExist());

            // User B sees only their category
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest UserB Only')]").exists())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest UserA Only')]").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Category data isolation")
    class CategoryIsolationTests {

        @Test
        @DisplayName("GET /api/categories as User B returns empty when only User A has categories")
        void userB_listCategories_doesNotSeeUserAData() throws Exception {
            // User A creates categories
            mockMvc.perform(
                            post("/api/categories")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest Food")))
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/categories")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest Transport")))
                    .andExpect(status().isCreated());

            // User B lists categories - should NOT see User A's categories
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Food')]").doesNotExist())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Transport')]").doesNotExist());
        }

        @Test
        @DisplayName("PUT /api/categories/{userA-id} as User B returns 404")
        void userB_cannotRenameUserACategory() throws Exception {
            // User A creates a category
            MvcResult result =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest Rename Target")))
                            .andExpect(status().isCreated())
                            .andReturn();

            Long userACatId = extractId(result);

            // User B tries to rename User A's category
            mockMvc.perform(
                            put("/api/categories/" + userACatId)
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest Hacked Name")))
                    .andExpect(status().isNotFound());

            // Verify User A's category is unchanged
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Rename Target')]").exists())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Hacked Name')]").doesNotExist());
        }

        @Test
        @DisplayName("DELETE /api/categories/{userA-id} as User B returns 404")
        void userB_cannotDeleteUserACategory() throws Exception {
            // User A creates a category
            MvcResult result =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest Delete Target")))
                            .andExpect(status().isCreated())
                            .andReturn();

            Long userACatId = extractId(result);

            // User B tries to delete User A's category
            mockMvc.perform(delete("/api/categories/" + userACatId).header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isNotFound());

            // Verify User A's category still exists
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Delete Target')]").exists());
        }

        @Test
        @DisplayName("User A and User B can have categories with the same name independently")
        void bothUsers_canHaveSameNamedCategories() throws Exception {
            // User A creates a category
            mockMvc.perform(
                            post("/api/categories")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest Shared Name")))
                    .andExpect(status().isCreated());

            // User B creates a category with the same name - should succeed
            mockMvc.perform(
                            post("/api/categories")
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest Shared Name")))
                    .andExpect(status().isCreated());

            // Each user sees only their own
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Shared Name')]", hasSize(1)));

            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest Shared Name')]", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Transaction data isolation")
    class TransactionIsolationTests {

        private Long userACategoryId;
        private Long userBCategoryId;

        @BeforeEach
        void setUpCategories() throws Exception {
            // Create categories for each user
            MvcResult catA =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest CatA")))
                            .andExpect(status().isCreated())
                            .andReturn();
            userACategoryId = extractId(catA);

            MvcResult catB =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_B)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest CatB")))
                            .andExpect(status().isCreated())
                            .andReturn();
            userBCategoryId = extractId(catB);
        }

        @Test
        @DisplayName(
                "GET /api/transactions as User B returns empty when only User A has transactions")
        void userB_listTransactions_doesNotSeeUserAData() throws Exception {
            // User A creates transactions
            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest Groceries",
                                                    "52.75",
                                                    "2026-03-15",
                                                    userACategoryId,
                                                    "Weekly shopping")))
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest Gas",
                                                    "40.00",
                                                    "2026-03-14",
                                                    userACategoryId,
                                                    null)))
                    .andExpect(status().isCreated());

            // User B lists transactions - should NOT see User A's transactions
            mockMvc.perform(get("/api/transactions").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest Groceries')]").doesNotExist())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest Gas')]").doesNotExist());
        }

        @Test
        @DisplayName("PUT /api/transactions/{userA-id} as User B returns 404")
        void userB_cannotUpdateUserATransaction() throws Exception {
            // User A creates a transaction
            MvcResult result =
                    mockMvc.perform(
                                    post("/api/transactions")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    transactionJson(
                                                            "AuthTest Update Target",
                                                            "50.00",
                                                            "2026-03-15",
                                                            userACategoryId,
                                                            null)))
                            .andExpect(status().isCreated())
                            .andReturn();

            Long userATxnId = extractId(result);

            // User B tries to update User A's transaction
            mockMvc.perform(
                            put("/api/transactions/" + userATxnId)
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest Hacked Title",
                                                    "999.00",
                                                    "2026-03-20",
                                                    userBCategoryId,
                                                    "hacked")))
                    .andExpect(status().isNotFound());

            // Verify User A's transaction is unchanged
            mockMvc.perform(get("/api/transactions").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest Update Target')]").exists())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest Hacked Title')]").doesNotExist());
        }

        @Test
        @DisplayName("DELETE /api/transactions/{userA-id} as User B returns 404")
        void userB_cannotDeleteUserATransaction() throws Exception {
            // User A creates a transaction
            MvcResult result =
                    mockMvc.perform(
                                    post("/api/transactions")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    transactionJson(
                                                            "AuthTest Delete Target",
                                                            "75.00",
                                                            "2026-03-15",
                                                            userACategoryId,
                                                            null)))
                            .andExpect(status().isCreated())
                            .andReturn();

            Long userATxnId = extractId(result);

            // User B tries to delete User A's transaction
            mockMvc.perform(
                            delete("/api/transactions/" + userATxnId)
                                    .header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isNotFound());

            // Verify User A's transaction still exists
            mockMvc.perform(get("/api/transactions").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest Delete Target')]").exists());
        }

        @Test
        @DisplayName("POST /api/transactions with User A's categoryId as User B returns 404")
        void userB_cannotCreateTransactionWithUserACategoryId() throws Exception {
            // User B tries to create a transaction using User A's category
            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest Cross Category",
                                                    "25.00",
                                                    "2026-03-15",
                                                    userACategoryId,
                                                    null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").isNotEmpty());

            // Verify nothing was created for User B
            mockMvc.perform(get("/api/transactions").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$[?(@.title == 'AuthTest Cross Category')]").doesNotExist());
        }

        @Test
        @DisplayName("User B cannot update User A's transaction to use User A's category")
        void userB_cannotUpdateTransactionWithUserACategoryId() throws Exception {
            // User B creates a transaction with their own category
            MvcResult result =
                    mockMvc.perform(
                                    post("/api/transactions")
                                            .header(USER_ID_HEADER, USER_B)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    transactionJson(
                                                            "AuthTest UserB Own Txn",
                                                            "30.00",
                                                            "2026-03-15",
                                                            userBCategoryId,
                                                            null)))
                            .andExpect(status().isCreated())
                            .andReturn();

            Long userBTxnId = extractId(result);

            // User B tries to update their transaction to use User A's category
            mockMvc.perform(
                            put("/api/transactions/" + userBTxnId)
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest UserB Own Txn",
                                                    "30.00",
                                                    "2026-03-15",
                                                    userACategoryId,
                                                    null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("End-to-end data isolation scenario")
    class EndToEndIsolationTests {

        @Test
        @DisplayName("Full scenario: User A creates data, User B cannot access any of it")
        void fullIsolationScenario() throws Exception {
            // === Step 1: User A creates categories ===
            MvcResult catResult1 =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest E2E Food")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userACatId1 = extractId(catResult1);

            MvcResult catResult2 =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest E2E Transport")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userACatId2 = extractId(catResult2);

            // === Step 2: User A creates transactions ===
            MvcResult txnResult1 =
                    mockMvc.perform(
                                    post("/api/transactions")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    transactionJson(
                                                            "AuthTest E2E Groceries",
                                                            "52.75",
                                                            "2026-03-15",
                                                            userACatId1,
                                                            "Weekly")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userATxnId1 = extractId(txnResult1);

            MvcResult txnResult2 =
                    mockMvc.perform(
                                    post("/api/transactions")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    transactionJson(
                                                            "AuthTest E2E Bus Fare",
                                                            "3.50",
                                                            "2026-03-14",
                                                            userACatId2,
                                                            null)))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userATxnId2 = extractId(txnResult2);

            // === Step 3: User B cannot see User A's categories ===
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest E2E Food')]").doesNotExist())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest E2E Transport')]").doesNotExist());

            // === Step 4: User B cannot access User A's category by ID (PUT returns 404) ===
            mockMvc.perform(
                            put("/api/categories/" + userACatId1)
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(categoryJson("AuthTest E2E Hacked")))
                    .andExpect(status().isNotFound());

            // === Step 5: User B cannot delete User A's category by ID ===
            mockMvc.perform(delete("/api/categories/" + userACatId1).header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isNotFound());

            // === Step 6: User B cannot see User A's transactions ===
            mockMvc.perform(get("/api/transactions").header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest E2E Groceries')]").doesNotExist())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest E2E Bus Fare')]").doesNotExist());

            // === Step 7: User B cannot update User A's transaction by ID ===
            // First, User B needs a category to provide in the update request
            MvcResult userBCatResult =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_B)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest E2E UserB Cat")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userBCatId = extractId(userBCatResult);

            mockMvc.perform(
                            put("/api/transactions/" + userATxnId1)
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest E2E Hacked Txn",
                                                    "999.00",
                                                    "2026-03-20",
                                                    userBCatId,
                                                    "hacked")))
                    .andExpect(status().isNotFound());

            // === Step 8: User B cannot delete User A's transaction by ID ===
            mockMvc.perform(
                            delete("/api/transactions/" + userATxnId1)
                                    .header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isNotFound());

            mockMvc.perform(
                            delete("/api/transactions/" + userATxnId2)
                                    .header(USER_ID_HEADER, USER_B))
                    .andExpect(status().isNotFound());

            // === Step 9: User B cannot create a transaction with User A's category ===
            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_B)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest E2E Cross Cat",
                                                    "10.00",
                                                    "2026-03-15",
                                                    userACatId1,
                                                    null)))
                    .andExpect(status().isNotFound());

            // === Step 10: Verify User A's data is still intact ===
            mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest E2E Food')]").exists())
                    .andExpect(jsonPath("$[?(@.name == 'AuthTest E2E Transport')]").exists());

            mockMvc.perform(get("/api/transactions").header(USER_ID_HEADER, USER_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest E2E Groceries')]").exists())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest E2E Bus Fare')]").exists());
        }
    }

    @Nested
    @DisplayName("Transaction search/filter isolation")
    class TransactionFilterIsolationTests {

        @Test
        @DisplayName("User B cannot find User A's transactions via search")
        void userB_cannotSearchUserATransactions() throws Exception {
            // User A creates category and transaction
            MvcResult catResult =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest Search Cat")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userACatId = extractId(catResult);

            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest Secret Purchase",
                                                    "100.00",
                                                    "2026-03-15",
                                                    userACatId,
                                                    "confidential notes")))
                    .andExpect(status().isCreated());

            // User B searches by title keyword
            mockMvc.perform(
                            get("/api/transactions")
                                    .header(USER_ID_HEADER, USER_B)
                                    .param("search", "Secret"))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$[?(@.title == 'AuthTest Secret Purchase')]").doesNotExist());

            // User B searches by notes keyword
            mockMvc.perform(
                            get("/api/transactions")
                                    .header(USER_ID_HEADER, USER_B)
                                    .param("search", "confidential"))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$[?(@.title == 'AuthTest Secret Purchase')]").doesNotExist());
        }

        @Test
        @DisplayName("User B cannot find User A's transactions via category filter")
        void userB_cannotFilterByUserACategoryId() throws Exception {
            // User A creates category and transaction
            MvcResult catResult =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest Filter Cat")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userACatId = extractId(catResult);

            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest Filtered Item",
                                                    "50.00",
                                                    "2026-03-15",
                                                    userACatId,
                                                    null)))
                    .andExpect(status().isCreated());

            // User B tries to filter by User A's category ID
            mockMvc.perform(
                            get("/api/transactions")
                                    .header(USER_ID_HEADER, USER_B)
                                    .param("categoryId", userACatId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$[?(@.title == 'AuthTest Filtered Item')]").doesNotExist());
        }

        @Test
        @DisplayName("User B cannot find User A's transactions via date range filter")
        void userB_cannotFilterByDateRange() throws Exception {
            // User A creates category and transaction
            MvcResult catResult =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest Date Cat")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userACatId = extractId(catResult);

            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest March Item",
                                                    "50.00",
                                                    "2026-03-15",
                                                    userACatId,
                                                    null)))
                    .andExpect(status().isCreated());

            // User B filters by the same date range
            mockMvc.perform(
                            get("/api/transactions")
                                    .header(USER_ID_HEADER, USER_B)
                                    .param("dateFrom", "2026-03-01")
                                    .param("dateTo", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.title == 'AuthTest March Item')]").doesNotExist());
        }

        @Test
        @DisplayName("User B cannot find User A's transactions via amount range filter")
        void userB_cannotFilterByAmountRange() throws Exception {
            // User A creates category and transaction
            MvcResult catResult =
                    mockMvc.perform(
                                    post("/api/categories")
                                            .header(USER_ID_HEADER, USER_A)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(categoryJson("AuthTest Amount Cat")))
                            .andExpect(status().isCreated())
                            .andReturn();
            Long userACatId = extractId(catResult);

            mockMvc.perform(
                            post("/api/transactions")
                                    .header(USER_ID_HEADER, USER_A)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            transactionJson(
                                                    "AuthTest Expensive Item",
                                                    "500.00",
                                                    "2026-03-15",
                                                    userACatId,
                                                    null)))
                    .andExpect(status().isCreated());

            // User B filters by amount range that would include User A's transaction
            mockMvc.perform(
                            get("/api/transactions")
                                    .header(USER_ID_HEADER, USER_B)
                                    .param("amountMin", "400")
                                    .param("amountMax", "600"))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$[?(@.title == 'AuthTest Expensive Item')]").doesNotExist());
        }
    }
}
