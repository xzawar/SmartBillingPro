package com.smartbillingpro.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Exact money arithmetic.
 *
 * <p>Every monetary value in the application is a {@code long} holding <b>minor
 * units</b> (paisa for PKR, cents for USD): {@code 125050} means 1,250.50. This
 * is how amounts are stored in SQLite too, so sums and comparisons in SQL are
 * exact integer maths with no floating-point drift.</p>
 */
public final class Money {

    /** Number of decimal places for the configured currency. */
    public static final int SCALE = 2;
    private static final long FACTOR = 100L;

    private static volatile String symbol = "Rs";

    private Money() {
    }

    /** Set the currency symbol used by {@link #format(long)} (e.g. "Rs", "$"). */
    public static void setSymbol(String value) {
        symbol = value == null ? "" : value.trim();
    }

    public static String symbol() {
        return symbol;
    }

    /** 1250.50 -> 125050. Rounds half-up to the currency scale. */
    public static long fromDecimal(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).longValueExact();
    }

    /** 125050 -> 1250.50 */
    public static BigDecimal toDecimal(long minor) {
        return BigDecimal.valueOf(minor, SCALE);
    }

    /**
     * Parse user input such as "1,250.5" or "Rs 300". Throws
     * {@link IllegalArgumentException} with a readable message for invalid input.
     */
    public static long parse(String text) {
        String cleaned = clean(text);
        if (cleaned.isEmpty()) {
            return 0;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.scale() > SCALE && value.stripTrailingZeros().scale() > SCALE) {
                throw new IllegalArgumentException("Amounts can have at most " + SCALE + " decimal places.");
            }
            return fromDecimal(value);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("\"" + text.trim() + "\" is not a valid amount.");
        }
    }

    /** Like {@link #parse(String)} but returns 0 for anything invalid. */
    public static long parseOrZero(String text) {
        try {
            return parse(text);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == '-') {
                sb.append(c);
            } else if (c == ',' || Character.isWhitespace(c) || Character.isLetter(c) || c == '$') {
                // thousands separators, currency symbols and codes are ignored
            } else {
                return "x"; // force a NumberFormatException for junk such as "12#4"
            }
        }
        return sb.toString();
    }

    /** "1250.50" - plain text suitable for an input field. */
    public static String plain(long minor) {
        return toDecimal(minor).toPlainString();
    }

    /** "Rs 1,250.50" / "-Rs 50.00" */
    public static String format(long minor) {
        String digits = grouped(Math.abs(minor));
        String sym = symbol;
        String body = sym.isEmpty() ? digits : sym + " " + digits;
        return minor < 0 ? "-" + body : body;
    }

    /** "1,250.50" without a currency symbol. */
    public static String grouped(long minor) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(toDecimal(minor));
    }

    /**
     * Price x quantity. {@code priceMinor} is per one unit, {@code qtyMilli} is the
     * quantity in thousandths ({@link Qty}). 50000 (500.00/kg) x 250 (0.250 kg) = 12500.
     */
    public static long mulQty(long priceMinor, long qtyMilli) {
        return BigDecimal.valueOf(priceMinor)
                .multiply(BigDecimal.valueOf(qtyMilli))
                .divide(BigDecimal.valueOf(Qty.FACTOR), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** {@code amount * percent / 100}, rounded half-up. */
    public static long percentOf(long amount, BigDecimal percent) {
        if (percent == null || percent.signum() == 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount).multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Split {@code total} across lines in proportion to {@code weights} so the
     * parts always add up exactly to {@code total} (largest-remainder method).
     */
    public static long[] allocate(long total, long[] weights) {
        long[] parts = new long[weights.length];
        long weightSum = 0;
        for (long w : weights) {
            weightSum += Math.max(0, w);
        }
        if (total == 0 || weights.length == 0) {
            return parts;
        }
        if (weightSum == 0) {
            parts[weights.length - 1] = total;
            return parts;
        }
        long assigned = 0;
        BigDecimal[] remainders = new BigDecimal[weights.length];
        for (int i = 0; i < weights.length; i++) {
            BigDecimal exact = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(Math.max(0, weights[i])))
                    .divide(BigDecimal.valueOf(weightSum), 10, RoundingMode.DOWN);
            long floor = exact.setScale(0, RoundingMode.DOWN).longValue();
            parts[i] = floor;
            remainders[i] = exact.subtract(BigDecimal.valueOf(floor));
            assigned += floor;
        }
        long left = total - assigned;
        while (left != 0) {
            int best = 0;
            for (int i = 1; i < remainders.length; i++) {
                if (remainders[i].compareTo(remainders[best]) > 0) {
                    best = i;
                }
            }
            parts[best] += Long.signum(left);
            remainders[best] = BigDecimal.valueOf(-1);
            left -= Long.signum(left);
        }
        return parts;
    }

    /** Factor between major and minor units (100). */
    public static long factor() {
        return FACTOR;
    }
}
