package com.example.aml.rule.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record RuleSummary(UUID id, String code, String name, String ruleType, boolean enabled,
                          String severity, int activeVersion, String implementationStatus,
                          JsonNode parameters, String description) {}
