package com.example.aml.dashboard;

import java.time.*;

public record ReportRange(LocalDate from, LocalDate to, Instant startInclusive, Instant endExclusive) {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public static ReportRange of(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now(BUSINESS_ZONE) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(29) : from;
        if (resolvedFrom.isAfter(resolvedTo)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        if (resolvedFrom.plusDays(366).isBefore(resolvedTo)) throw new IllegalArgumentException("统计区间不能超过 366 天");
        return new ReportRange(resolvedFrom, resolvedTo,
                resolvedFrom.atStartOfDay(BUSINESS_ZONE).toInstant(),
                resolvedTo.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
    }
}
