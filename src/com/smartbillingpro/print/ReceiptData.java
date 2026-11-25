package com.smartbillingpro.print;

import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleItem;
import com.smartbillingpro.model.SaleStatus;
import com.smartbillingpro.service.Settings;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import java.util.ArrayList;
import java.util.List;

/** Everything printed on a receipt, already formatted as text. */
public record ReceiptData(String storeName, List<String> storeLines, String header, String footer, String receiptNo,
                          String dateTime, String customerName, String customerPhone, String paymentMethod,
                          List<Line> lines, List<Total> totals, String grandTotal, List<Total> payment,
                          String statusNote, String currencyNote) {

    public record Line(String name, String qtyText, String unitPrice, String amount, String discount) {
    }

    /** A label/value row in the totals block. */
    public record Total(String label, String value, boolean strong) {
    }

    public static ReceiptData of(Sale s, Settings st) {
        List<String> storeLines = new ArrayList<>();
        if (!Text.blank(st.storeAddress())) {
            storeLines.add(st.storeAddress().trim());
        }
        StringBuilder contact = new StringBuilder();
        if (!Text.blank(st.storePhone())) {
            contact.append("Tel: ").append(st.storePhone().trim());
        }
        if (!Text.blank(st.storeEmail())) {
            contact.append(contact.length() > 0 ? "  " : "").append(st.storeEmail().trim());
        }
        if (contact.length() > 0) {
            storeLines.add(contact.toString());
        }
        if (!Text.blank(st.storeTaxId())) {
            storeLines.add("Tax No: " + st.storeTaxId().trim());
        }
        List<Line> lines = new ArrayList<>();
        for (SaleItem i : s.items()) {
            lines.add(new Line(i.displayName(), Qty.format(i.quantity(), i.unitCode()), Money.grouped(i.unitPrice()),
                    Money.grouped(i.grossAmount()), i.lineDiscount() > 0 ? "-" + Money.grouped(i.lineDiscount()) : null));
        }
        List<Total> totals = new ArrayList<>();
        totals.add(new Total("Subtotal", Money.grouped(s.subtotal()), false));
        if (s.totalDiscount() > 0) {
            totals.add(new Total("Discount", "-" + Money.grouped(s.totalDiscount()), false));
        }
        if (s.taxAmount() > 0) {
            totals.add(new Total(st.taxLabel() + " (" + s.taxPercent().stripTrailingZeros().toPlainString() + "%)",
                    Money.grouped(s.taxAmount()), false));
        }
        List<Total> payment = new ArrayList<>();
        payment.add(new Total("Paid (" + Text.orDash(s.paymentMethodName()) + ")", Money.grouped(s.cashReceived()), false));
        if (s.changeGiven() > 0) {
            payment.add(new Total("Change", Money.grouped(s.changeGiven()), false));
        }
        if (s.balanceDue() > 0) {
            payment.add(new Total("Balance due", Money.grouped(s.balanceDue()), true));
        }
        if (s.returnedTotal() > 0) {
            payment.add(new Total("Returned", "-" + Money.grouped(s.returnedTotal()), false));
        }
        String status = s.status() == SaleStatus.COMPLETED ? null : "*** " + s.status().label().toUpperCase() + " ***";
        String currency = "Amounts in " + (Text.blank(st.currencyCode()) ? st.currencySymbol() : st.currencyCode());
        return new ReceiptData(st.storeName(), storeLines, Text.trim(st.receiptHeader()), Text.trim(st.receiptFooter()),
                s.receiptNo(), Dates.formatDateTime(s.createdAt()), s.customerName(), s.customerPhone(),
                Text.orDash(s.paymentMethodName()), lines, totals, Money.symbol() + " " + Money.grouped(s.grandTotal()),
                payment, status, currency);
    }
}
