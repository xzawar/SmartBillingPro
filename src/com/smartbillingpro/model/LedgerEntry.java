package com.smartbillingpro.model;

/**
 * A ledger line. For customers a debit increases what they owe us; for
 * suppliers a credit increases what we owe them. {@code balance} is the running
 * balance after this entry, from the party's point of view (positive = outstanding).
 */
public record LedgerEntry(long id, String createdAt, PartyType partyType, long partyId, String entryType,
                          String reference, String description, long debit, long credit, long balance) {

    public String typeLabel() {
        return switch (entryType) {
            case "OPENING" -> "Opening balance";
            case "SALE" -> "Sale";
            case "SALE_PAYMENT" -> "Paid at sale";
            case "PAYMENT" -> "Payment";
            case "RETURN" -> "Return";
            case "REFUND" -> "Refund";
            case "PURCHASE" -> "Purchase";
            case "PURCHASE_PAYMENT" -> "Paid at purchase";
            case "PURCHASE_REVERSAL" -> "Purchase reversed";
            case "ADJUSTMENT" -> "Adjustment";
            case "REVERSAL" -> "Reversal";
            default -> entryType;
        };
    }
}
