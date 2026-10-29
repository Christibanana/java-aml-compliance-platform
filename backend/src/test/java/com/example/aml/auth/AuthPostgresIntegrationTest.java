package com.example.aml.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthPostgresIntegrationTest {
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

    private static final String PASSWORD = "SyntheticPass123!";

    private String createUser() {
        String username = "auth_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into sys_user(username,password_hash,display_name) values(?,?,?)",
                username, passwords.encode(PASSWORD), "Synthetic Auth User");
        return username;
    }

    private String login(String username, String password, int expectedStatus) throws Exception {
        String body = json.writeValueAsString(new AuthController.LoginRequest(username, password));
        return mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private int failures(String username) {
        return jdbc.queryForObject("select failed_attempts from sys_user where username=?", Integer.class, username);
    }

    private long failedAudits(String username) {
        return jdbc.queryForObject("select count(*) from sys_audit_log where action='LOGIN' and result='FAILED' "
                + "and resource_id=(select id::text from sys_user where username=?)", Long.class, username);
    }

    @Test
    void failureCommitsEvenThoughRequestFailsAndSuccessResetsCount() throws Exception {
        String username = createUser();
        login(username, "wrong", 401);
        assertThat(failures(username)).isEqualTo(1);
        assertThat(failedAudits(username)).isEqualTo(1);
        JsonNode success = json.readTree(login(username, PASSWORD, 200));
        assertThat(success.path("token").asText()).isNotBlank();
        assertThat(failures(username)).isZero();
    }

    @Test
    void fifthFailureLocksAndExpiryUnlocks() throws Exception {
        String username = createUser();
        for (int attempt = 0; attempt < 5; attempt++) login(username, "wrong", 401);
        assertThat(failures(username)).isEqualTo(5);
        assertThat(failedAudits(username)).isEqualTo(5);
        Instant lockedUntil = jdbc.queryForObject("select locked_until from sys_user where username=?",
                (result, row) -> result.getTimestamp(1).toInstant(), username);
        assertThat(lockedUntil).isAfter(Instant.now());
        login(username, PASSWORD, 401);
        jdbc.update("update sys_user set locked_until=now()-interval '1 second' where username=?", username);
        login(username, PASSWORD, 200);
        assertThat(failures(username)).isZero();
        java.sql.Timestamp unlockedUntil = jdbc.queryForObject("select locked_until from sys_user where username=?",
                (result, row) -> result.getTimestamp(1), username);
        assertThat(unlockedUntil).isNull();
    }

    @Test
    void unknownUserHasSameResponseAsWrongPassword() throws Exception {
        String username = createUser();
        String wrong = login(username, "wrong", 401);
        String unknown = login("missing_" + UUID.randomUUID(), "wrong", 401);
        assertThat(json.readTree(wrong).path("message").asText())
                .isEqualTo(json.readTree(unknown).path("message").asText());
    }

    @Test
    void concurrentFailuresDoNotLoseUpdates() throws Exception {
        String username = createUser();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 4).<java.util.concurrent.Callable<String>>mapToObj(
                    attempt -> () -> login(username, "wrong", 401)).toList();
            for (var result : executor.invokeAll(tasks, 60, TimeUnit.SECONDS)) result.get();
        }
        assertThat(failures(username)).isEqualTo(4);
        assertThat(failedAudits(username)).isEqualTo(4);
    }
}
