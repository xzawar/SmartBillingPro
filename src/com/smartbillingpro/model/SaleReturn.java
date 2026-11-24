package com.smartbillingpro.model;

public record SaleReturn(long id, String returnNo, long saleId, String receiptNo, String createdAt, String reason,
                         long totalAmount, long cashRefunded, long creditAdjusted, String methodName,
                         String customerName) {
}
