package com.smartbillingpro.model;

import java.math.BigDecimal;
import java.util.List;

/** A completed sale (receipt). */
public record Sale(long id, String receiptNo, String createdAt, Long customerId, String customerName,
                   String customerPhone, long subtotal, long itemDiscount, long cartDiscount, BigDecimal taxPercent,
                   long taxAmount, long grandTotal, long amountPaid, long cashReceived, long changeGiven,
                   long balanceDue, Long paymentMethodId, String paymentMethodName, SaleStatus status,
                   long returnedTotal, String note, List<SaleItem> items) {

    public long totalDiscount() {
        return itemDiscount + cartDiscount;
    }

    public boolean walkIn() {
        return customerId == null;
    }

    /** "Paid", "Partial", "Credit". */
    public String paymentStatus() {
        if (balanceDue == 0) {
            return "Paid";
        }
        return amountPaid == 0 ? "Credit" : "Partial";
    }
}
