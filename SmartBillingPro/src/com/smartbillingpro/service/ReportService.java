package com.smartbillingpro.service;

import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceItem;
import com.smartbillingpro.model.InvoiceStatus;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Settings;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Builds plain-text receipts and reports that can be printed or saved. */
public class ReportService {

    private final InvoiceService invoiceService;
    private final InventoryService inventoryService;

    public ReportService(InvoiceService invoiceService, InventoryService inventoryService) {
        this.invoiceService = invoiceService;
        this.inventoryService = inventoryService;
    }

    // ----- formatting helpers -------------------------------------------------

    private String rule(char c, int width) {
        return String.valueOf(c).repeat(width);
    }

    private String center(String text, int width) {
        if (text == null) {
            text = "";
        }
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int left = (width - text.length()) / 2;
        return " ".repeat(left) + text + " ".repeat(width - text.length() - left);
    }

    private String padRight(String text, int width) {
        if (text == null) {
            text = "";
        }
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }

    private String padLeft(String text, int width) {
        if (text == null) {
            text = "";
        }
        if (text.length() >= width) {
            return text.substring(text.length() - width);
        }
        return " ".repeat(width - text.length()) + text;
    }

    private String labelValue(String label, String value, int width) {
        int space = width - label.length();
        return label + padLeft(value, Math.max(0, space));
    }

    // ----- receipt ------------------------------------------------------------

    public String receipt(Invoice invoice, Settings settings) {
        final int w = 32;
        String symbol = settings.getCurrencySymbol();
        StringBuilder sb = new StringBuilder();
        sb.append(center(settings.getBusinessName(), w)).append('\n');
        if (notBlank(settings.getAddress())) {
            sb.append(center(settings.getAddress(), w)).append('\n');
        }
        if (notBlank(settings.getPhone())) {
            sb.append(center("Tel: " + settings.getPhone(), w)).append('\n');
        }
        sb.append(rule('=', w)).append('\n');
        sb.append(padRight("Invoice: " + invoice.getInvoiceNumber(), w)).append('\n');
        sb.append(padRight("Date: " + invoice.getCreatedAt(), w)).append('\n');
        sb.append(padRight("Cashier: " + safe(invoice.getCashier()), w)).append('\n');
        if (notBlank(invoice.getCustomerName())) {
            sb.append(padRight("Customer: " + invoice.getCustomerName(), w)).append('\n');
        }
        if (notBlank(invoice.getCustomerPhone())) {
            sb.append(padRight("Phone: " + invoice.getCustomerPhone(), w)).append('\n');
        }
        if (notBlank(invoice.getCustomerAddress())) {
            sb.append(padRight("Address: " + invoice.getCustomerAddress(), w)).append('\n');
        }
        if (invoice.getStatus() != InvoiceStatus.COMPLETED) {
            sb.append(center("*** " + invoice.getStatus().getLabel().toUpperCase() + " ***", w)).append('\n');
        }
        sb.append(rule('-', w)).append('\n');
        for (InvoiceItem item : invoice.getItems()) {
            sb.append(padRight(item.getName(), w)).append('\n');
            String left = "  " + item.getQuantity() + " x " + Money.format(symbol, item.getUnitPrice());
            String right = Money.format(symbol, item.getLineTotal());
            sb.append(padRight(left, w - right.length())).append(right).append('\n');
        }
        sb.append(rule('-', w)).append('\n');
        sb.append(labelValue("Subtotal", Money.format(symbol, invoice.getSubtotal()), w)).append('\n');
        if (invoice.getDiscount().signum() > 0) {
            sb.append(labelValue("Discount", "-" + Money.format(symbol, invoice.getDiscount()), w)).append('\n');
        }
        if (invoice.getTaxAmount().signum() > 0) {
            sb.append(labelValue("Tax (" + Money.scale(invoice.getTaxPercent()).toPlainString() + "%)",
                    Money.format(symbol, invoice.getTaxAmount()), w)).append('\n');
        }
        sb.append(rule('-', w)).append('\n');
        sb.append(labelValue("TOTAL", Money.format(symbol, invoice.getGrandTotal()), w)).append('\n');
        sb.append(labelValue("Cash", Money.format(symbol, invoice.getAmountPaid()), w)).append('\n');
        sb.append(labelValue("Change", Money.format(symbol, invoice.getChangeDue()), w)).append('\n');
        if (invoice.getLoyaltyEarned() > 0) {
            sb.append(rule('-', w)).append('\n');
            sb.append(labelValue("Points earned", String.valueOf(invoice.getLoyaltyEarned()), w)).append('\n');
        }
        sb.append(rule('=', w)).append('\n');
        if (notBlank(settings.getFooterMessage())) {
            sb.append(center(settings.getFooterMessage(), w)).append('\n');
        }
        sb.append(center("Returns within " + settings.getReturnPolicyDays() + " days", w)).append('\n');
        return sb.toString();
    }

    // ----- inventory report ---------------------------------------------------

