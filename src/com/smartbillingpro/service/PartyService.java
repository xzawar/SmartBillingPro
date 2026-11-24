package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.LedgerEntry;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.Payment;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.LedgerRepo;
import com.smartbillingpro.repo.PartyRepo;
import com.smartbillingpro.repo.PaymentRepo;
import com.smartbillingpro.repo.ReferenceRepo;
import com.smartbillingpro.repo.SequenceRepo;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import java.util.List;

/**
 * Customers, suppliers, their payments and their ledgers. Balances are never
 * stored on the party: they are always the sum of ledger entries, which are
 * append-only. Corrections are recorded as adjustments or reversals.
 */
public final class PartyService {

    /** Totals shown on a customer's profile. */
    public record CustomerStats(long totalPurchases, long totalPaid, long balance, long salesCount, String lastSale) {
    }

    /** Totals shown on a supplier's profile. */
    public record SupplierStats(long totalPurchases, long totalPaid, long balance, long purchaseCount,
                                String lastPurchase) {
    }

    private final Db db;
    private final PartyRepo parties;
    private final LedgerRepo ledger;
    private final PaymentRepo payments;
    private final ReferenceRepo refs;
    private final SequenceRepo sequences;
    private final AuditRepo audit;

    public PartyService(Db db, PartyRepo parties, LedgerRepo ledger, PaymentRepo payments, ReferenceRepo refs,
                        SequenceRepo sequences, AuditRepo audit) {
        this.db = db;
        this.parties = parties;
        this.ledger = ledger;
        this.payments = payments;
        this.refs = refs;
        this.sequences = sequences;
        this.audit = audit;
    }

    // ------------------------------------------------------------ customers

    public List<Customer> customers(boolean includeInactive) {
        return parties.customers(includeInactive);
    }

    public Customer customer(long id) {
        return parties.customer(id);
    }

    /**
     * Create (id == null) or update a customer. The opening balance is only used
     * when creating: positive means the customer already owes the shop.
     */
    public long saveCustomer(Long id, String name, String phone, String address, String notes, long openingBalance,
                             boolean active) {
        String n = Text.trim(name);
        if (n.isEmpty()) {
            throw new AppException("Please enter the customer's name.");
        }
        String ph = Text.trimToNull(phone);
        if (ph != null && !ph.matches("[0-9+()\\-\\s]{4,20}")) {
            throw new AppException("The phone number can only contain digits, spaces, +, - and brackets.");
        }
        if (ph != null) {
            Customer dup = parties.customerByPhone(ph);
            if (dup != null && (id == null || dup.id() != id)) {
                throw new AppException("Another customer (" + dup.name() + ") already has the phone number " + ph + ".");
            }
        }
        return db.tx(() -> {
            if (id == null) {
                long newId = parties.insertCustomer(n, ph, Text.trimToNull(address), Text.trimToNull(notes));
                if (openingBalance != 0) {
                    ledger.add(PartyType.CUSTOMER, newId, "OPENING", null, "Opening balance",
                            Math.max(0, openingBalance), Math.max(0, -openingBalance), null, null, null, null);
                }
                audit.log("CUSTOMER_ADD", n);
                return newId;
            }
            parties.updateCustomer(id, n, ph, Text.trimToNull(address), Text.trimToNull(notes), active);
            audit.log("CUSTOMER_EDIT", n);
            return id;
        });
    }

    public CustomerStats customerStats(long customerId) {
        long purchases = db.scalar("SELECT COALESCE(SUM(grand_total - returned_total), 0) FROM sales WHERE customer_id = ?",
                customerId);
        long paidIn = db.scalar("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE party_type = 'CUSTOMER' "
                + "AND party_id = ? AND direction = 'IN'", customerId);
        long paidOut = db.scalar("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE party_type = 'CUSTOMER' "
                + "AND party_id = ? AND direction = 'OUT'", customerId);
        long count = db.scalar("SELECT COUNT(*) FROM sales WHERE customer_id = ?", customerId);
        String last = db.scalarText("SELECT MAX(created_at) FROM sales WHERE customer_id = ?", customerId);
        return new CustomerStats(purchases, paidIn - paidOut, ledger.balance(PartyType.CUSTOMER, customerId, null),
                count, last);
    }

    // ------------------------------------------------------------ suppliers

    public List<Supplier> suppliers(boolean includeInactive) {
        return parties.suppliers(includeInactive);
    }

