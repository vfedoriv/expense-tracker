package com.expensetracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getCurrentUser_returnsUserData() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.provider", is("fake")))
            .andExpect(jsonPath("$.email", is("admin@test.com")))
            .andExpect(jsonPath("$.displayName", is("Test User")))
            .andExpect(jsonPath("$.avatarUrl").value(nullValue()));
    }

    @Test
    void getCurrentUser_differentUser_returnsTheirData() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("X-User-Id", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(2)))
            .andExpect(jsonPath("$.provider", is("fake")))
            .andExpect(jsonPath("$.email", is("user2@test.com")))
            .andExpect(jsonPath("$.displayName", is("Test User 2")));
    }

    @Test
    void getCurrentUser_defaultUser_returnsUser1() throws Exception {
        // Without X-User-Id header, the filter defaults to user 1
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.displayName", is("Test User")));
    }
}
