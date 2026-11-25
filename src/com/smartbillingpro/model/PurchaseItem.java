package com.smartbillingpro.model;

public record PurchaseItem(long id, long purchaseId, long variantId, String productName, String variantName,
                           String unitCode, long quantity, long unitCost, long lineTotal) {

    public String displayName() {
        return variantName == null || variantName.isBlank() ? productName : productName + " (" + variantName + ")";
    }
}
