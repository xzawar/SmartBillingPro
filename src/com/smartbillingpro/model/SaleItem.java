package com.smartbillingpro.model;

/**
 * A line of a completed sale - a snapshot that never changes. Amounts are minor
 * units; quantities thousandths. {@code netAmount} is after the line discount and
 * the line's share of the cart discount, excluding tax.
 */
public record SaleItem(long id, long saleId, Long variantId, Long productId, String productName, String variantName,
                       String unitCode, String barcode, String categoryName, long quantity, long unitPrice,
                       long unitCost, long grossAmount, long lineDiscount, long cartDiscountShare, long netAmount,
                       long taxAmount, long lineTotal, long costAmount, long returnedQty) {

    public String displayName() {
        return variantName == null || variantName.isBlank() ? productName : productName + " (" + variantName + ")";
    }

    public long returnableQty() {
        return quantity - returnedQty;
    }
}
