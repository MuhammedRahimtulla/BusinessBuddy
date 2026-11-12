package com.businessbuddy.util;

import com.businessbuddy.exception.BusinessRuleException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive range of calendar dates, interpreted in the business's time zone (4.6.11).
 * {@link #startInstant} and {@link #endInstantExclusive} turn it into the half-open
 * interval used to query timestamp columns.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from == null || to == null) {
            throw new BusinessRuleException("Choose a start and end date");
        }
        if (from.isAfter(to)) {
            throw new BusinessRuleException("Start date must be before end date");
        }
    }

    /** Default report period: the first day of the current month up to today. */
    public static DateRange monthToDate(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return new DateRange(today.withDayOfMonth(1), today);
    }

    public static DateRange lastDays(ZoneId zone, int days) {
        LocalDate today = LocalDate.now(zone);
        return new DateRange(today.minusDays(days - 1L), today);
    }

    public static DateRange of(LocalDate from, LocalDate to, ZoneId zone) {
        if (from == null && to == null) {
            return monthToDate(zone);
        }
        LocalDate today = LocalDate.now(zone);
        return new DateRange(from == null ? today.withDayOfMonth(1) : from, to == null ? today : to);
    }

    public Instant startInstant(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    public Instant endInstantExclusive(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** The period of the same length immediately before this one (used by insights). */
    public DateRange previous() {
        long length = days();
        return new DateRange(from.minusDays(length), from.minusDays(1));
    }

    @Override
    public String toString() {
        return from + " to " + to;
    }
}