    public Supplier supplier(long id) {
        return parties.supplier(id);
    }

    /** Opening balance (create only): positive means the shop already owes the supplier. */
    public long saveSupplier(Long id, String name, String phone, String address, String business, String notes,
                             long openingBalance, boolean active) {
        String n = Text.trim(name);
        if (n.isEmpty()) {
            throw new AppException("Please enter the supplier's name.");
        }
        String ph = Text.trimToNull(phone);
        if (ph != null && !ph.matches("[0-9+()\\-\\s]{4,20}")) {
            throw new AppException("The phone number can only contain digits, spaces, +, - and brackets.");
        }
        return db.tx(() -> {
            if (id == null) {
                long newId = parties.insertSupplier(n, ph, Text.trimToNull(address), Text.trimToNull(business),
                        Text.trimToNull(notes));
                if (openingBalance != 0) {
                    ledger.add(PartyType.SUPPLIER, newId, "OPENING", null, "Opening balance",
                            Math.max(0, -openingBalance), Math.max(0, openingBalance), null, null, null, null);
                }
                audit.log("SUPPLIER_ADD", n);
                return newId;
            }
            parties.updateSupplier(id, n, ph, Text.trimToNull(address), Text.trimToNull(business),
                    Text.trimToNull(notes), active);
            audit.log("SUPPLIER_EDIT", n);
            return id;
        });
    }

    public SupplierStats supplierStats(long supplierId) {
        long purchases = db.scalar("SELECT COALESCE(SUM(total), 0) FROM purchases WHERE supplier_id = ? AND status = 'RECEIVED'",
                supplierId);
        long paidOut = db.scalar("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE party_type = 'SUPPLIER' "
                + "AND party_id = ? AND direction = 'OUT'", supplierId);
        long paidIn = db.scalar("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE party_type = 'SUPPLIER' "
                + "AND party_id = ? AND direction = 'IN'", supplierId);
        long count = db.scalar("SELECT COUNT(*) FROM purchases WHERE supplier_id = ? AND status = 'RECEIVED'", supplierId);
        String last = db.scalarText("SELECT MAX(created_at) FROM purchases WHERE supplier_id = ?", supplierId);
        return new SupplierStats(purchases, paidOut - paidIn, ledger.balance(PartyType.SUPPLIER, supplierId, null),
                count, last);
    }

    // ------------------------------------------------------------ payments

    private PaymentMethod method(long methodId) {
        PaymentMethod m = refs.method(methodId);
        if (m == null || !m.active()) {
            throw new AppException("Please choose a payment method.");
        }
        return m;
    }

    /** Money received from a customer against their account. */
    public Payment receiveFromCustomer(long customerId, long amount, long methodId, String note) {
        if (amount <= 0) {
            throw new AppException("Enter an amount greater than zero.");
        }
        Customer c = parties.customer(customerId);
        if (c == null) {
            throw new AppException("Customer not found.");
        }
        PaymentMethod m = method(methodId);
        long id = db.tx(() -> {
            String no = SequenceRepo.format("PAY", sequences.next("payment"));
            long pid = payments.insert(no, "IN", "CUSTOMER", customerId, amount, m.id(), m.name(), null, null, null,
                    null, null, Text.trimToNull(note));
            ledger.add(PartyType.CUSTOMER, customerId, "PAYMENT", no,
                    "Payment received (" + m.name() + ")" + (Text.blank(note) ? "" : " - " + note.trim()), 0, amount,
                    null, null, pid, null);
            audit.log("CUSTOMER_PAYMENT", no + " " + c.name() + " " + Money.plain(amount));
            return pid;
        });
        return payments.find(id);
    }

    /** Money paid to a supplier against their account. */
    public Payment payToSupplier(long supplierId, long amount, long methodId, String note) {
        if (amount <= 0) {
            throw new AppException("Enter an amount greater than zero.");
        }
        Supplier s = parties.supplier(supplierId);
        if (s == null) {
            throw new AppException("Supplier not found.");
        }
        PaymentMethod m = method(methodId);
        long id = db.tx(() -> {
            String no = SequenceRepo.format("PAY", sequences.next("payment"));
            long pid = payments.insert(no, "OUT", "SUPPLIER", supplierId, amount, m.id(), m.name(), null, null, null,
                    null, null, Text.trimToNull(note));
            ledger.add(PartyType.SUPPLIER, supplierId, "PAYMENT", no,
                    "Payment made (" + m.name() + ")" + (Text.blank(note) ? "" : " - " + note.trim()), amount, 0,
                    null, null, pid, null);
            audit.log("SUPPLIER_PAYMENT", no + " " + s.name() + " " + Money.plain(amount));
            return pid;
        });
        return payments.find(id);
    }

