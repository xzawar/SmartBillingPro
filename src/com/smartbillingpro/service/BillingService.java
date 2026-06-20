package com.smartbillingpro.service;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceItem;
import com.smartbillingpro.model.InvoiceStatus;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Holds the in-progress cart for the current sale and does the math. */
public class BillingService {

    private final List<InvoiceItem> items = new ArrayList<>();
    private Customer customer;
    // Customer details typed in at the till. These are always captured on the
    // receipt even for a walk-in customer who is not saved to the database.
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal taxPercent = BigDecimal.ZERO;

    public List<InvoiceItem> getItems() {
        return items;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    /** Capture the buyer's details typed in on the billing screen. */
    public void setCustomerInfo(String name, String phone, String address) {
        this.customerName = name;
        this.customerPhone = phone;
        this.customerAddress = address;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount == null ? BigDecimal.ZERO : discount;
    }

    public BigDecimal getTaxPercent() {
        return taxPercent;
    }

    public void setTaxPercent(BigDecimal taxPercent) {
        this.taxPercent = taxPercent == null ? BigDecimal.ZERO : taxPercent;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    private InvoiceItem findLine(int productId) {
        for (InvoiceItem item : items) {
            if (item.getProductId() == productId) {
                return item;
            }
        }
        return null;
    }

    /** Add a quantity of a product, merging with an existing line. */
    public void addProduct(Product product, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }
        InvoiceItem existing = findLine(product.getId());
        int newQty = (existing == null ? 0 : existing.getQuantity()) + qty;
        if (newQty > product.getQuantity()) {
            throw new IllegalArgumentException("Only " + product.getQuantity() + " of " + product.getName() + " in stock.");
        }
        if (existing != null) {
            existing.setQuantity(newQty);
        } else {
            InvoiceItem item = new InvoiceItem(product.getId(), product.getSku(), product.getName(),
                    product.getSalePrice(), qty);
            item.setCostPrice(product.getCostPrice());
            items.add(item);
        }
    }

    /** Set an exact quantity for a line, removing it when zero. */
    public void setLineQuantity(int productId, int qty, int availableStock) {
        InvoiceItem item = findLine(productId);
        if (item == null) {
            return;
        }
        if (qty <= 0) {
            items.remove(item);
            return;
        }
        if (qty > availableStock) {
            throw new IllegalArgumentException("Only " + availableStock + " in stock.");
        }
        item.setQuantity(qty);
    }

    public void removeLine(int productId) {
        InvoiceItem item = findLine(productId);
        if (item != null) {
            items.remove(item);
        }
    }

    /** Reset the cart but keep the configured tax percent for the next sale. */
    public void clear() {
        items.clear();
        customer = null;
        customerName = null;
        customerPhone = null;
        customerAddress = null;
        discount = BigDecimal.ZERO;
    }

    public int totalUnits() {
        int units = 0;
        for (InvoiceItem item : items) {
            units += item.getQuantity();
        }
        return units;
    }

    public BigDecimal subtotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceItem item : items) {
            total = total.add(item.getLineTotal());
        }
        return Money.scale(total);
    }

    public BigDecimal effectiveDiscount() {
        BigDecimal sub = subtotal();
        return discount.min(sub).max(BigDecimal.ZERO);
    }

    public BigDecimal taxableAmount() {
        return Money.scale(subtotal().subtract(effectiveDiscount()));
    }

    public BigDecimal taxAmount() {
        return taxableAmount().multiply(taxPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal grandTotal() {
        return Money.scale(taxableAmount().add(taxAmount()));
    }

    /** Build an Invoice snapshot from the current cart, ready to be saved. */
    public Invoice buildInvoice(String cashier, BigDecimal amountPaid) {
        Invoice inv = new Invoice();
        inv.setItems(new ArrayList<>(items));
        inv.setSubtotal(subtotal());
        inv.setDiscount(effectiveDiscount());
        inv.setTaxPercent(taxPercent);
        inv.setTaxAmount(taxAmount());
        inv.setGrandTotal(grandTotal());
        inv.setAmountPaid(Money.scale(amountPaid));
        inv.setChangeDue(Money.scale(amountPaid.subtract(grandTotal())));
        inv.setPaymentMethod(PaymentMethod.CASH);
        inv.setStatus(InvoiceStatus.COMPLETED);
        inv.setCashier(cashier);
        // Typed-in details win; fall back to the saved customer record if present.
        String name = customerName;
        String phone = customerPhone;
        String address = customerAddress;
        if (customer != null) {
            inv.setCustomerId(customer.getId());
            if (name == null || name.isBlank()) {
                name = customer.getName();
            }
            if (phone == null || phone.isBlank()) {
                phone = customer.getPhone();
            }
            if (address == null || address.isBlank()) {
                address = customer.getAddress();
            }
        }
        inv.setCustomerName(name == null || name.isBlank() ? "Walk-in Customer" : name);
        inv.setCustomerPhone(phone);
        inv.setCustomerAddress(address);
        return inv;
    }
}
