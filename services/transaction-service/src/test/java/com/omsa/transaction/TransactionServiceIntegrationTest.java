package com.omsa.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omsa.transaction.dto.TransactionRequest;
import com.omsa.transaction.model.Transaction.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Transaction Service — Integration Tests")
class TransactionServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("txn_test")
                    .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private TransactionRequest validRequest() {
        return TransactionRequest.builder()
                .accountId("ACC-ZA-001")
                .counterpartyAccount("ACC-ZA-002")
                .type(TransactionType.PAYMENT)
                .amount(new BigDecimal("1500.00"))
                .currency("ZAR")
                .region("za-johannesburg")
                .description("Monthly premium payment")
                .build();
    }

    @Test @DisplayName("POST — creates transaction and returns 201")
    void createTransaction_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceNumber").value(startsWith("TXN-ZA-")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("ZAR"))
                .andExpect(jsonPath("$.region").value("za-johannesburg"));
    }

    @Test @DisplayName("POST — returns 400 for zero amount")
    void createTransaction_zeroAmount_returns400() throws Exception {
        var req = validRequest(); req.setAmount(BigDecimal.ZERO);
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("GET — returns 404 for unknown reference")
    void getByReference_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/reference/TXN-UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("PATCH — transitions PENDING to PROCESSING")
    void updateStatus_pendingToProcessing_succeeds() throws Exception {
        String body = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(patch("/api/v1/transactions/{id}/status", id)
                        .param("status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test @DisplayName("GET /account — returns paginated list")
    void getByAccount_returnsPaginatedList() throws Exception {
        var req = validRequest();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)));
        }
        mockMvc.perform(get("/api/v1/transactions/account/{id}", req.getAccountId())
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test @DisplayName("GET /actuator/health — returns UP")
    void actuatorHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test @DisplayName("GET /actuator/prometheus — returns metrics")
    void actuatorPrometheus_returnsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("transactions_initiated_total")));
    }
}
