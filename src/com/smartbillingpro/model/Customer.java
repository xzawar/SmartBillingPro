package com.smartbillingpro.model;

/**
 * A customer. {@code balance} is the amount the customer owes the shop
 * (positive = receivable, negative = advance / we owe them), computed from the ledger.
 */
public record Customer(long id, String name, String phone, String address, String notes,
                       boolean active, String createdAt, long balance) {
    @Override
    public String toString() {
        return phone == null || phone.isBlank() ? name : name + " · " + phone;
    }
}
