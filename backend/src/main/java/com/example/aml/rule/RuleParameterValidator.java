package com.example.aml.rule;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Set;

@Component
public class RuleParameterValidator {
    public void validate(String code, JsonNode parameters) {
        if (parameters == null || !parameters.isObject()) throw new IllegalArgumentException("规则参数必须是 JSON 对象");
        switch (code) {
            case "AML-R001" -> {
                requireOnly(parameters, Set.of("amountThreshold"));
                positiveDecimal(parameters, "amountThreshold");
            }
            case "AML-R002" -> {
                requireOnly(parameters, Set.of("windowMinutes", "countThreshold"));
                boundedInteger(parameters, "windowMinutes", 1, 10_080);
                boundedInteger(parameters, "countThreshold", 2, 10_000);
            }
            case "AML-R003" -> {
                requireOnly(parameters, Set.of("windowHours", "amountThreshold"));
                boundedInteger(parameters, "windowHours", 1, 720);
                positiveDecimal(parameters, "amountThreshold");
            }
            default -> throw new IllegalArgumentException("该复杂规则尚未提供受控执行模板，不能创建新版本");
        }
    }

    private static void requireOnly(JsonNode node, Set<String> expected) {
        Set<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new IllegalArgumentException("参数字段必须为 " + expected);
    }
    private static BigDecimal positiveDecimal(JsonNode node, String field) {
        if (!node.has(field) || !node.get(field).isNumber() || node.get(field).decimalValue().signum() <= 0) {
            throw new IllegalArgumentException(field + " 必须为大于 0 的数字");
        }
        return node.get(field).decimalValue();
    }
    private static int boundedInteger(JsonNode node, String field, int min, int max) {
        if (!node.has(field) || !node.get(field).isIntegralNumber()) {
            throw new IllegalArgumentException(field + " 必须为整数");
        }
        int value = node.get(field).intValue();
        if (value < min || value > max) throw new IllegalArgumentException(field + " 必须在 " + min + " 到 " + max + " 之间");
        return value;
    }
}
