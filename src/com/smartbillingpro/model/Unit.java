package com.smartbillingpro.model;

import java.math.BigDecimal;

/**
 * A unit of measurement. {@code factor} converts to the base unit of the
 * dimension (gram, millilitre, centimetre, piece): kg = 1000, g = 1.
 */
public record Unit(long id, String code, String name, Dimension dimension, BigDecimal factor,
                   boolean builtIn, boolean active) {

    /** Measured products (weight, volume, length) are sold in fractional quantities. */
    public boolean fractional() {
        return dimension != Dimension.COUNT;
    }

    @Override
    public String toString() {
        return name + " (" + code + ")";
    }
}
