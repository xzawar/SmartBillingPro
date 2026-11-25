package com.smartbillingpro.util;

/** Small string helpers. */
public final class Text {

    private Text() {
    }

    public static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Trimmed text, or {@code null} when blank (for optional database columns). */
    public static String trimToNull(String s) {
        return blank(s) ? null : s.trim();
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public static String orDash(String s) {
        return blank(s) ? "—" : s;
    }

    public static boolean containsIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
