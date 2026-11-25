package com.smartbillingpro.model;

public record StockMovement(long id, long variantId, String productName, String variantName, String unitCode,
                            String createdAt, String type, long qtyChange, long balanceAfter, Long unitCost,
                            String reference, String reason) {

    public String displayName() {
        return variantName == null || variantName.isBlank() ? productName : productName + " (" + variantName + ")";
    }

    public String typeLabel() {
        return switch (type) {
            case "OPENING" -> "Starting stock";
            case "SALE" -> "Sold";
            case "RETURN" -> "Returned by customer";
            case "PURCHASE" -> "Bought";
            case "PURCHASE_REVERSAL" -> "Purchase cancelled";
            case "ADJUSTMENT_IN" -> "Added by hand";
            case "ADJUSTMENT_OUT" -> "Removed by hand";
            default -> type;
        };
    }
}
