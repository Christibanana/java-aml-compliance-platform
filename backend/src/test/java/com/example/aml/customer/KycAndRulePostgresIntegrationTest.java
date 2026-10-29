package com.example.aml.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KycAndRulePostgresIntegrationTest {
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.10-alpine");
    static { postgres.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    private String token(String role) throws Exception {
        String username = "synthetic_" + UUID.randomUUID().toString().replace("-", "");
        String password = "SyntheticPass123!";
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into sys_user(id,username,password_hash,display_name) values(?,?,?,?)",
                userId, username, passwords.encode(password), "Synthetic Test User");
        jdbc.update("insert into sys_user_role(user_id,role_id) select ?,id from sys_role where code=?", userId, role);
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(new com.example.aml.auth.AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).path("token").asText();
    }

    private UUID customer(String bearer) throws Exception {
        String no = "SYN-" + UUID.randomUUID().toString().substring(0, 12);
        String body = "{\"customerNo\":\"" + no + "\",\"customerType\":\"INDIVIDUAL\","
                + "\"fullName\":\"Synthetic Customer\",\"countryCode\":\"CN\",\"riskLevel\":\"LOW\"}";
        String result = mvc.perform(post("/api/customers").header("Authorization", bearer)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(result).path("id").asText());
    }

    @Test
    void defaultThreeRuleExecutionPersistsOnFlywayPostgres() throws Exception {
        String bearer = token("SYSTEM_ADMIN");
        UUID customerId = customer(bearer);
        String accountNo = "SYN-A-" + UUID.randomUUID().toString().substring(0, 12);
        mvc.perform(post("/api/accounts").header("Authorization", bearer).contentType("application/json")
                .content("{\"accountNo\":\"" + accountNo + "\",\"currency\":\"CNY\",\"customerId\":\""
                        + customerId + "\",\"relationType\":\"OWNER\",\"ownershipPercent\":100}"))
                .andExpect(status().isOk());
        String csv = "externalId,accountNo,counterpartyAccount,direction,amount,currency,transactionTime,transactionType,description\n"
                + "SYN-T-" + UUID.randomUUID() + "," + accountNo + ",SYN-OTHER,IN,120000.00,CNY,"
                + Instant.now() + ",TRANSFER,synthetic only\n";
        MockMultipartFile file = new MockMultipartFile("file", "synthetic.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/transactions/import").file(file).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
        String first = mvc.perform(post("/api/rules/execute").header("Authorization", bearer)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rules.length()").value(3))
                .andExpect(jsonPath("$.hits").value(1)).andReturn().getResponse().getContentAsString();
        UUID executionId = UUID.fromString(json.readTree(first).path("executionId").asText());
        assertThat(jdbc.queryForObject("select length(scope) from aml_rule_execution where id=?", Integer.class, executionId))
                .isGreaterThan(30);
        assertThat(jdbc.queryForObject("select status from aml_rule_execution where id=?", String.class, executionId))
                .isEqualTo("COMPLETED");
        mvc.perform(post("/api/rules/execute").header("Authorization", bearer)
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hits").value(0));
    }

    @Test
    void kycSubmissionReviewExpiryAndAuditPersist() throws Exception {
        String admin = token("SYSTEM_ADMIN");
        String analyst = token("AML_ANALYST");
        String manager = token("AML_MANAGER");
        UUID customerId = customer(admin);
        String path = "/api/customers/" + customerId + "/kyc";
        mvc.perform(post(path + "/submit").header("Authorization", admin).contentType("application/json")
                .content("{\"documentSummary\":\"Synthetic masked identity summary\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.current.status").value("PENDING"));
        mvc.perform(post(path + "/submit").header("Authorization", admin).contentType("application/json")
                .content("{\"documentSummary\":\"duplicate\"}"))
                .andExpect(status().isBadRequest());
        String approval = "{\"decision\":\"VERIFIED\",\"reason\":\"Synthetic data checked\",\"expiresAt\":\""
                + Instant.now().plusSeconds(3600) + "\"}";
        mvc.perform(post(path + "/review").header("Authorization", analyst).contentType("application/json")
                .content(approval)).andExpect(status().isForbidden());
        mvc.perform(post(path + "/review").header("Authorization", manager).contentType("application/json")
                .content(approval)).andExpect(status().isOk()).andExpect(jsonPath("$.current.status").value("VERIFIED"))
                .andExpect(jsonPath("$.history.length()").value(2));
        mvc.perform(post(path + "/review").header("Authorization", admin).contentType("application/json")
                .content(approval)).andExpect(status().isBadRequest());
        jdbc.update("update aml_customer_kyc set expires_at=now()-interval '1 second' where customer_id=?", customerId);
        mvc.perform(get(path).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.current.status").value("EXPIRED"));
        mvc.perform(post(path + "/submit").header("Authorization", admin).contentType("application/json")
                .content("{\"documentSummary\":\"Synthetic renewal summary\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.current.status").value("PENDING"))
                .andExpect(jsonPath("$.history.length()").value(4));
        mvc.perform(post(path + "/review").header("Authorization", admin).contentType("application/json")
                .content("{\"decision\":\"REJECTED\",\"reason\":\"Need updated synthetic summary\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.current.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("select count(*) from aml_customer_kyc_history where customer_id=?",
                Integer.class, customerId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from sys_audit_log where resource_type='CUSTOMER_KYC'",
                Integer.class)).isGreaterThanOrEqualTo(4);
        assertThat(jdbc.queryForObject("select risk_level from aml_customer where id=?", String.class, customerId))
                .isEqualTo("LOW");
    }
}
