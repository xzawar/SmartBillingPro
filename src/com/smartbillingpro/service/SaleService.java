package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.HeldSale;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleItem;
import com.smartbillingpro.model.SaleReturn;
import com.smartbillingpro.model.SaleStatus;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.HeldSaleRepo;
import com.smartbillingpro.repo.LedgerRepo;
import com.smartbillingpro.repo.PartyRepo;
import com.smartbillingpro.repo.PaymentRepo;
import com.smartbillingpro.repo.ProductRepo;
import com.smartbillingpro.repo.ReferenceRepo;
import com.smartbillingpro.repo.SaleRepo;
import com.smartbillingpro.repo.SequenceRepo;
import com.smartbillingpro.repo.StockRepo;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Json;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Completes sales and returns. Everything a sale touches - the sale record, its
 * lines, stock, the payment and the customer ledger - is written in one
 * database transaction, so a sale is either fully recorded or not at all.
 */
public final class SaleService {

    /** How the customer paid. {@code received} is what was handed over (cash tendered). */
    public record PaymentInput(long methodId, long received, String note) {
    }

    /** One line of a return request. */
    public record ReturnLine(long saleItemId, long qty) {
    }

    private final Db db;
    private final SaleRepo sales;
    private final ProductRepo products;
    private final StockRepo stock;
    private final PaymentRepo payments;
    private final LedgerRepo ledger;
    private final PartyRepo parties;
    private final ReferenceRepo refs;
    private final SequenceRepo sequences;
    private final HeldSaleRepo held;
    private final AuditRepo audit;
    private final SettingsService settings;

    public SaleService(Db db, SaleRepo sales, ProductRepo products, StockRepo stock, PaymentRepo payments,
                       LedgerRepo ledger, PartyRepo parties, ReferenceRepo refs, SequenceRepo sequences,
                       HeldSaleRepo held, AuditRepo audit, SettingsService settings) {
        this.db = db;
        this.sales = sales;
        this.products = products;
        this.stock = stock;
        this.payments = payments;
        this.ledger = ledger;
        this.parties = parties;
        this.refs = refs;
        this.sequences = sequences;
        this.held = held;
        this.audit = audit;
        this.settings = settings;
    }

    public String previewNextReceiptNo() {
        return SequenceRepo.format(settings.get().receiptPrefix(), sequences.peek("receipt"));
    }

