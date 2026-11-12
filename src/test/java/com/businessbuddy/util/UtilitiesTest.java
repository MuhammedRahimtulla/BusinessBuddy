package com.businessbuddy.util;

import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.validation.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests of the money, date-range, CSV and password helpers (NFR-01, NFR-02). */
class UtilitiesTest {

    @Test
    @DisplayName("TC-MONEY-01: weighted-average cost of 1000 g at 0.02 plus 1000 g at 0.03 is 0.025")
    void weightedAverage() {
        assertThat(Money.weightedAverage(new BigDecimal("1000"), new BigDecimal("0.02"),
                new BigDecimal("1000"), new BigDecimal("0.03"))).isEqualByComparingTo("0.0250");
    }

    @Test
    @DisplayName("TC-MONEY-02: negative stock counts as zero in the weighted average")
    void weightedAverageWithNegativeStock() {
        assertThat(Money.weightedAverage(new BigDecimal("-5"), new BigDecimal("9.99"),
                new BigDecimal("10"), new BigDecimal("2.00"))).isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("TC-MONEY-03: money is rounded HALF_UP to 2 decimals; percentages convert to rates")
    void rounding() {
        assertThat(Money.money(new BigDecimal("2.345"))).isEqualByComparingTo("2.35");
        assertThat(Money.money(new BigDecimal("2.344"))).isEqualByComparingTo("2.34");
        assertThat(Money.percentToRate(new BigDecimal("15"))).isEqualByComparingTo("0.1500");
        assertThat(Money.rateToPercent(new BigDecimal("0.4500"))).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("TC-CSV-01: values starting with = + - @ are neutralised (CSV formula injection)")
    void csvInjection() {
        String csv = new CsvWriter().row("=SUM(A1:A9)", "+cmd", "@x", "-2+3", "Plain", 12.5).toString();
        assertThat(csv).isEqualTo("\"'=SUM(A1:A9)\",\"'+cmd\",\"'@x\",\"'-2+3\",\"Plain\",12.5\r\n");
    }

    @Test
    @DisplayName("TC-CSV-02: quotes inside values are doubled")
    void csvQuotes() {
        assertThat(new CsvWriter().row("He said \"hi\"").toString()).isEqualTo("\"He said \"\"hi\"\"\"\r\n");
    }

    @Test
    @DisplayName("TC-PWD-01: password policy rejects short, email-equal and common passwords")
    void passwordPolicy() {
        assertThat(PasswordPolicy.check("short", "a@b.test")).isPresent();
        assertThat(PasswordPolicy.check("owner@shop.test", "owner@shop.test")).isPresent();
        assertThat(PasswordPolicy.check("password123", "a@b.test")).isPresent();
        assertThat(PasswordPolicy.check("Crumb-Oven-42!", "a@b.test")).isEmpty();
    }

    @Test
    @DisplayName("TC-DATE-01: a period is inclusive and its previous period has the same length")
    void dateRange() {
        DateRange r = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        assertThat(r.days()).isEqualTo(10);
        assertThat(r.previous()).isEqualTo(new DateRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 30)));
        ZoneId sa = ZoneId.of("Africa/Johannesburg");
        assertThat(r.startInstant(sa).toString()).isEqualTo("2026-09-30T22:00:00Z");
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
