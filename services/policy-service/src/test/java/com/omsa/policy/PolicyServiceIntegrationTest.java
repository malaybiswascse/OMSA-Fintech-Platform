package com.omsa.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omsa.policy.dto.PolicyRequest;
import com.omsa.policy.model.Policy.PolicyStatus;
import com.omsa.policy.model.Policy.PolicyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Policy Service Integration Tests")
class PolicyServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("policy_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private PolicyRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = PolicyRequest.builder()
                .customerId("CUST-ZA-001")
                .type(PolicyType.LIFE)
                .premiumAmount(new BigDecimal("850.00"))
                .sumAssured(new BigDecimal("1000000.00"))
                .currency("ZAR")
                .region("za-johannesburg")
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/policies - creates policy and returns 201")
    void createPolicy_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber").value(startsWith("POL-")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.premiumAmount").value(850.00))
                .andExpect(jsonPath("$.currency").value("ZAR"))
                .andExpect(jsonPath("$.region").value("za-johannesburg"))
                .andExpect(jsonPath("$.type").value("LIFE"));
    }

    @Test
    @DisplayName("POST /api/v1/policies - returns 400 for missing customerId")
    void createPolicy_missingCustomerId_returns400() throws Exception {
        validRequest.setCustomerId(null);
        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/policies - returns 400 for zero premium")
    void createPolicy_zeroPremium_returns400() throws Exception {
        validRequest.setPremiumAmount(BigDecimal.ZERO);
        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/policies/{id} - returns 404 for unknown ID")
    void getById_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/policies/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/policies/{id} - returns policy after creation")
    void getById_afterCreation_returnsPolicy() throws Exception {
        String response = mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/policies/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerId").value("CUST-ZA-001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/policies/{id}/status - activates a PENDING policy")
    void updateStatus_pendingToActive_succeeds() throws Exception {
        String response = mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/api/v1/policies/{id}/status", id)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/customer/{id} - returns paginated list")
    void getByCustomer_returnsPaginatedResults() throws Exception {
        String customerId = "CUST-ZA-PAGINATE";
        validRequest.setCustomerId(customerId);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/policies")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)));
        }

        mockMvc.perform(get("/api/v1/policies/customer/{id}", customerId)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("GET /actuator/health - returns UP")
    void actuatorHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
