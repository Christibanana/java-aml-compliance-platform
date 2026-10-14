package com.example.aml.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CsvCellTest {
    @Test void escapesQuotesAndPreservesPlainValues() {
        assertThat(CsvCell.quoted("张\"三")).isEqualTo("\"张\"\"三\"");
        assertThat(CsvCell.quoted(new BigDecimal("12.30"))).isEqualTo("\"12.30\"");
        assertThat(CsvCell.quoted(null)).isEqualTo("\"\"");
    }

    @Test void neutralizesSpreadsheetFormulaInjectionIncludingLeadingSpaces() {
        assertThat(CsvCell.quoted("=HYPERLINK(\"https://bad.example\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"https://bad.example\"\")\"");
        assertThat(CsvCell.quoted("  @SUM(1,1)")).isEqualTo("\"'  @SUM(1,1)\"");
        assertThat(CsvCell.quoted("+cmd|' /C calc'!A0")).startsWith("\"'+cmd");
    }
}
