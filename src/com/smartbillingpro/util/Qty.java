package com.smartbillingpro.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Exact quantities. Stock and sale quantities are {@code long} values in
 * <b>thousandths</b> of the product's unit: 1 piece = 1000, 0.250 kg = 250.
 * Whole-unit products simply always use multiples of 1000.
 */
public final class Qty {

    public static final int SCALE = 3;
    public static final long FACTOR = 1000L;
    public static final long ONE = FACTOR;

    private Qty() {
    }

    public static long of(long wholeUnits) {
        return Math.multiplyExact(wholeUnits, FACTOR);
    }

    public static long fromDecimal(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).longValueExact();
    }

    public static BigDecimal toDecimal(long milli) {
        return BigDecimal.valueOf(milli, SCALE);
    }

    /** Parse "2", "0.25", "1,000". Throws {@link IllegalArgumentException} when invalid. */
    public static long parse(String text) {
        String t = text == null ? "" : text.trim().replace(",", "");
        if (t.isEmpty()) {
            return 0;
        }
        try {
            BigDecimal value = new BigDecimal(t);
            if (value.stripTrailingZeros().scale() > SCALE) {
                throw new IllegalArgumentException("Quantities can have at most 3 decimal places.");
            }
            return fromDecimal(value);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("\"" + text.trim() + "\" is not a valid quantity.");
        }
    }

    /** True when the quantity is a whole number of units. */
    public static boolean isWhole(long milli) {
        return milli % FACTOR == 0;
    }

    /** 2000 -> "2", 250 -> "0.25", 1500 -> "1.5" */
    public static String format(long milli) {
        BigDecimal d = toDecimal(milli).stripTrailingZeros();
        if (d.scale() < 0) {
            d = d.setScale(0);
        }
        return d.toPlainString();
    }

    /** Quantity with its unit: "2 pcs", "0.25 kg". */
    public static String format(long milli, String unitCode) {
        String q = format(milli);
        return unitCode == null || unitCode.isBlank() ? q : q + " " + unitCode;
    }
}
