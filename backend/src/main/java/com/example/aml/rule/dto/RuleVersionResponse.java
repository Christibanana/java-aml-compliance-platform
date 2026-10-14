package com.example.aml.rule.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record RuleVersionResponse(UUID id, int versionNo, JsonNode parameters,
                                  String description, Instant createdAt) {}