    /**
     * Undo a payment that was recorded by mistake. The original stays in the
     * history; an opposite payment and a REVERSAL ledger entry are added.
     */
    public void reversePayment(long paymentId, String reason) {
        if (Text.blank(reason)) {
            throw new AppException("Please enter why the payment is being reversed.");
        }
        Payment p = payments.find(paymentId);
        if (p == null) {
            throw new AppException("Payment not found.");
        }
        if (p.reversed() || p.reversesPaymentId() != null) {
            throw new AppException("This payment has already been reversed.");
        }
        if (p.saleId() != null || p.purchaseId() != null || p.returnId() != null || p.expenseId() != null) {
            throw new AppException("This payment belongs to a sale, purchase or expense. Use a return, a purchase "
                    + "reversal or void the expense instead.");
        }
        if (!"CUSTOMER".equals(p.partyType()) && !"SUPPLIER".equals(p.partyType())) {
            throw new AppException("Only customer and supplier payments can be reversed here.");
        }
        db.txRun(() -> {
            String no = SequenceRepo.format("PAY", sequences.next("payment"));
            String dir = p.incoming() ? "OUT" : "IN";
            long pid = payments.insert(no, dir, p.partyType(), p.partyId(), p.amount(), p.methodId(), p.methodName(),
                    null, null, null, null, p.id(), "Reversal of " + p.paymentNo() + ": " + reason.trim());
            PartyType type = PartyType.valueOf(p.partyType());
            long debit;
            long credit;
            if (type == PartyType.CUSTOMER) {
                debit = p.incoming() ? p.amount() : 0;
                credit = p.incoming() ? 0 : p.amount();
            } else {
                debit = p.incoming() ? p.amount() : 0;
                credit = p.incoming() ? 0 : p.amount();
            }
            ledger.add(type, p.partyId(), "REVERSAL", no, "Reversal of " + p.paymentNo() + ": " + reason.trim(),
                    debit, credit, null, null, pid, null);
            audit.log("PAYMENT_REVERSAL", p.paymentNo() + ": " + reason.trim());
        });
    }

    public List<Payment> payments(String fromInclusive, String toExclusive, String partyType, Long partyId,
                                  String direction) {
        return payments.list(fromInclusive, toExclusive, partyType, partyId, direction, 5000);
    }

    // ------------------------------------------------------------ ledger

    public List<LedgerEntry> statement(PartyType type, long partyId, String fromInclusive, String toExclusive) {
        return ledger.statement(type, partyId, fromInclusive, toExclusive);
    }

    public long balance(PartyType type, long partyId) {
        return ledger.balance(type, partyId, null);
    }

    public long balanceBefore(PartyType type, long partyId, String beforeExclusive) {
        return ledger.balance(type, partyId, beforeExclusive);
    }

    public long totalReceivable() {
        return ledger.totalOutstanding(PartyType.CUSTOMER);
    }

    public long totalPayable() {
        return ledger.totalOutstanding(PartyType.SUPPLIER);
    }

    /**
     * Correct a balance without editing history. A positive {@code amount}
     * increases what the party owes (customer) / what we owe (supplier).
     */
    public void adjustBalance(PartyType type, long partyId, long amount, String reason) {
        if (amount == 0) {
            throw new AppException("Enter a non-zero adjustment amount.");
        }
        if (Text.blank(reason)) {
            throw new AppException("Please enter the reason for this adjustment.");
        }
        boolean increase = amount > 0;
        long abs = Math.abs(amount);
        long debit;
        long credit;
        if (type == PartyType.CUSTOMER) {
            debit = increase ? abs : 0;
            credit = increase ? 0 : abs;
        } else {
            debit = increase ? 0 : abs;
            credit = increase ? abs : 0;
        }
        db.txRun(() -> {
            ledger.add(type, partyId, "ADJUSTMENT", null, reason.trim(), debit, credit, null, null, null, null);
            audit.log("LEDGER_ADJUST", type + " " + partyId + " " + Money.plain(amount) + ": " + reason.trim());
        });
    }
}
