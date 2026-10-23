package com.example.aml.rule;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class RuleExecutionStateService {
    private final JdbcTemplate jdbc;

    public RuleExecutionStateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(UUID id, String scope, Instant windowStart, Instant windowEnd) {
        jdbc.update("insert into aml_rule_execution(id,execution_key,scope,started_at,status,window_start,window_end) "
                        + "values(?,?,?,?,?,?,?)", id, "RULE-RUN-" + UUID.randomUUID(), scope,
                Timestamp.from(Instant.now()), "RUNNING", Timestamp.from(windowStart), Timestamp.from(windowEnd));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id, int processed, int hits) {
        jdbc.update("update aml_rule_execution set status='COMPLETED',finished_at=?,processed_count=?,hit_count=? where id=?",
                Timestamp.from(Instant.now()), processed, hits, id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, int processed, int hits, String category) {
        jdbc.update("update aml_rule_execution set status='FAILED',finished_at=?,processed_count=?,hit_count=?,error_message=? where id=?",
                Timestamp.from(Instant.now()), processed, hits, category, id);
    }
}
