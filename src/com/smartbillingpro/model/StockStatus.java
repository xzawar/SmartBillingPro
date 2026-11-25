package com.smartbillingpro.model;

public enum StockStatus {
    IN_STOCK("In stock"), LOW("Low stock"), OUT("Out of stock");

    private final String label;

    StockStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static StockStatus of(long stock, long minStock) {
        if (stock <= 0) {
            return OUT;
        }
        if (minStock > 0 && stock <= minStock) {
            return LOW;
        }
        return IN_STOCK;
    }
}
