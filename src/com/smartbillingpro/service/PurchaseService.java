package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.PurchaseItem;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.LedgerRepo;
import com.smartbillingpro.repo.PartyRepo;
import com.smartbillingpro.repo.PaymentRepo;
import com.smartbillingpro.repo.ProductRepo;
import com.smartbillingpro.repo.PurchaseRepo;
import com.smartbillingpro.repo.ReferenceRepo;
import com.smartbillingpro.repo.SequenceRepo;
import com.smartbillingpro.repo.StockRepo;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Stock purchases from suppliers. Receiving a purchase raises stock, updates
 * the average cost price, and records the bill and any payment on the supplier
 * ledger - all in one transaction.
 */
public final class PurchaseService {

    public record Line(long variantId, long qty, long unitCost) {
    }

    public record PurchaseForm(Long supplierId, String billRef, List<Line> lines, long discount, long amountPaid,
                               Long methodId, String note) {
    }

    private final Db db;
    private final PurchaseRepo purchases;
    private final ProductRepo products;
    private final StockRepo stock;
    private final PartyRepo parties;
    private final LedgerRepo ledger;
    private final PaymentRepo payments;
    private final ReferenceRepo refs;
    private final SequenceRepo sequences;
    private final AuditRepo audit;

    public PurchaseService(Db db, PurchaseRepo purchases, ProductRepo products, StockRepo stock, PartyRepo parties,
                           LedgerRepo ledger, PaymentRepo payments, ReferenceRepo refs, SequenceRepo sequences,
                           AuditRepo audit) {
        this.db = db;
        this.purchases = purchases;
        this.products = products;
        this.stock = stock;
        this.parties = parties;
        this.ledger = ledger;
        this.payments = payments;
        this.refs = refs;
        this.sequences = sequences;
        this.audit = audit;
    }

    public static long lineTotal(Line l) {
        return Money.mulQty(l.unitCost(), l.qty());
    }

    public Purchase receive(PurchaseForm f) {
        if (f.lines() == null || f.lines().isEmpty()) {
            throw new AppException("Add at least one product to the purchase.");
        }
        long subtotal = 0;
        for (Line l : f.lines()) {
            if (l.qty() <= 0) {
                throw new AppException("Every quantity must be greater than zero.");
            }
            if (l.unitCost() < 0) {
                throw new AppException("Costs cannot be negative.");
            }
            subtotal += lineTotal(l);
        }
        if (f.discount() < 0 || f.discount() > subtotal) {
            throw new AppException("The discount must be between 0 and the bill subtotal (" + Money.format(subtotal) + ").");
        }
        long total = subtotal - f.discount();
        if (f.amountPaid() < 0 || f.amountPaid() > total) {
            throw new AppException("The amount paid must be between 0 and the bill total (" + Money.format(total) + ").");
        }
        long due = total - f.amountPaid();
        Supplier supplier = f.supplierId() == null ? null : parties.supplier(f.supplierId());
        if (f.supplierId() != null && supplier == null) {
            throw new AppException("Supplier not found.");
        }
        if (due > 0 && supplier == null) {
            throw new AppException("Select a supplier to record an unpaid balance of " + Money.format(due) + ".");
        }
        PaymentMethod method = null;
        if (f.amountPaid() > 0) {
            method = f.methodId() == null ? null : refs.method(f.methodId());
            if (method == null) {
                throw new AppException("Choose how the supplier was paid.");
            }
        }
        final PaymentMethod pm = method;
        final long sub = subtotal;
        long[] weights = f.lines().stream().mapToLong(PurchaseService::lineTotal).toArray();
        long[] discountShares = Money.allocate(f.discount(), weights);

        long id = db.tx(() -> {
            String no = SequenceRepo.format("PUR", sequences.next("purchase"));
            Purchase header = new Purchase(0, no, supplier == null ? null : supplier.id(),
                    supplier == null ? null : supplier.name(), Dates.now(), Text.trimToNull(f.billRef()), sub,
                    f.discount(), total, f.amountPaid(), due, pm == null ? null : pm.id(), pm == null ? null : pm.name(),
                    "RECEIVED", Text.trimToNull(f.note()), null, List.of());
            long pid = purchases.insert(header);
            for (int i = 0; i < f.lines().size(); i++) {
                Line l = f.lines().get(i);
                SellableItem item = products.sellable(l.variantId());
                if (item == null) {
                    throw new AppException("A product on this purchase no longer exists.");
                }
                if (!item.fractional() && !Qty.isWhole(l.qty())) {
                    throw new AppException(item.displayName() + " is counted in whole " + item.unitCode() + ".");
                }
                long lt = lineTotal(l);
                purchases.insertItem(pid, new PurchaseItem(0, pid, item.variantId(), item.productName(),
                        item.variantName(), item.unitCode(), l.qty(), l.unitCost(), lt));
                long effective = lt - discountShares[i];
                long newCost = averageCost(item.stockQty(), item.costPrice(), l.qty(), effective);
                products.updateCost(item.variantId(), newCost);
                stock.apply(item.variantId(), l.qty(), "PURCHASE", l.unitCost(), no, supplier == null ? null
                        : "From " + supplier.name(), null, pid, null);
            }
            Long paymentId = null;
            if (f.amountPaid() > 0) {
                paymentId = payments.insert(SequenceRepo.format("PAY", sequences.next("payment")), "OUT",
                        supplier == null ? "OTHER" : "SUPPLIER", supplier == null ? null : supplier.id(),
                        f.amountPaid(), pm.id(), pm.name(), null, pid, null, null, null, "Payment for " + no);
            }
            if (supplier != null) {
                ledger.add(PartyType.SUPPLIER, supplier.id(), "PURCHASE", no,
                        "Purchase " + no + (Text.blank(f.billRef()) ? "" : " (bill " + f.billRef().trim() + ")"),
                        0, total, null, pid, null, null);
                if (f.amountPaid() > 0) {
                    ledger.add(PartyType.SUPPLIER, supplier.id(), "PURCHASE_PAYMENT", no,
                            "Paid at purchase (" + pm.name() + ")", f.amountPaid(), 0, null, pid, paymentId, null);
                }
            }
            audit.log("PURCHASE", no + " total " + Money.plain(total));
            return pid;
        });
        return purchases.find(id);
    }

