package com.example.vulnbank;

import com.example.vulnbank.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VulnBankSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registrationAssignsSafeDefaults() throws Exception {
        long before = userRepository.count();
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-user","password":"long-enough-password",
                                 "email":"new-user@example.test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.balance").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.has("password")).isFalse();
        assertThat(userRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void userCannotReadAnotherUsersProfileOrHistory() throws Exception {
        String token = login("alice", "alice-password-123");

        mockMvc.perform(get("/api/users/3/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/transactions/history/3").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotDeleteAnAccount() throws Exception {
        String token = login("alice", "alice-password-123");
        mockMvc.perform(delete("/api/admin/users/3").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferRejectsNonPositiveAmounts() throws Exception {
        String token = login("alice", "alice-password-123");
        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toUserId":3,"amount":-10.00,"note":"Invalid transfer"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthIsPublicAndHistoryDoesNotContainCredentials() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        String token = login("alice", "alice-password-123");
        mockMvc.perform(get("/api/transactions/history/2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void transactionSearchIsScopedToAuthenticatedAccount() throws Exception {
        String token = login("bob", "bob-password-123");
        mockMvc.perform(get("/api/transactions/search")
                        .param("note", "payroll")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