    /**
     * Record the sale in {@code cart}. Re-validates every line against the
     * database (stock, archived products) and recalculates all totals; the UI's
     * numbers are never trusted blindly.
     *
     * @return the saved sale with its receipt number
     */
    public Sale complete(Cart cart, PaymentInput pay) {
        if (cart.isEmpty()) {
            throw new AppException("The cart is empty. Add at least one product.");
        }
        if (sales.tokenExists(cart.token())) {
            throw new AppException("This sale has already been saved.");
        }
        PaymentMethod method = refs.method(pay.methodId());
        if (method == null || !method.active()) {
            throw new AppException("Please choose a payment method.");
        }
        if (pay.received() < 0) {
            throw new AppException("The amount received cannot be negative.");
        }
        Cart.Totals totals = cart.totals();
        long grand = totals.grandTotal();
        long applied = Math.min(pay.received(), grand);
        long change = Math.max(0, pay.received() - grand);
        long due = grand - applied;
        Customer customer = cart.customer();
        if (customer != null) {
            customer = parties.customer(customer.id());
            if (customer == null || !customer.active()) {
                throw new AppException("The selected customer no longer exists.");
            }
        }
        if (due > 0 && customer == null) {
            throw new AppException("The amount received is less than the total (" + Money.format(grand)
                    + "). Select a customer to record the remaining " + Money.format(due) + " on their account.");
        }
        final Customer buyer = customer;
        String now = Dates.now();

        long saleId = db.tx(() -> {
            String receiptNo = SequenceRepo.format(settings.get().receiptPrefix(), sequences.next("receipt"));
            Sale header = new Sale(0, receiptNo, now, buyer == null ? null : buyer.id(),
                    buyer == null ? "Walk-in Customer" : buyer.name(), buyer == null ? null : buyer.phone(),
                    totals.subtotal(), totals.itemDiscount(), totals.cartDiscount(), cart.taxPercent(), totals.tax(),
                    grand, applied, pay.received(), change, due, method.id(), method.name(), SaleStatus.COMPLETED, 0,
                    blankToNull(cart.note()), List.of());
            long id = sales.insertSale(header, cart.token());

            for (Cart.Allocation a : totals.lines()) {
                SellableItem fresh = products.sellable(a.line().item().variantId());
                if (fresh == null || !fresh.active()) {
                    throw new AppException(a.line().item().displayName() + " is no longer available for sale.");
                }
                long qty = a.line().qty();
                long cost = Money.mulQty(fresh.costPrice(), qty);
                SaleItem item = new SaleItem(0, id, fresh.variantId(), fresh.productId(), fresh.productName(),
                        fresh.variantName(), fresh.unitCode(), fresh.barcode(), fresh.categoryName(), qty,
                        a.line().unitPrice(), fresh.costPrice(), a.gross(), a.lineDiscount(), a.cartShare(), a.net(),
                        a.tax(), a.total(), cost, 0);
                sales.insertItem(id, item);
                stock.apply(fresh.variantId(), -qty, "SALE", fresh.costPrice(), receiptNo, null, id, null, null);
            }

            if (applied > 0) {
                String payNo = SequenceRepo.format("PAY", sequences.next("payment"));
                long paymentId = payments.insert(payNo, "IN", buyer == null ? "WALK_IN" : "CUSTOMER",
                        buyer == null ? null : buyer.id(), applied, method.id(), method.name(), id, null, null, null,
                        null, "Payment for " + receiptNo);
                if (buyer != null) {
                    ledger.add(PartyType.CUSTOMER, buyer.id(), "SALE", receiptNo, "Sale " + receiptNo, grand, 0,
                            id, null, null, null);
                    ledger.add(PartyType.CUSTOMER, buyer.id(), "SALE_PAYMENT", receiptNo,
                            "Paid at sale (" + method.name() + ")", 0, applied, id, null, paymentId, null);
                }
            } else if (buyer != null && grand > 0) {
                ledger.add(PartyType.CUSTOMER, buyer.id(), "SALE", receiptNo, "Credit sale " + receiptNo, grand, 0,
                        id, null, null, null);
            }
            audit.log("SALE", receiptNo + " total " + Money.plain(grand) + (due > 0 ? " due " + Money.plain(due) : ""));
            return id;
        });
        return sales.find(saleId);
    }

    public Sale find(long id) {
        return sales.find(id);
    }

    public Sale findByReceipt(String receiptNo) {
        return sales.findByReceipt(receiptNo);
    }

    public List<Sale> list(String fromInclusive, String toExclusive, String search, Long methodId, Long customerId,
                           SaleStatus status, int limit) {
        return sales.list(fromInclusive, toExclusive, search, methodId, customerId, status, limit);
    }

    public List<SaleReturn> returnsFor(long saleId) {
        return sales.returnsForSale(saleId);
    }

    public List<SaleReturn> returns(String fromInclusive, String toExclusive) {
        return sales.returns(fromInclusive, toExclusive);
    }

    // ------------------------------------------------------------------ returns

    /** Value (incl. tax) of returning {@code qty} of a sale line, proportional to what was charged. */
    public static long[] returnValue(SaleItem item, long qty) {
        long net = proportion(item.netAmount(), qty, item.quantity());
        long tax = proportion(item.taxAmount(), qty, item.quantity());
        long cost = proportion(item.costAmount(), qty, item.quantity());
        if (qty == item.returnableQty()) {
            // Last part of the line: take whatever is left so totals match exactly.
            long alreadyQty = item.returnedQty();
            net = item.netAmount() - proportion(item.netAmount(), alreadyQty, item.quantity());
            tax = item.taxAmount() - proportion(item.taxAmount(), alreadyQty, item.quantity());
            cost = item.costAmount() - proportion(item.costAmount(), alreadyQty, item.quantity());
        }
        return new long[] {net, tax, net + tax, cost};
    }

