package io.github.vfedoriv.expensetracker;

import tools.jackson.databind.ObjectMapper;
import io.github.vfedoriv.expensetracker.budget.BudgetRequest;
import io.github.vfedoriv.expensetracker.budget.MonthlyBudget;
import io.github.vfedoriv.expensetracker.budget.MonthlyBudgetRepository;
import io.github.vfedoriv.expensetracker.category.Category;
import io.github.vfedoriv.expensetracker.category.CategoryRepository;
import io.github.vfedoriv.expensetracker.category.CategoryRequest;
import io.github.vfedoriv.expensetracker.transaction.Transaction;
import io.github.vfedoriv.expensetracker.transaction.TransactionRepository;
import io.github.vfedoriv.expensetracker.user.User;
import io.github.vfedoriv.expensetracker.user.UserRepository;
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
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that cross-user data access is properly prevented.
 *
 * <p>The FakeAuthFilter always authenticates as "user A" (provider=fake, providerUserId=fake-user-1).
 * We create "user B" directly in the database and populate user B's data via repositories.
 * Then we verify that API calls (which run as user A) cannot see or modify user B's data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CrossUserAccessTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MonthlyBudgetRepository monthlyBudgetRepository;

    private MockMvc mockMvc;

    /**
     * User B, created directly in the DB. All API calls go through as user A (via FakeAuthFilter).
     */
    private User userB;
    private Category userBCategory;
    private Transaction userBTransaction;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Trigger a request so FakeAuthFilter creates user A in the database
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());

        // Create user B directly in the database
        userB = userRepository.save(User.builder()
                .provider("fake")
                .providerUserId("fake-user-2")
                .email("other@test.com")
                .displayName("Other User")
                .build());

        // Create a category for user B directly in the database
        userBCategory = categoryRepository.save(Category.builder()
                .user(userB)
                .name("User B Category")
                .build());

        // Create a transaction for user B directly in the database
        userBTransaction = transactionRepository.save(Transaction.builder()
                .user(userB)
                .category(userBCategory)
                .title("User B Transaction")
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .transactionDate(LocalDate.of(2026, 3, 15))
                .notes("User B's private note")
                .build());

        // Create a budget for user B directly in the database
        monthlyBudgetRepository.save(MonthlyBudget.builder()
                .user(userB)
                .year(2026)
                .month(3)
                .amount(new BigDecimal("2000.00"))
                .build());
    }

    // --- Category isolation ---

    @Test
    void getCategories_shouldNotReturnOtherUserCategories() throws Exception {
        // Create a category for user A via API
        createCategoryViaApi("User A Category");

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("User A Category")));
        // User B's category should not appear
    }

    @Test
    void updateCategory_ofOtherUser_shouldReturn404() throws Exception {
        String body = objectMapper.writeValueAsString(new CategoryRequest("Hacked Name"));

        mockMvc.perform(put("/api/categories/{id}", userBCategory.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_ofOtherUser_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", userBCategory.getId()))
                .andExpect(status().isNotFound());
    }

    // --- Transaction isolation ---

    @Test
    void getTransactions_shouldNotReturnOtherUserTransactions() throws Exception {
        // Create a category + transaction for user A
        Long userACatId = createCategoryViaApi("Food");
        createTransactionViaApi("User A Lunch", "12.50", userACatId, "2026-03-15");

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("User A Lunch")));
        // User B's transaction should not appear
    }

    @Test
    void updateTransaction_ofOtherUser_shouldReturn404() throws Exception {
        // User A needs a category to reference in the request body
        Long userACatId = createCategoryViaApi("Food");

        String body = """
                {
                    "title": "Hacked",
                    "amount": 1.00,
                    "currency": "USD",
                    "categoryId": %d,
                    "transactionDate": "2026-03-15"
                }
                """.formatted(userACatId);

        mockMvc.perform(put("/api/transactions/{id}", userBTransaction.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTransaction_ofOtherUser_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/transactions/{id}", userBTransaction.getId()))
                .andExpect(status().isNotFound());
    }

    // --- Budget isolation ---

    @Test
    void getBudget_shouldNotSeeOtherUserBudget() throws Exception {
        // User A has no budget set for 2026/3
        mockMvc.perform(get("/api/budgets/2026/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetSet", is(false)));
        // User B's $2000 budget should not be visible
    }

    @Test
    void getBudget_shouldNotSeeOtherUserSpending() throws Exception {
        // User B has a $99.99 transaction in March 2026
        // User A should see $0 spending
        mockMvc.perform(get("/api/budgets/2026/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent", is(0)));
    }

    @Test
    void setBudget_shouldNotAffectOtherUserBudget() throws Exception {
        // User A sets their own budget
        String body = objectMapper.writeValueAsString(new BudgetRequest(new BigDecimal("500.00")));
        mockMvc.perform(put("/api/budgets/2026/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetAmount", is(500.00)));

        // Verify user B's budget is still intact in the database
        MonthlyBudget userBBudget = monthlyBudgetRepository
                .findByUserIdAndYearAndMonth(userB.getId(), 2026, 3)
                .orElseThrow();
        assertEquals(0, userBBudget.getAmount().compareTo(new BigDecimal("2000.00")),
                "User B's budget should remain unchanged");
    }

    @Test
    void deleteBudget_forMonthWithOnlyOtherUserBudget_shouldReturn404() throws Exception {
        // User B has a budget for 2026/3, but user A does not
        mockMvc.perform(delete("/api/budgets/2026/3"))
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
