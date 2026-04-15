package io.github.vfedoriv.expensetracker.category;

import tools.jackson.databind.ObjectMapper;
import io.github.vfedoriv.expensetracker.TestcontainersConfig;
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
class CategoryControllerIntegrationTest {

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
    void createCategory_shouldReturn201WithCreatedCategory() throws Exception {
        String body = objectMapper.writeValueAsString(new CategoryRequest("Food"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Food")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    void createCategory_withBlankName_shouldReturn400() throws Exception {
        String body = objectMapper.writeValueAsString(new CategoryRequest(""));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", notNullValue()));
    }

    @Test
    void createCategory_duplicateName_shouldReturn409() throws Exception {
        String body = objectMapper.writeValueAsString(new CategoryRequest("Food"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void getAllCategories_shouldReturnList() throws Exception {
        createCategoryViaApi("Food");
        createCategoryViaApi("Transport");

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", notNullValue()))
                .andExpect(jsonPath("$[1].name", notNullValue()));
    }

    @Test
    void getAllCategories_empty_shouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void updateCategory_shouldRename() throws Exception {
        Long categoryId = createCategoryViaApi("Food");

        String updateBody = objectMapper.writeValueAsString(new CategoryRequest("Groceries"));

        mockMvc.perform(put("/api/categories/{id}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(categoryId.intValue())))
                .andExpect(jsonPath("$.name", is("Groceries")));
    }

    @Test
    void updateCategory_notFound_shouldReturn404() throws Exception {
        String updateBody = objectMapper.writeValueAsString(new CategoryRequest("Groceries"));

        mockMvc.perform(put("/api/categories/{id}", 99999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_shouldReturn204() throws Exception {
        Long categoryId = createCategoryViaApi("Food");

        mockMvc.perform(delete("/api/categories/{id}", categoryId))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteCategory_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_withTransactions_shouldReturn409() throws Exception {
        // Create a category
        Long categoryId = createCategoryViaApi("Food");

        // Create a transaction referencing this category
        String transactionBody = """
                {
                    "title": "Lunch",
                    "amount": 12.50,
                    "currency": "USD",
                    "categoryId": %d,
                    "transactionDate": "2026-03-15"
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionBody))
                .andExpect(status().isCreated());

        // Try to delete the category - should be blocked
        mockMvc.perform(delete("/api/categories/{id}", categoryId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    /**
     * Helper: creates a category via the API and returns its ID.
     */
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
}
