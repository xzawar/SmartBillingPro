package com.smartbillingpro.model;

/**
 * A supplier. {@code balance} is the amount the shop owes the supplier
 * (positive = payable), computed from the ledger.
 */
public record Supplier(long id, String name, String phone, String address, String businessInfo,
                       String notes, boolean active, String createdAt, long balance) {
    @Override
    public String toString() {
        return name;
    }
}
