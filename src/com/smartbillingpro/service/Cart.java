package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The sale being rung up at the till. Pure in-memory logic with exact integer
 * maths: line totals, discounts and tax. The cart also carries a unique token so
 * the same cart can never be saved as two sales (double click, double Enter).
 */
public final class Cart {

    /** One line in the cart. */
    public static final class Line {
        private SellableItem item;
        private long qty;
        private long unitPrice;
        private long lineDiscount;

        Line(SellableItem item, long qty) {
            this.item = item;
            this.qty = qty;
            this.unitPrice = item.salePrice();
        }

        public SellableItem item() {
            return item;
        }

        public long qty() {
            return qty;
        }

        public long unitPrice() {
            return unitPrice;
        }

        public long lineDiscount() {
            return lineDiscount;
        }

        public long gross() {
            return Money.mulQty(unitPrice, qty);
        }

        /** After the line discount, before the cart discount and tax. */
        public long netBeforeCartDiscount() {
            return gross() - Math.min(lineDiscount, gross());
        }

        void refresh(SellableItem fresh) {
            this.item = fresh;
        }
    }

    /** A line with its share of the cart discount and tax. */
    public record Allocation(Line line, long gross, long lineDiscount, long cartShare, long net, long tax, long total) {
    }

    public record Totals(long subtotal, long itemDiscount, long cartDiscount, long taxable, long tax, long grandTotal,
                         List<Allocation> lines) {
        public long totalDiscount() {
            return itemDiscount + cartDiscount;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private Customer customer;
    private long discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal taxPercent = BigDecimal.ZERO;
    private String token = UUID.randomUUID().toString();
    private String note;

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public String token() {
        return token;
    }

    public Customer customer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String note() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal taxPercent() {
        return taxPercent;
    }

    public void setTaxPercent(BigDecimal percent) {
        this.taxPercent = percent == null ? BigDecimal.ZERO : percent;
    }

    /** Fixed discount on the whole bill (minor units). */
    public void setDiscountAmount(long amount) {
        if (amount < 0) {
            throw new AppException("Discount cannot be negative.");
        }
        this.discountAmount = amount;
        this.discountPercent = null;
    }

    /** Percentage discount on the whole bill. */
    public void setDiscountPercent(BigDecimal percent) {
        if (percent == null || percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new AppException("Discount percent must be between 0 and 100.");
        }
        this.discountPercent = percent;
        this.discountAmount = 0;
    }

    public boolean discountIsPercent() {
        return discountPercent != null;
    }

    public BigDecimal discountPercent() {
        return discountPercent;
    }

    public long discountAmount() {
        return discountAmount;
    }

    public long qtyInCart(long variantId) {
        long total = 0;
        for (Line l : lines) {
            if (l.item.variantId() == variantId) {
                total += l.qty;
            }
        }
        return total;
    }

    /**
     * Add {@code qty} (thousandths) of an item; merges with an existing line for the
     * same variant at the same price. Refuses to exceed the available stock.
     */
    public Line add(SellableItem item, long qty) {
        if (!item.active()) {
            throw new AppException(item.displayName() + " is archived and cannot be sold.");
        }
        if (qty <= 0) {
            throw new AppException("Quantity must be more than zero.");
        }
        if (!item.fractional() && !Qty.isWhole(qty)) {
            throw new AppException(item.displayName() + " is sold in whole " + item.unitCode() + " only.");
        }
        checkStock(item, qtyInCart(item.variantId()) + qty);
        for (Line l : lines) {
            if (l.item.variantId() == item.variantId() && l.unitPrice == item.salePrice() && l.lineDiscount == 0) {
                l.qty += qty;
                l.refresh(item);
                return l;
            }
        }
        Line line = new Line(item, qty);
        lines.add(line);
        return line;
    }

    public void setQty(Line line, long qty) {
        if (qty <= 0) {
            lines.remove(line);
            return;
        }
        if (!line.item.fractional() && !Qty.isWhole(qty)) {
            throw new AppException(line.item.displayName() + " is sold in whole " + line.item.unitCode() + " only.");
        }
        long others = qtyInCart(line.item.variantId()) - line.qty;
        checkStock(line.item, others + qty);
        line.qty = qty;
        if (line.lineDiscount > line.gross()) {
            line.lineDiscount = line.gross();
        }
    }

    /** Change quantity by whole units (+1 / -1). */
    public void step(Line line, int units) {
        setQty(line, line.qty + Qty.of(units));
    }

    public void setUnitPrice(Line line, long price) {
        if (price < 0) {
            throw new AppException("Price cannot be negative.");
        }
        line.unitPrice = price;
        if (line.lineDiscount > line.gross()) {
            line.lineDiscount = line.gross();
        }
    }

    public void setLineDiscount(Line line, long discount) {
        if (discount < 0) {
            throw new AppException("Discount cannot be negative.");
        }
        if (discount > line.gross()) {
            throw new AppException("The discount cannot be more than the line amount ("
                    + Money.format(line.gross()) + ").");
        }
        line.lineDiscount = discount;
    }

    public void remove(Line line) {
        lines.remove(line);
    }

    /** Empty the cart and issue a new token for the next sale. */
    public void clear() {
        lines.clear();
        customer = null;
        discountAmount = 0;
        discountPercent = null;
        note = null;
        token = UUID.randomUUID().toString();
    }

    private static void checkStock(SellableItem item, long wanted) {
        if (wanted > item.stockQty()) {
            String available = item.stockQty() <= 0 ? "it is out of stock"
                    : "only " + Qty.format(item.stockQty(), item.unitCode()) + " in stock";
            throw new AppException("Cannot add " + item.displayName() + ": " + available + ".");
        }
    }

    public Totals totals() {
        int n = lines.size();
        long subtotal = 0;
        long itemDiscount = 0;
        long[] bases = new long[n];
        for (int i = 0; i < n; i++) {
            Line l = lines.get(i);
            subtotal += l.gross();
            itemDiscount += Math.min(l.lineDiscount, l.gross());
            bases[i] = l.netBeforeCartDiscount();
        }
        long afterItems = subtotal - itemDiscount;
        long cartDiscount = discountPercent != null ? Money.percentOf(afterItems, discountPercent) : discountAmount;
        cartDiscount = Math.max(0, Math.min(cartDiscount, afterItems));
        long taxable = afterItems - cartDiscount;
        long tax = Money.percentOf(taxable, taxPercent);

        long[] shares = Money.allocate(cartDiscount, bases);
        long[] nets = new long[n];
        for (int i = 0; i < n; i++) {
            nets[i] = bases[i] - shares[i];
        }
        long[] taxes = Money.allocate(tax, nets);
        List<Allocation> alloc = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Line l = lines.get(i);
            alloc.add(new Allocation(l, l.gross(), Math.min(l.lineDiscount, l.gross()), shares[i], nets[i], taxes[i],
                    nets[i] + taxes[i]));
        }
        return new Totals(subtotal, itemDiscount, cartDiscount, taxable, tax, taxable + tax, alloc);
    }

    /** Re-point lines at fresh product data (e.g. after a sale changed stock elsewhere). */
    public void refreshItem(SellableItem fresh) {
        for (Line l : lines) {
            if (l.item.variantId() == fresh.variantId()) {
                l.refresh(fresh);
            }
        }
    }
}
