package com.smartbillingpro.model;

public record StockMovement(long id, long variantId, String productName, String variantName, String unitCode,
                            String createdAt, String type, long qtyChange, long balanceAfter, Long unitCost,
                            String reference, String reason) {

    public String displayName() {
        return variantName == null || variantName.isBlank() ? productName : productName + " (" + variantName + ")";
    }

    public String typeLabel() {
        return switch (type) {
            case "OPENING" -> "Opening stock";
            case "SALE" -> "Sale";
            case "RETURN" -> "Customer return";
            case "PURCHASE" -> "Purchase";
            case "PURCHASE_REVERSAL" -> "Purchase reversed";
            case "ADJUSTMENT_IN" -> "Adjustment (+)";
            case "ADJUSTMENT_OUT" -> "Adjustment (−)";
            default -> type;
        };
    }
}
