package com.example.aml.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleParameterValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RuleParameterValidator validator = new RuleParameterValidator();

    @Test
    void acceptsWhitelistedParametersForImplementedRules() throws Exception {
        validator.validate("AML-R001", json.readTree("{\"amountThreshold\":100000}"));
        validator.validate("AML-R002", json.readTree("{\"windowMinutes\":10,\"countThreshold\":10}"));
        validator.validate("AML-R003", json.readTree("{\"windowHours\":24,\"amountThreshold\":500000}"));
    }

    @Test
    void rejectsUnknownFieldsInvalidBoundsAndUnimplementedRules() throws Exception {
        assertThatThrownBy(() -> validator.validate("AML-R001",
                json.readTree("{\"amountThreshold\":100000,\"drl\":\"arbitrary\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("AML-R002",
                json.readTree("{\"windowMinutes\":0,\"countThreshold\":1}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("AML-R004", json.readTree("{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