    private static long proportion(long amount, long part, long whole) {
        if (whole == 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(part))
                .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** The most cash that may be refunded for a return of value {@code returnValue}. */
    public long maxCashRefund(Sale sale, long returnValue) {
        if (sale.walkIn()) {
            return returnValue;
        }
        long paidLeft = sale.amountPaid() - sales.cashRefunded(sale.id());
        return Math.max(0, Math.min(returnValue, paidLeft));
    }

    /**
     * Return items from a sale: stock goes back, the sale is marked (partly)
     * returned, and the refund is recorded - as cash out, as a reduction of the
     * customer's balance, or both.
     */
    public SaleReturn processReturn(long saleId, List<ReturnLine> lines, long cashRefund, Long refundMethodId,
                                    String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AppException("Please enter a reason for the return.");
        }
        if (lines.stream().map(ReturnLine::saleItemId).distinct().count() != lines.size()) {
            throw new AppException("Each sale line can appear only once in a return.");
        }
        long returnId = db.tx(() -> {
            Sale sale = sales.find(saleId);
            if (sale == null) {
                throw new AppException("Sale not found.");
            }
            if (sale.status() == SaleStatus.RETURNED) {
                throw new AppException("All items of this sale have already been returned.");
            }
            long total = 0;
            List<long[]> values = new ArrayList<>();
            List<SaleItem> items = new ArrayList<>();
            for (ReturnLine rl : lines) {
                if (rl.qty() <= 0) {
                    continue;
                }
                SaleItem item = sales.item(rl.saleItemId());
                if (item == null || item.saleId() != saleId) {
                    throw new AppException("A returned item does not belong to this sale.");
                }
                if (rl.qty() > item.returnableQty()) {
                    throw new AppException("You can return at most " + Qty.format(item.returnableQty(), item.unitCode())
                            + " of " + item.displayName() + ".");
                }
                long[] v = returnValue(item, rl.qty());
                values.add(v);
                items.add(item);
                total += v[2];
            }
            if (items.isEmpty()) {
                throw new AppException("Enter the quantity to return for at least one item.");
            }
            long maxCash = maxCashRefund(sale, total);
            if (cashRefund < 0 || cashRefund > maxCash) {
                throw new AppException("The cash refund must be between 0 and " + Money.format(maxCash) + ".");
            }
            if (sale.walkIn() && cashRefund != total) {
                throw new AppException("For walk-in customers the full value (" + Money.format(total) + ") is refunded.");
            }
            PaymentMethod method = null;
            if (cashRefund > 0) {
                method = refundMethodId == null ? null : refs.method(refundMethodId);
                if (method == null) {
                    throw new AppException("Choose how the refund is paid.");
                }
            }
            long credit = total - cashRefund;
            String returnNo = SequenceRepo.format("RET", sequences.next("return"));
            long rid = sales.insertReturn(returnNo, saleId, Dates.now(), reason.trim(), total, cashRefund, credit,
                    method == null ? null : method.id(), method == null ? null : method.name());
            for (int i = 0; i < items.size(); i++) {
                SaleItem item = items.get(i);
                long qty = lines.stream().filter(l -> l.saleItemId() == item.id()).findFirst().orElseThrow().qty();
                long[] v = values.get(i);
                sales.insertReturnItem(rid, item.id(), qty, v[0], v[1], v[2], v[3]);
                sales.addReturnedQty(item.id(), qty);
                if (item.variantId() != null) {
                    stock.apply(item.variantId(), qty, "RETURN", item.unitCost(), returnNo, reason.trim(), saleId, null, rid);
                }
            }
            long returnedTotal = sale.returnedTotal() + total;
            boolean all = sales.items(saleId).stream().allMatch(it -> it.returnedQty() == it.quantity());
            sales.updateReturnState(saleId, all ? SaleStatus.RETURNED : SaleStatus.PARTIALLY_RETURNED,
                    Math.min(returnedTotal, sale.grandTotal()));

            Long paymentId = null;
            if (cashRefund > 0) {
                paymentId = payments.insert(SequenceRepo.format("PAY", sequences.next("payment")), "OUT",
                        sale.walkIn() ? "WALK_IN" : "CUSTOMER", sale.customerId(), cashRefund, method.id(),
                        method.name(), saleId, null, rid, null, null, "Refund " + returnNo + " for " + sale.receiptNo());
            }
            if (!sale.walkIn()) {
                ledger.add(PartyType.CUSTOMER, sale.customerId(), "RETURN", returnNo,
                        "Goods returned from " + sale.receiptNo(), 0, total, saleId, null, null, rid);
                if (cashRefund > 0) {
                    ledger.add(PartyType.CUSTOMER, sale.customerId(), "REFUND", returnNo,
                            "Cash refunded (" + method.name() + ")", cashRefund, 0, saleId, null, paymentId, rid);
                }
            }
            audit.log("RETURN", returnNo + " for " + sale.receiptNo() + " value " + Money.plain(total) + ": " + reason.trim());
            return rid;
        });
        return sales.returnsForSale(saleId).stream().filter(r -> r.id() == returnId).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------- held sales

    public void hold(Cart cart, String label) {
        if (cart.isEmpty()) {
            throw new AppException("There is nothing to hold.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", cart.customer() == null ? null : String.valueOf(cart.customer().id()));
        data.put("discountAmount", String.valueOf(cart.discountAmount()));
        data.put("discountPercent", cart.discountPercent() == null ? null : cart.discountPercent().toPlainString());
        data.put("note", cart.note());
        List<Object> list = new ArrayList<>();
        for (Cart.Line l : cart.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("variantId", String.valueOf(l.item().variantId()));
            m.put("qty", String.valueOf(l.qty()));
            m.put("price", String.valueOf(l.unitPrice()));
            m.put("discount", String.valueOf(l.lineDiscount()));
            list.add(m);
        }
        data.put("lines", list);
        String name = label == null || label.isBlank()
                ? (cart.customer() == null ? "Held sale" : cart.customer().name()) : label.trim();
        held.insert(name, Json.write(data), cart.lines().size(), cart.totals().grandTotal());
        cart.clear();
    }

    public List<HeldSale> heldSales() {
        return held.list();
    }

    public long heldCount() {
        return held.count();
    }

    public void deleteHeld(long id) {
        held.delete(id);
    }

    /**
     * Load a held sale into {@code cart} (which is cleared first) and remove it
     * from the held list. Returns warnings for lines that could not be restored.
     */
    @SuppressWarnings("unchecked")
    public List<String> recall(HeldSale sale, Cart cart) {
        cart.clear();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> data = (Map<String, Object>) Json.parse(sale.payload());
        Object cid = data.get("customerId");
        if (cid != null) {
            Customer c = parties.customer(Long.parseLong(cid.toString()));
            if (c != null && c.active()) {
                cart.setCustomer(c);
            }
        }
        Object pct = data.get("discountPercent");
        if (pct != null) {
            cart.setDiscountPercent(new BigDecimal(pct.toString()));
        } else if (data.get("discountAmount") != null) {
            cart.setDiscountAmount(Long.parseLong(data.get("discountAmount").toString()));
        }
        cart.setNote(data.get("note") == null ? null : data.get("note").toString());
        for (Object o : (List<Object>) data.get("lines")) {
            Map<String, Object> m = (Map<String, Object>) o;
            SellableItem item = products.sellable(Long.parseLong(m.get("variantId").toString()));
            if (item == null) {
                warnings.add("A product in this sale no longer exists.");
                continue;
            }
            try {
                Cart.Line line = cart.add(item, Long.parseLong(m.get("qty").toString()));
                cart.setUnitPrice(line, Long.parseLong(m.get("price").toString()));
                cart.setLineDiscount(line, Long.parseLong(m.get("discount").toString()));
            } catch (AppException e) {
                warnings.add(e.getMessage());
            }
        }
        held.delete(sale.id());
        return warnings;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
