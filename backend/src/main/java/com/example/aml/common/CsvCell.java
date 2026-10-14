package com.example.aml.common;

import java.math.BigDecimal;

/** CSV 单元格编码，并阻止电子表格把外部数据解释为公式。 */
public final class CsvCell {
    private CsvCell() {}

    public static String quoted(Object raw) {
        String value = raw == null ? "" : raw instanceof BigDecimal decimal
                ? decimal.toPlainString() : raw.toString();
        String leadingTrimmed = value.stripLeading();
        if (!leadingTrimmed.isEmpty() && isFormulaPrefix(leadingTrimmed.charAt(0))) {
            value = "'" + value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static boolean isFormulaPrefix(char first) {
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
    }
}
