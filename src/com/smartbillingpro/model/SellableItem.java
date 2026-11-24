package com.smartbillingpro.model;

import java.math.BigDecimal;

/** A variant together with the product details needed at the till. */
public record SellableItem(long variantId, long productId, String productName, String variantName, String barcode,
                           String sku, long unitId, String unitCode, Dimension dimension, BigDecimal unitFactor,
                           String categoryName, long costPrice, long salePrice, long stockQty, long minStock,
                           boolean active) {

    public boolean fractional() {
        return dimension != Dimension.COUNT;
    }

    public String displayName() {
        return variantName == null || variantName.isBlank() ? productName : productName + " (" + variantName + ")";
    }
}