    /** Moving-average cost after receiving {@code qty} for a total of {@code value}. */
    static long averageCost(long stockQty, long currentCost, long qty, long value) {
        long existing = Math.max(0, stockQty);
        BigDecimal existingValue = BigDecimal.valueOf(Money.mulQty(currentCost, existing));
        BigDecimal totalQty = BigDecimal.valueOf(existing + qty);
        if (totalQty.signum() == 0) {
            return currentCost;
        }
        return existingValue.add(BigDecimal.valueOf(value)).multiply(BigDecimal.valueOf(Qty.FACTOR))
                .divide(totalQty, 0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Cancel a purchase entered by mistake: stock is taken back out and the
     * supplier ledger is credited back. The purchase stays visible as "Reversed".
     */
    public void reverse(long purchaseId, String reason) {
        if (Text.blank(reason)) {
            throw new AppException("Please enter why this purchase is being reversed.");
        }
        db.txRun(() -> {
            Purchase p = purchases.find(purchaseId);
            if (p == null) {
                throw new AppException("Purchase not found.");
            }
            if (p.reversed()) {
                throw new AppException("This purchase has already been reversed.");
            }
            for (PurchaseItem item : p.items()) {
                stock.apply(item.variantId(), -item.quantity(), "PURCHASE_REVERSAL", item.unitCost(), p.purchaseNo(),
                        reason.trim(), null, purchaseId, null);
            }
            purchases.markReversed(purchaseId, reason.trim());
            if (p.supplierId() != null) {
                ledger.add(PartyType.SUPPLIER, p.supplierId(), "PURCHASE_REVERSAL", p.purchaseNo(),
                        "Purchase reversed: " + reason.trim(), p.total(), 0, null, purchaseId, null, null);
            } else if (p.amountPaid() > 0) {
                payments.insert(SequenceRepo.format("PAY", sequences.next("payment")), "IN", "OTHER", null,
                        p.amountPaid(), p.paymentMethodId(), p.paymentMethodName(), null, purchaseId, null, null, null,
                        "Money back for reversed purchase " + p.purchaseNo());
            }
            audit.log("PURCHASE_REVERSAL", p.purchaseNo() + ": " + reason.trim());
        });
    }

    public Purchase find(long id) {
        return purchases.find(id);
    }

    public List<Purchase> list(String fromInclusive, String toExclusive, Long supplierId, String search) {
        return purchases.list(fromInclusive, toExclusive, supplierId, search, 5000);
    }
}
