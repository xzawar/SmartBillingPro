package com.smartbillingpro.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for working with money values consistently (2 decimal places). */
public final class Money {

    private Money() {
    }

    /** Round to 2 decimal places, treating null as zero. */
    public static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Format an amount with the given currency symbol, e.g. "Rs 1,250.00". */
    public static String format(String symbol, BigDecimal value) {
        return (symbol == null ? "" : symbol) + String.format("%,.2f", scale(value));
    }

    /** Parse free-form user text into a money value, ignoring stray characters. */
    public static BigDecimal parse(String text) {
        if (text == null) {
            return BigDecimal.ZERO;
        }
        StringBuilder sb = new StringBuilder();
        boolean dotSeen = false;
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (c == '.' && !dotSeen) {
                dotSeen = true;
                sb.append(c);
            } else if (c == '-' && sb.length() == 0) {
                sb.append(c);
            }
        }
        String s = sb.toString();
        if (s.isEmpty() || s.equals("-") || s.equals(".")) {
            return BigDecimal.ZERO;
        }
        try {
            return scale(new BigDecimal(s));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
