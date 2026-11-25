package com.smartbillingpro.model;

public record PaymentMethod(long id, String name, boolean cash, boolean active, int sortOrder) {
    @Override
    public String toString() {
        return name;
    }
}
