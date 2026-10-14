package com.example.aml.rule.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRuleStatusRequest(@NotNull Boolean enabled) {}
