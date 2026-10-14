package com.example.aml.casework.dto;

import java.util.List;

public record CasePage(List<CaseSummary> content, long total, int page, int size) {
}
