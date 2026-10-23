package com.example.aml.rule;

import com.example.aml.audit.AuditService;
import com.example.aml.rule.dto.ExecuteRulesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RuleExecutionReliabilityTest {
    private JdbcTemplate jdbc;
    private RuleExecutionService service;
    private UUID accountId;
    private UUID customerId;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:rules-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        var manager = new DataSourceTransactionManager(dataSource);
        jdbc.execute("create table aml_rule(id uuid primary key,code varchar(30),active_version int,severity varchar(10),enabled boolean,implementation_status varchar(20))");
        jdbc.execute("create table aml_rule_version(rule_id uuid,version_no int,parameters_json varchar(500))");
        jdbc.execute("create table aml_rule_execution(id uuid primary key,execution_key varchar(160),scope varchar(100),started_at timestamp with time zone,finished_at timestamp with time zone,status varchar(20),window_start timestamp with time zone,window_end timestamp with time zone,processed_count int default 0,hit_count int default 0,error_message varchar(200))");
        jdbc.execute("create table aml_transaction(id uuid primary key,account_id uuid,amount numeric(19,4),transaction_time timestamp with time zone,status varchar(20))");
        jdbc.execute("create table aml_account_customer(account_id uuid,customer_id uuid)");
        jdbc.execute("create table aml_rule_hit(id uuid primary key,execution_id uuid,rule_id uuid,rule_version int,transaction_id uuid,customer_id uuid,hit_key varchar(200) unique,score numeric(8,2),explanation varchar(500))");
        jdbc.execute("create table aml_rule_hit_transaction(rule_hit_id uuid,transaction_id uuid)");
        jdbc.execute("create table aml_alert(id uuid primary key,alert_no varchar(50),rule_hit_id uuid,customer_id uuid,risk_level varchar(10),status varchar(20),summary varchar(500))");
        jdbc.execute("create table aml_alert_transaction(alert_id uuid,transaction_id uuid)");
        jdbc.execute("create table aml_alert_history(id uuid,alert_id uuid,to_status varchar(20),comment varchar(500))");
        accountId = UUID.randomUUID(); customerId = UUID.randomUUID();
        jdbc.update("insert into aml_account_customer values(?,?)", accountId, customerId);
        addRule("AML-R001", "{\"amountThreshold\":100}");
        addRule("AML-R002", "{\"windowMinutes\":10,\"countThreshold\":3}");
        addRule("AML-R003", "{\"windowHours\":1,\"amountThreshold\":300}");
        service = new RuleExecutionService(jdbc, new ObjectMapper(), mock(AuditService.class),
                new DroolsRuleEvaluator(), new RuleExecutionStateService(jdbc), manager);
    }

    private void addRule(String code, String parameters) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into aml_rule values(?,?,1,'HIGH',true,'IMPLEMENTED')", id, code);
        jdbc.update("insert into aml_rule_version values(?,1,?)", id, parameters);
    }

    private void transaction(String time, int amount) {
        jdbc.update("insert into aml_transaction values(?,?,?,?,'COMPLETED')", UUID.randomUUID(), accountId,
                amount, Timestamp.from(Instant.parse(time)));
    }

    @Test
    void windowsIncludeLowerBoundaryAndRepeatIsIdempotent() {
        transaction("2020-01-01T00:00:00Z", 150);
        transaction("2026-10-14T09:49:59Z", 150);
        transaction("2026-10-14T09:50:00Z", 150);
        transaction("2026-10-14T10:00:00Z", 150);
        transaction("2026-10-14T09:59:59Z", 50);
        ExecuteRulesRequest request = new ExecuteRulesRequest(List.of("AML-R001", "AML-R002", "AML-R003"),
                Instant.parse("2026-10-14T10:00:00Z"));
        RuleExecutionService.Result first = service.execute(request);
        assertThat(first.processed()).isEqualTo(5);
        assertThat(first.rules().stream().map(RuleExecutionService.RuleResult::newHits)).containsExactly(4, 1, 1);
        assertThat(first.hits()).isEqualTo(6);
        assertThat(service.execute(request).hits()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from aml_alert", Integer.class)).isEqualTo(6);
    }

    @Test
    void failureSurvivesBusinessTransactionRollback() {
        jdbc.update("update aml_rule_version set parameters_json='{}' where rule_id=(select id from aml_rule where code='AML-R001')");
        assertThatThrownBy(() -> service.execute(new ExecuteRulesRequest(List.of("AML-R001"), Instant.now())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select status from aml_rule_execution", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select error_message from aml_rule_execution", String.class))
                .isEqualTo("RULE_EXECUTION_FAILED:IllegalStateException");
        assertThat(jdbc.queryForObject("select count(*) from aml_rule_hit", Integer.class)).isZero();
    }
}