    public String inventoryReport(Settings settings) {
        final int w = 76;
        String symbol = settings.getCurrencySymbol();
        List<Product> products = inventoryService.all();
        StringBuilder sb = new StringBuilder();
        sb.append(center(settings.getBusinessName() + " - Inventory Report", w)).append('\n');
        sb.append(center("Generated " + Dates.now(), w)).append('\n');
        sb.append(rule('=', w)).append('\n');
        sb.append(padRight("SKU", 12)).append(padRight("Name", 24)).append(padLeft("Qty", 6))
                .append(padLeft("Cost", 12)).append(padLeft("Price", 12)).append(padLeft("", 10)).append('\n');
        sb.append(rule('-', w)).append('\n');
        for (Product p : products) {
            sb.append(padRight(safe(p.getSku()), 12))
                    .append(padRight(safe(p.getName()), 24))
                    .append(padLeft(String.valueOf(p.getQuantity()), 6))
                    .append(padLeft(Money.format(symbol, p.getCostPrice()), 12))
                    .append(padLeft(Money.format(symbol, p.getSalePrice()), 12))
                    .append(padLeft(InventoryService.stockStatus(p, settings.getLowStockThreshold()), 10))
                    .append('\n');
        }
        sb.append(rule('=', w)).append('\n');
        sb.append(labelValue("Products", String.valueOf(inventoryService.productCount()), w)).append('\n');
        sb.append(labelValue("Total units", String.valueOf(inventoryService.totalUnits()), w)).append('\n');
        sb.append(labelValue("Retail value", Money.format(symbol, inventoryService.retailValue()), w)).append('\n');
        sb.append(labelValue("Cost value", Money.format(symbol, inventoryService.costValue()), w)).append('\n');
        return sb.toString();
    }

    // ----- sales report -------------------------------------------------------

    public String salesReport(Settings settings, LocalDate from, LocalDate to) {
        final int w = 76;
        String symbol = settings.getCurrencySymbol();
        StringBuilder sb = new StringBuilder();
        sb.append(center(settings.getBusinessName() + " - Sales Report", w)).append('\n');
        sb.append(center(from + "  to  " + to, w)).append('\n');
        sb.append(rule('=', w)).append('\n');
        sb.append(padRight("Invoice", 14)).append(padRight("Date", 20)).append(padRight("Customer", 20))
                .append(padLeft("Total", 14)).append(padLeft("", 8)).append('\n');
        sb.append(rule('-', w)).append('\n');
        BigDecimal gross = BigDecimal.ZERO;
        int completed = 0;
        for (Invoice inv : invoiceService.all()) {
            if (!inRange(inv, from, to)) {
                continue;
            }
            String flag = inv.getStatus() == InvoiceStatus.COMPLETED ? "" : inv.getStatus().getLabel();
            sb.append(padRight(safe(inv.getInvoiceNumber()), 14))
                    .append(padRight(safe(inv.getCreatedAt()), 20))
                    .append(padRight(inv.getCustomerName() == null ? "Walk-in" : inv.getCustomerName(), 20))
                    .append(padLeft(Money.format(symbol, inv.getGrandTotal()), 14))
                    .append(padLeft(flag, 8))
                    .append('\n');
            if (inv.getStatus() == InvoiceStatus.COMPLETED) {
                gross = gross.add(inv.getGrandTotal());
                completed++;
            }
        }
        sb.append(rule('=', w)).append('\n');
        sb.append(labelValue("Completed sales", String.valueOf(completed), w)).append('\n');
        sb.append(labelValue("Gross revenue", Money.format(symbol, gross), w)).append('\n');
        return sb.toString();
    }

    // ----- profit report ------------------------------------------------------

    public String profitReport(Settings settings, LocalDate from, LocalDate to) {
        final int w = 76;
        String symbol = settings.getCurrencySymbol();
        StringBuilder sb = new StringBuilder();
        sb.append(center(settings.getBusinessName() + " - Profit Report", w)).append('\n');
        sb.append(center(from + "  to  " + to, w)).append('\n');
        sb.append(rule('=', w)).append('\n');
        sb.append(padRight("Invoice", 16)).append(padLeft("Revenue", 18)).append(padLeft("Cost", 18))
                .append(padLeft("Profit", 18)).append('\n');
        sb.append(rule('-', w)).append('\n');
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Invoice header : invoiceService.all()) {
            if (header.getStatus() != InvoiceStatus.COMPLETED || !inRange(header, from, to)) {
                continue;
            }
            Invoice inv = invoiceService.load(header.getId());
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal cost = BigDecimal.ZERO;
            for (InvoiceItem item : inv.getItems()) {
                revenue = revenue.add(item.getLineTotal());
                cost = cost.add(item.getCostPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            BigDecimal profit = revenue.subtract(cost);
            sb.append(padRight(safe(inv.getInvoiceNumber()), 16))
                    .append(padLeft(Money.format(symbol, revenue), 18))
                    .append(padLeft(Money.format(symbol, cost), 18))
                    .append(padLeft(Money.format(symbol, profit), 18))
                    .append('\n');
            totalRevenue = totalRevenue.add(revenue);
            totalCost = totalCost.add(cost);
        }
        sb.append(rule('=', w)).append('\n');
        sb.append(labelValue("Total revenue", Money.format(symbol, totalRevenue), w)).append('\n');
        sb.append(labelValue("Total cost", Money.format(symbol, totalCost), w)).append('\n');
        sb.append(labelValue("Net profit", Money.format(symbol, totalRevenue.subtract(totalCost)), w)).append('\n');
        return sb.toString();
    }

    private boolean inRange(Invoice inv, LocalDate from, LocalDate to) {
        LocalDate date = Dates.dateOf(inv.getCreatedAt());
        if (date == null) {
            return false;
        }
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
