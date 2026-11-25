package com.smartbillingpro.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Date helpers. Timestamps are stored in SQLite as local time text
 * {@code yyyy-MM-dd HH:mm:ss}, which sorts correctly and is easy to filter.
 * The display format is configurable from Settings.
 */
public final class Dates {

    public static final DateTimeFormatter STORED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static final String[] DISPLAY_PATTERNS = {"dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "dd MMM yyyy"};

    private static volatile DateTimeFormatter displayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
    private static volatile DateTimeFormatter displayTime = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static volatile String displayPattern = "dd/MM/yyyy";

    private Dates() {
    }

    public static void setDisplayPattern(String pattern) {
        try {
            displayDate = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
            displayPattern = pattern;
        } catch (IllegalArgumentException e) {
            displayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
            displayPattern = "dd/MM/yyyy";
        }
    }

    public static String displayPattern() {
        return displayPattern;
    }

    public static String now() {
        return LocalDateTime.now().withNano(0).format(STORED);
    }

    public static String stored(LocalDateTime time) {
        return time.withNano(0).format(STORED);
    }

    public static LocalDateTime parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(stored.trim(), STORED);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(stored.trim().substring(0, 10), ISO_DATE).atStartOfDay();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    public static LocalDate dateOf(String stored) {
        LocalDateTime t = parse(stored);
        return t == null ? null : t.toLocalDate();
    }

    /** Inclusive lower bound for a SQL range filter on stored timestamps. */
    public static String startOf(LocalDate date) {
        return date.atStartOfDay().format(STORED);
    }

    /** Exclusive upper bound: the start of the following day. */
    public static String endExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay().format(STORED);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(displayDate);
    }

    public static String formatDate(String stored) {
        LocalDateTime t = parse(stored);
        return t == null ? "" : t.toLocalDate().format(displayDate);
    }

    public static String formatDateTime(String stored) {
        LocalDateTime t = parse(stored);
        return t == null ? "" : t.format(displayDate) + " " + t.format(displayTime);
    }

    public static String formatTime(LocalDateTime t) {
        return t == null ? "" : t.format(displayTime);
    }

    /** Parse a date typed by the user in the display format (or ISO). */
    public static LocalDate parseDisplayDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), displayDate);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(text.trim(), ISO_DATE);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }
}
