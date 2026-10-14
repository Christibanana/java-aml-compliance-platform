package com.example.aml.alert.dto;

import java.util.List;

public record AlertPage(List<AlertSummary> content, long total, int page, int size) {
}
