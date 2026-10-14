package com.example.aml.rule.dto;

import java.time.Instant;
import java.util.List;

public record ExecuteRulesRequest(List<String> ruleCodes, Instant asOf) {}
