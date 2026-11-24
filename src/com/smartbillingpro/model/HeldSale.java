package com.smartbillingpro.model;

public record HeldSale(long id, String createdAt, String label, String payload, int itemCount, long total) {
}
