package com.expensetracker.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private javax.sql.DataSource dataSource;

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Clean up test data from previous runs (transactions first due to FK constraints)
        jdbc.execute(
                "DELETE FROM transactions WHERE category_id IN (SELECT id FROM categories WHERE name LIKE 'IntTest%')");
        jdbc.execute("DELETE FROM categories WHERE name LIKE 'IntTest%'");
    }

    private String categoryJson(String name) {
        return "{\"name\": \"" + name + "\"}";
    }

    private Long extractId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void createCategory_returns201() throws Exception {
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Food")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("IntTest Food")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createCategory_duplicateName_returns409() throws Exception {
        // Create first
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Duplicate")))
                .andExpect(status().isCreated());

        // Create duplicate
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Duplicate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void createCategory_blankName_returns400() throws Exception {
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void listCategories_returnsOnlyOwnCategories() throws Exception {
        // Create categories for user 1
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest User1 Cat")))
                .andExpect(status().isCreated());

        // Create categories for user 2
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest User2 Cat")))
                .andExpect(status().isCreated());

        // User 1 should not see user 2's categories
        mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'IntTest User2 Cat')]").doesNotExist());

        // User 2 should not see user 1's categories
        mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'IntTest User1 Cat')]").doesNotExist());
    }

    @Test
    void renameCategory_returns200() throws Exception {
        // Create a category
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/categories")
                                        .header(USER_ID_HEADER, "1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(categoryJson("IntTest Rename Original")))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long categoryId = extractId(createResult);

        // Rename
        mockMvc.perform(
                        put("/api/categories/" + categoryId)
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Rename Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("IntTest Rename Updated")));
    }

    @Test
    void renameCategory_duplicateName_returns409() throws Exception {
        // Create two categories
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Dup A")))
                .andExpect(status().isCreated());

        MvcResult createResult =
                mockMvc.perform(
                                post("/api/categories")
                                        .header(USER_ID_HEADER, "1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(categoryJson("IntTest Dup B")))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long categoryBId = extractId(createResult);

        // Try to rename B to A's name
        mockMvc.perform(
                        put("/api/categories/" + categoryBId)
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Dup A")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void renameCategory_otherUserCategory_returns404() throws Exception {
        // Create a category for user 1
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/categories")
                                        .header(USER_ID_HEADER, "1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(categoryJson("IntTest Auth Rename")))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long categoryId = extractId(createResult);

        // User 2 tries to rename user 1's category
        mockMvc.perform(
                        put("/api/categories/" + categoryId)
                                .header(USER_ID_HEADER, "2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("Hacked Name")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_noTransactions_returns204() throws Exception {
        // Create a category
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/categories")
                                        .header(USER_ID_HEADER, "1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(categoryJson("IntTest Delete Me")))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long categoryId = extractId(createResult);

        // Delete
        mockMvc.perform(delete("/api/categories/" + categoryId).header(USER_ID_HEADER, "1"))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/categories").header(USER_ID_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'IntTest Delete Me')]").doesNotExist());
    }

    @Test
    void deleteCategory_otherUserCategory_returns404() throws Exception {
        // Create a category for user 1
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/categories")
                                        .header(USER_ID_HEADER, "1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(categoryJson("IntTest Auth Delete")))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long categoryId = extractId(createResult);

        // User 2 tries to delete user 1's category
        mockMvc.perform(delete("/api/categories/" + categoryId).header(USER_ID_HEADER, "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_withTransactions_returns409() throws Exception {
        // Create a category
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/categories")
                                        .header(USER_ID_HEADER, "1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(categoryJson("IntTest Has Txn")))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long categoryId = extractId(createResult);

        // Insert a transaction directly using JDBC (transaction API may not exist yet)
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, category_id, title, amount, currency, transaction_date) VALUES (?, ?, ?, ?, ?, ?)",
                1L,
                categoryId,
                "Test Transaction",
                new BigDecimal("25.50"),
                "USD",
                LocalDate.of(2026, 3, 15));

        // Try to delete - should return 409
        mockMvc.perform(delete("/api/categories/" + categoryId).header(USER_ID_HEADER, "1"))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath(
                                "$.error",
                                is("Cannot delete category with existing transactions")));
    }

    @Test
    void sameNameDifferentUsers_allowed() throws Exception {
        // User 1 creates
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Shared Name")))
                .andExpect(status().isCreated());

        // User 2 creates same name - should succeed
        mockMvc.perform(
                        post("/api/categories")
                                .header(USER_ID_HEADER, "2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryJson("IntTest Shared Name")))
                .andExpect(status().isCreated());
    }
}
