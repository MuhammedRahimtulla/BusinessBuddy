package com.businessbuddy.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Display formatting used by the Thymeleaf templates as {@code ${@fmt.money(x)}}.
 * Money is shown with 2 decimals (D15); quantities without trailing zeros.
 */
@Component("fmt")
public class Fmt {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static DecimalFormat format(String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ENGLISH);
        DecimalFormat f = new DecimalFormat(pattern, symbols);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f;
    }

    public String money(BigDecimal value) {
        return value == null ? "-" : format("#,##0.00").format(value);
    }

    public String cost(BigDecimal value) {
        return value == null ? "-" : format("#,##0.00##").format(value);
    }

    public String qty(BigDecimal value) {
        return value == null ? "-" : format("#,##0.###").format(value);
    }

    /** 0.2500 is shown as 25.0%; null (undefined) as "n/a". */
    public String pct(BigDecimal rate) {
        return rate == null ? "n/a" : format("0.0").format(rate.multiply(Money.HUNDRED)) + "%";
    }

    public String dateTime(Instant instant, String zone) {
        return instant == null ? "-" : DATE_TIME.format(instant.atZone(ZoneId.of(zone)));
    }

    public String date(java.time.LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    public boolean negative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    /** Rounded to a whole number for estimates ("about 12 units"). */
    public String whole(BigDecimal value) {
        return value == null ? "-" : qty(value.setScale(0, RoundingMode.HALF_UP));
    }

    /** One decimal place, for estimated daily values. */
    public String oneDecimal(BigDecimal value) {
        return value == null ? "-" : format("#,##0.0").format(value);
    }

    /** Stockout wording: "within a day", "in about 1 day", "in about 5 days". */
    public String runOut(BigDecimal days) {
        return days == null ? "-" : com.businessbuddy.service.insights.InsightsService.runOutWords(days);
    }

    public boolean atMost(BigDecimal value, int limit) {
        return value != null && value.compareTo(BigDecimal.valueOf(limit)) <= 0;
    }
}
