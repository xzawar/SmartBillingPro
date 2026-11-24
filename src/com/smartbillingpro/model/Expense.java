package com.smartbillingpro.model;

public record Expense(long id, String expenseNo, String createdAt, String expenseDate, String category, long amount,
                      Long methodId, String methodName, String note, String status, String voidReason) {

    public boolean voided() {
        return "VOIDED".equals(status);
    }
}
