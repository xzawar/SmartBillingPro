package com.smartbillingpro.model;

/** How a sale was paid. Cash only for this build. */
public enum PaymentMethod {
    CASH("Cash");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PaymentMethod fromName(String name) {
        if (name != null) {
            for (PaymentMethod method : values()) {
                if (method.name().equalsIgnoreCase(name.trim())) {
                    return method;
                }
            }
        }
        return CASH;
    }
}
