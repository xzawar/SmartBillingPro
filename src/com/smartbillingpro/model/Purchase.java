package com.smartbillingpro.model;

import java.util.List;

public record Purchase(long id, String purchaseNo, Long supplierId, String supplierName, String createdAt,
                       String billRef, long subtotal, long discount, long total, long amountPaid, long balanceDue,
                       Long paymentMethodId, String paymentMethodName, String status, String note,
                       String reversalReason, List<PurchaseItem> items) {

    public boolean reversed() {
        return "REVERSED".equals(status);
    }
}
