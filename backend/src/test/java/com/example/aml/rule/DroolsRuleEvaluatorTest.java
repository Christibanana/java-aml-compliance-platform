package com.example.aml.rule;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DroolsRuleEvaluatorTest {
    private final DroolsRuleEvaluator evaluator = new DroolsRuleEvaluator();

    @Test
    void compilesOnDrools10AndMatchesAllThreeControlledRules() {
        List<RuleEvaluationFact> facts = List.of(fact("AML-R001", "100000", "100000"),
                fact("AML-R002", "10", "10"), fact("AML-R003", "500000", "500000"));
        assertThat(evaluator.evaluate(facts)).isEqualTo(3);
        assertThat(facts).allMatch(RuleEvaluationFact::isMatched);
        assertThat(evaluator.runtimeVersion()).startsWith("10.1");
    }

    @Test
    void doesNotMatchMetricsBelowThresholdOrUnknownRule() {
        List<RuleEvaluationFact> facts = List.of(fact("AML-R001", "99999.99", "100000"),
                fact("AML-R002", "9", "10"), fact("AML-R003", "499999.99", "500000"),
                fact("AML-R999", "999999", "1"));
        assertThat(evaluator.evaluate(facts)).isZero();
        assertThat(facts).noneMatch(RuleEvaluationFact::isMatched);
    }

    private RuleEvaluationFact fact(String code, String metric, String threshold) {
        UUID transactionId = UUID.randomUUID();
        return new RuleEvaluationFact(code, transactionId.toString(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(transactionId), new BigDecimal(metric), new BigDecimal(threshold),
                Instant.parse("2026-10-14T00:00:00Z"), Instant.parse("2026-10-14T01:00:00Z"), "test");
    }
}
