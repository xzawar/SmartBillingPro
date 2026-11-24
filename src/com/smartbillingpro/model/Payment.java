package com.smartbillingpro.model;

/** Money received (IN) or paid out (OUT). */
public record Payment(long id, String paymentNo, String createdAt, String direction, String partyType, Long partyId,
                      String partyName, long amount, Long methodId, String methodName, Long saleId, Long purchaseId,
                      Long returnId, Long expenseId, Long reversesPaymentId, String note, boolean reversed) {

    public boolean incoming() {
        return "IN".equals(direction);
    }
}
