package com.smartbillingpro.model;

public enum SaleStatus {
    COMPLETED("Completed"), PARTIALLY_RETURNED("Partly returned"), RETURNED("Returned");

    private final String label;

    SaleStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
