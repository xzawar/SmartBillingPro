package com.smartbillingpro.model;

public record Category(long id, String name, int productCount) {
    @Override
    public String toString() {
        return name;
    }
}
