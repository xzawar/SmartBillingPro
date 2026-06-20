package com.smartbillingpro.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Shared date/time formatters and helpers stored as text in SQLite. */
public final class Dates {

    private Dates() {
    }

    public static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Current timestamp as "yyyy-MM-dd HH:mm:ss". */
    public static String now() {
        return LocalDateTime.now().format(TIMESTAMP);
    }

    /** Today as "yyyy-MM-dd". */
    public static String today() {
        return LocalDate.now().format(DATE);
    }

    /** Extract the date part from a stored timestamp, defaulting to today. */
    public static LocalDate dateOf(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(timestamp.substring(0, 10), DATE);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}
