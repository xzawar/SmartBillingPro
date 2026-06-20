package com.smartbillingpro.model;

/** Lifecycle state of a sale. */
public enum InvoiceStatus {
    COMPLETED("Completed"),
    VOIDED("Voided"),
    REFUNDED("Refunded");

    private final String label;

    InvoiceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static InvoiceStatus fromName(String name) {
        if (name != null) {
            for (InvoiceStatus status : values()) {
                if (status.name().equalsIgnoreCase(name.trim())) {
                    return status;
                }
            }
        }
        return COMPLETED;
    }
}
