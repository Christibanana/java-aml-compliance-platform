package com.example.aml.rule.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRuleVersionRequest(@NotNull JsonNode parameters,
                                       @NotBlank @Size(max = 500) String description) {}
