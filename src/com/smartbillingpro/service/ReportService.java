package com.smartbillingpro.service;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.StockStatus;
import com.smartbillingpro.service.ReportTable.Type;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * All business reports, calculated directly from the local database. Profit
 * uses the cost recorded on each sale line at the moment of sale, net of
 * discounts and returns; tax collected is excluded from revenue.
 */
public final class ReportService {

    /** A report the user can pick from the Reports screen. */
    public record Definition(String id, String group, String title, String description, boolean dated) {
    }

    public enum Grouping {
        DAY, WEEK, MONTH
    }

    public static final List<Definition> CATALOG = List.of(
        new Definition("sales.daily", "Sales", "Daily sales", "Receipts, discounts, returns and net sales per day", true),
        new Definition("sales.weekly", "Sales", "Weekly sales", "Sales totals per week (Monday to Sunday)", true),
        new Definition("sales.monthly", "Sales", "Monthly sales", "Sales totals per month", true),
        new Definition("sales.register", "Sales", "Sales register", "Every receipt in the period", true),
        new Definition("sales.products", "Sales", "Product-wise sales", "Quantity and value sold per product", true),
        new Definition("sales.categories", "Sales", "Category-wise sales", "Sales value per category", true),
        new Definition("sales.methods", "Sales", "Payment method sales", "Sales split by payment method", true),
        new Definition("sales.returns", "Sales", "Returns & refunds", "Every return in the period", true),
        new Definition("profit.summary", "Profit", "Profit & loss summary", "Revenue, cost, expenses and net profit", true),
        new Definition("profit.daily", "Profit", "Daily profit", "Gross and net profit per day", true),
        new Definition("profit.monthly", "Profit", "Monthly profit", "Gross and net profit per month", true),
        new Definition("profit.products", "Profit", "Product-wise profit", "Revenue, cost and margin per product", true),
        new Definition("stock.current", "Inventory", "Current stock", "Every product with stock and value", false),
        new Definition("stock.low", "Inventory", "Low stock", "Products at or below their minimum stock", false),
        new Definition("stock.out", "Inventory", "Out of stock", "Products with no stock left", false),
        new Definition("stock.valuation", "Inventory", "Stock valuation", "Stock value at cost and retail per category", false),
        new Definition("stock.movements", "Inventory", "Stock movements", "Every stock change in the period", true),
        new Definition("stock.purchases", "Inventory", "Product purchase history", "Every product purchased in the period", true),
        new Definition("fin.receivables", "Financial", "Customer balances", "Outstanding amounts customers owe", false),
        new Definition("fin.payables", "Financial", "Supplier balances", "Outstanding amounts owed to suppliers", false),
        new Definition("fin.received", "Financial", "Cash received", "All money received in the period", true),
        new Definition("fin.credit", "Financial", "Credit sales", "Sales not fully paid at the till", true),
        new Definition("fin.paid", "Financial", "Payments made", "All money paid out in the period", true),
        new Definition("fin.methods", "Financial", "Cash book by method", "Money in and out per payment method", true),
        new Definition("fin.expenses", "Financial", "Expense summary", "Expenses grouped by category", true)
    );

    private final Db db;
    private final SettingsService settings;

    public ReportService(Db db, SettingsService settings) {
        this.db = db;
        this.settings = settings;
    }

    public static Definition definition(String id) {
        return CATALOG.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    public ReportTable run(String id, LocalDate from, LocalDate to) {
        ReportTable t = switch (id) {
            case "sales.daily" -> salesByPeriod(from, to, Grouping.DAY);
            case "sales.weekly" -> salesByPeriod(from, to, Grouping.WEEK);
            case "sales.monthly" -> salesByPeriod(from, to, Grouping.MONTH);
            case "sales.register" -> salesRegister(from, to);
            case "sales.products" -> productSales(from, to);
            case "sales.categories" -> categorySales(from, to);
            case "sales.methods" -> methodSales(from, to);
            case "sales.returns" -> returns(from, to);
            case "profit.summary" -> profitSummary(from, to);
            case "profit.daily" -> profitByPeriod(from, to, Grouping.DAY);
            case "profit.monthly" -> profitByPeriod(from, to, Grouping.MONTH);
            case "profit.products" -> productProfit(from, to);
            case "stock.current" -> stock(null);
            case "stock.low" -> stock(StockStatus.LOW);
            case "stock.out" -> stock(StockStatus.OUT);
            case "stock.valuation" -> valuation();
            case "stock.movements" -> movements(from, to);
            case "stock.purchases" -> purchaseHistory(from, to);
            case "fin.receivables" -> balances(true);
            case "fin.payables" -> balances(false);
            case "fin.received" -> payments(from, to, "IN");
            case "fin.credit" -> creditSales(from, to);
            case "fin.paid" -> payments(from, to, "OUT");
            case "fin.methods" -> cashBook(from, to);
            case "fin.expenses" -> expenses(from, to);
            default -> throw new IllegalArgumentException("Unknown report " + id);
        };
        Definition d = definition(id);
        String store = settings.get().storeName();
        t.setSubtitle(store + "  •  " + (d.dated() ? Dates.formatDate(from) + " to " + Dates.formatDate(to)
                : "As of " + Dates.formatDateTime(Dates.now())));
        return t;
    }

    // ------------------------------------------------------------ helpers

    private static String f(LocalDate d) {
        return Dates.startOf(d);
    }

    private static String t(LocalDate d) {
        return Dates.endExclusive(d);
    }

    public static Function<String, String> bucket(Grouping g) {
        return day -> {
            LocalDate d = LocalDate.parse(day, Dates.ISO_DATE);
            return switch (g) {
                case DAY -> day;
                case WEEK -> Dates.weekStart(d).format(Dates.ISO_DATE);
                case MONTH -> Dates.monthStart(d).format(Dates.ISO_DATE);
            };
        };
    }

    public static String bucketLabel(String key, Grouping g) {
        LocalDate d = LocalDate.parse(key, Dates.ISO_DATE);
        return switch (g) {
            case DAY -> Dates.formatDate(d);
            case WEEK -> "Week of " + Dates.formatDate(d);
            case MONTH -> d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        };
    }

    /** Adds per-day values into buckets: map day -> long[] at index offset. */
    private void sumByDay(Map<String, long[]> into, int width, Function<String, String> bucket, String sql,
                          int offset, Object... params) {
        db.list(sql, rs -> {
            String key = bucket.apply(rs.getString(1));
            long[] row = into.computeIfAbsent(key, k -> new long[width]);
            int cols = rs.getMetaData().getColumnCount();
            for (int c = 2; c <= cols; c++) {
                row[offset + c - 2] += rs.getLong(c);
            }
            return null;
        }, params);
    }

    private static BigDecimal pct(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(whole), 2,
                RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------ sales

    private ReportTable salesByPeriod(LocalDate from, LocalDate to, Grouping g) {
        String title = switch (g) {
            case DAY -> "Daily sales";
            case WEEK -> "Weekly sales";
            case MONTH -> "Monthly sales";
        };
        ReportTable r = new ReportTable(title)
                .col(g == Grouping.DAY ? "Date" : "Period", Type.TEXT).col("Receipts", Type.INT)
                .col("Gross sales", Type.MONEY).col("Discounts", Type.MONEY).col("Tax", Type.MONEY)
                .col("Returns", Type.MONEY).col("Net sales", Type.MONEY);
        Map<String, long[]> m = new TreeMap<>();
        Function<String, String> b = bucket(g);
        sumByDay(m, 6, b, "SELECT substr(created_at, 1, 10), COUNT(*), SUM(subtotal), SUM(item_discount + cart_discount), "
                + "SUM(tax_amount), SUM(grand_total) FROM sales WHERE created_at >= ? AND created_at < ? "
                + "GROUP BY substr(created_at, 1, 10)", 0, f(from), t(to));
        sumByDay(m, 6, b, "SELECT substr(created_at, 1, 10), SUM(total_amount) FROM sale_returns "
                + "WHERE created_at >= ? AND created_at < ? GROUP BY substr(created_at, 1, 10)", 5, f(from), t(to));
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            long[] v = e.getValue();
            r.row(bucketLabel(e.getKey(), g), v[0], v[1], v[2], v[3], v[5], v[4] - v[5]);
        }
        r.setTotals("Total", r.sum(1), r.sum(2), r.sum(3), r.sum(4), r.sum(5), r.sum(6));
        return r;
    }

    private ReportTable salesRegister(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Sales register")
                .col("Receipt", Type.TEXT, 2).col("Date", Type.DATETIME, 3).col("Customer", Type.TEXT)
                .col("Method", Type.TEXT, 2).col("Total", Type.MONEY).col("Paid", Type.MONEY).col("Due", Type.MONEY)
                .col("Returned", Type.MONEY).col("Status", Type.TEXT, 2);
        db.list("SELECT receipt_no, created_at, customer_name, payment_method_name, grand_total, amount_paid, balance_due, "
                + "returned_total, status FROM sales WHERE created_at >= ? AND created_at < ? ORDER BY id", rs -> {
                    r.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5),
                            rs.getLong(6), rs.getLong(7), rs.getLong(8),
                            com.smartbillingpro.model.SaleStatus.valueOf(rs.getString(9)).label());
                    return null;
                }, f(from), t(to));
        r.setTotals("Total", r.rows().size() + " receipts", null, null, r.sum(4), r.sum(5), r.sum(6), r.sum(7), null);
        return r;
    }

    private ReportTable productSales(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Product-wise sales")
                .col("Product", Type.TEXT, 4).col("Category", Type.TEXT, 2).col("Unit", Type.TEXT, 1)
                .col("Qty sold", Type.QTY).col("Qty returned", Type.QTY).col("Net qty", Type.QTY)
                .col("Net sales", Type.MONEY);
        db.list("SELECT CASE WHEN COALESCE(si.variant_name, '') = '' THEN si.product_name "
                + "ELSE si.product_name || ' (' || si.variant_name || ')' END AS name, "
                + "COALESCE(si.category_name, ''), si.unit_code, SUM(si.quantity), "
                + "COALESCE(SUM((SELECT SUM(ri.quantity) FROM sale_return_items ri WHERE ri.sale_item_id = si.id)), 0), "
                + "SUM(si.line_total) - COALESCE(SUM((SELECT SUM(ri.total_amount) FROM sale_return_items ri "
                + "WHERE ri.sale_item_id = si.id)), 0) AS net "
                + "FROM sale_items si JOIN sales s ON s.id = si.sale_id WHERE s.created_at >= ? AND s.created_at < ? "
                + "GROUP BY COALESCE(si.variant_id, si.product_name), name ORDER BY net DESC", rs -> {
                    long sold = rs.getLong(4);
                    long ret = rs.getLong(5);
                    r.row(rs.getString(1), rs.getString(2), rs.getString(3), sold, ret, sold - ret, rs.getLong(6));
                    return null;
                }, f(from), t(to));
        r.setTotals("Total", null, null, null, null, null, r.sum(6));
        return r;
    }

    private ReportTable categorySales(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Category-wise sales")
                .col("Category", Type.TEXT, 4).col("Lines sold", Type.INT).col("Net sales", Type.MONEY)
                .col("Share", Type.PERCENT);
        List<Object[]> rows = db.list("SELECT COALESCE(NULLIF(si.category_name, ''), 'Uncategorised') AS c, COUNT(*), "
                + "SUM(si.line_total) - COALESCE(SUM((SELECT SUM(ri.total_amount) FROM sale_return_items ri "
                + "WHERE ri.sale_item_id = si.id)), 0) AS net FROM sale_items si JOIN sales s ON s.id = si.sale_id "
                + "WHERE s.created_at >= ? AND s.created_at < ? GROUP BY c ORDER BY net DESC",
                rs -> new Object[] {rs.getString(1), rs.getLong(2), rs.getLong(3)}, f(from), t(to));
        long total = rows.stream().mapToLong(o -> (Long) o[2]).sum();
        for (Object[] o : rows) {
            r.row(o[0], o[1], o[2], pct((Long) o[2], total));
        }
        r.setTotals("Total", r.sum(1), total, total == 0 ? null : BigDecimal.valueOf(100));
        return r;
    }

    private ReportTable methodSales(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Sales by payment method")
                .col("Payment method", Type.TEXT, 4).col("Receipts", Type.INT).col("Sales total", Type.MONEY)
                .col("Paid at till", Type.MONEY).col("On credit", Type.MONEY);
        db.list("SELECT COALESCE(payment_method_name, '-'), COUNT(*), SUM(grand_total), SUM(amount_paid), SUM(balance_due) "
                + "FROM sales WHERE created_at >= ? AND created_at < ? GROUP BY payment_method_name ORDER BY 3 DESC", rs -> {
                    r.row(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5));
                    return null;
                }, f(from), t(to));
        r.setTotals("Total", r.sum(1), r.sum(2), r.sum(3), r.sum(4));
        return r;
    }

    private ReportTable returns(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Returns & refunds")
                .col("Return", Type.TEXT, 2).col("Date", Type.DATETIME, 3).col("Receipt", Type.TEXT, 2)
                .col("Customer", Type.TEXT).col("Reason", Type.TEXT).col("Value", Type.MONEY)
                .col("Cash refunded", Type.MONEY).col("Credited to account", Type.MONEY);
        db.list("SELECT r.return_no, r.created_at, s.receipt_no, s.customer_name, r.reason, r.total_amount, r.cash_refunded, "
                + "r.credit_adjusted FROM sale_returns r JOIN sales s ON s.id = r.sale_id "
                + "WHERE r.created_at >= ? AND r.created_at < ? ORDER BY r.id", rs -> {
                    r.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getLong(6), rs.getLong(7), rs.getLong(8));
                    return null;
                }, f(from), t(to));
        r.setTotals("Total", null, null, null, null, r.sum(5), r.sum(6), r.sum(7));
        return r;
    }

    // ------------------------------------------------------------ profit

    /** Revenue, cost, returns and expenses for a period. All minor units. */
    public record ProfitFigures(long grossSales, long discounts, long tax, long returnsValue, long revenue, long cost,
                                long grossProfit, long expenses, long netProfit, long receipts) {
    }

    public ProfitFigures profit(LocalDate from, LocalDate to) {
        long[] s = db.one("SELECT COUNT(*), COALESCE(SUM(subtotal), 0), COALESCE(SUM(item_discount + cart_discount), 0), "
                + "COALESCE(SUM(tax_amount), 0) FROM sales WHERE created_at >= ? AND created_at < ?",
                rs -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, f(from), t(to));
        long[] li = db.one("SELECT COALESCE(SUM(si.net_amount), 0), COALESCE(SUM(si.cost_amount), 0) FROM sale_items si "
                + "JOIN sales s ON s.id = si.sale_id WHERE s.created_at >= ? AND s.created_at < ?",
                rs -> new long[] {rs.getLong(1), rs.getLong(2)}, f(from), t(to));
        long[] rt = db.one("SELECT COALESCE(SUM(ri.net_amount), 0), COALESCE(SUM(ri.cost_amount), 0), "
                + "COALESCE(SUM(ri.total_amount), 0), COALESCE(SUM(ri.tax_amount), 0) FROM sale_return_items ri "
                + "JOIN sale_returns r ON r.id = ri.return_id WHERE r.created_at >= ? AND r.created_at < ?",
                rs -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, f(from), t(to));
        long expenses = expensesTotal(from, to);
        long revenue = li[0] - rt[0];
        long cost = li[1] - rt[1];
        long gross = revenue - cost;
        return new ProfitFigures(s[1], s[2], s[3] - rt[3], rt[2], revenue, cost, gross, expenses, gross - expenses, s[0]);
    }

    public long expensesTotal(LocalDate from, LocalDate to) {
        return db.scalar("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE status = 'ACTIVE' AND expense_date >= ? "
                + "AND expense_date <= ?", from.format(Dates.ISO_DATE), to.format(Dates.ISO_DATE));
    }

    private ReportTable profitSummary(LocalDate from, LocalDate to) {
        ProfitFigures p = profit(from, to);
        ReportTable r = new ReportTable("Profit & loss summary").col("Item", Type.TEXT, 5).col("Amount", Type.MONEY, 2);
        r.row("Gross sales (before discounts, excl. tax)", p.grossSales());
        r.row("Less: discounts given", -p.discounts());
        r.row("Less: returns (excl. tax)", -(p.grossSales() - p.discounts() - p.revenue()));
        r.row("Net revenue", p.revenue());
        r.row("Less: cost of goods sold", -p.cost());
        r.row("Gross profit", p.grossProfit());
        db.list("SELECT category, SUM(amount) FROM expenses WHERE status = 'ACTIVE' AND expense_date >= ? "
                + "AND expense_date <= ? GROUP BY category ORDER BY 2 DESC", rs -> {
                    r.row("Less: expense - " + rs.getString(1), -rs.getLong(2));
                    return null;
                }, from.format(Dates.ISO_DATE), to.format(Dates.ISO_DATE));
        r.setTotals(p.netProfit() >= 0 ? "Net profit" : "Net loss", p.netProfit());
        r.summary("Receipts", String.valueOf(p.receipts()));
        r.summary("Tax collected (not revenue)", Money.format(p.tax()));
        r.summary("Gross margin", pct(p.grossProfit(), p.revenue()).setScale(1, RoundingMode.HALF_UP) + "%");
        return r;
    }

    private ReportTable profitByPeriod(LocalDate from, LocalDate to, Grouping g) {
        ReportTable r = new ReportTable(g == Grouping.DAY ? "Daily profit" : "Monthly profit")
                .col(g == Grouping.DAY ? "Date" : "Month", Type.TEXT).col("Revenue", Type.MONEY).col("Cost", Type.MONEY)
                .col("Gross profit", Type.MONEY).col("Margin", Type.PERCENT).col("Expenses", Type.MONEY)
                .col("Net profit", Type.MONEY);
        Map<String, long[]> m = profitSeries(from, to, g);
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            long[] v = e.getValue();
            long gp = v[0] - v[1];
            r.row(bucketLabel(e.getKey(), g), v[0], v[1], gp, pct(gp, v[0]), v[2], gp - v[2]);
        }
        long rev = r.sum(1);
        long gp = r.sum(3);
        r.setTotals("Total", rev, r.sum(2), gp, pct(gp, rev), r.sum(5), r.sum(6));
        return r;
    }

    /** bucket -> {revenue, cost, expenses, netSales(incl. tax)} with returns subtracted on their own date. */
    public Map<String, long[]> profitSeries(LocalDate from, LocalDate to, Grouping g) {
        Map<String, long[]> m = new TreeMap<>();
        Function<String, String> b = bucket(g);
        sumByDay(m, 6, b, "SELECT substr(s.created_at, 1, 10), SUM(si.net_amount), SUM(si.cost_amount), 0, "
                + "SUM(si.line_total) FROM sale_items si JOIN sales s ON s.id = si.sale_id "
                + "WHERE s.created_at >= ? AND s.created_at < ? GROUP BY substr(s.created_at, 1, 10)", 0, f(from), t(to));
        Map<String, long[]> ret = new TreeMap<>();
        sumByDay(ret, 3, b, "SELECT substr(r.created_at, 1, 10), SUM(ri.net_amount), SUM(ri.cost_amount), "
                + "SUM(ri.total_amount) FROM sale_return_items ri JOIN sale_returns r ON r.id = ri.return_id "
                + "WHERE r.created_at >= ? AND r.created_at < ? GROUP BY substr(r.created_at, 1, 10)", 0, f(from), t(to));
        for (Map.Entry<String, long[]> e : ret.entrySet()) {
            long[] v = m.computeIfAbsent(e.getKey(), k -> new long[6]);
            v[0] -= e.getValue()[0];
            v[1] -= e.getValue()[1];
            v[3] -= e.getValue()[2];
        }
        db.list("SELECT expense_date, SUM(amount) FROM expenses WHERE status = 'ACTIVE' AND expense_date >= ? "
                + "AND expense_date <= ? GROUP BY expense_date", rs -> {
                    m.computeIfAbsent(b.apply(rs.getString(1)), k -> new long[6])[2] += rs.getLong(2);
                    return null;
                }, from.format(Dates.ISO_DATE), to.format(Dates.ISO_DATE));
        Map<String, long[]> out = new TreeMap<>();
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            long[] v = e.getValue();
            out.put(e.getKey(), new long[] {v[0], v[1], v[2], v[3]});
        }
        return out;
    }

    private ReportTable productProfit(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Product-wise profit")
                .col("Product", Type.TEXT, 4).col("Unit", Type.TEXT, 1).col("Net qty", Type.QTY)
                .col("Revenue", Type.MONEY).col("Cost", Type.MONEY).col("Profit", Type.MONEY).col("Margin", Type.PERCENT);
        db.list("SELECT CASE WHEN COALESCE(si.variant_name, '') = '' THEN si.product_name "
                + "ELSE si.product_name || ' (' || si.variant_name || ')' END AS name, si.unit_code, "
                + "SUM(si.quantity) - COALESCE(SUM((SELECT SUM(ri.quantity) FROM sale_return_items ri WHERE ri.sale_item_id = si.id)), 0), "
                + "SUM(si.net_amount) - COALESCE(SUM((SELECT SUM(ri.net_amount) FROM sale_return_items ri WHERE ri.sale_item_id = si.id)), 0), "
                + "SUM(si.cost_amount) - COALESCE(SUM((SELECT SUM(ri.cost_amount) FROM sale_return_items ri WHERE ri.sale_item_id = si.id)), 0) "
                + "FROM sale_items si JOIN sales s ON s.id = si.sale_id WHERE s.created_at >= ? AND s.created_at < ? "
                + "GROUP BY COALESCE(si.variant_id, si.product_name), name", rs -> {
                    long rev = rs.getLong(4);
                    long cost = rs.getLong(5);
                    r.row(rs.getString(1), rs.getString(2), rs.getLong(3), rev, cost, rev - cost, pct(rev - cost, rev));
                    return null;
                }, f(from), t(to));
        r.rows().sort((a, b) -> Long.compare((Long) b[5], (Long) a[5]));
        long rev = r.sum(3);
        long profit = r.sum(5);
        r.setTotals("Total", null, null, rev, r.sum(4), profit, pct(profit, rev));
        return r;
    }

    // ------------------------------------------------------------ inventory

    private ReportTable stock(StockStatus only) {
        String title = only == null ? "Current stock" : only == StockStatus.LOW ? "Low stock" : "Out of stock";
        ReportTable r = new ReportTable(title)
                .col("Product", Type.TEXT, 4).col("Category", Type.TEXT, 2).col("Barcode", Type.TEXT, 2)
                .col("Stock", Type.QTY).col("Unit", Type.TEXT, 1).col("Min.", Type.QTY, 1).col("Cost", Type.MONEY)
                .col("Price", Type.MONEY).col("Stock value", Type.MONEY).col("Status", Type.TEXT, 2);
        db.list("SELECT CASE WHEN v.name = '' THEN p.name ELSE p.name || ' (' || v.name || ')' END, "
                + "COALESCE(c.name, ''), COALESCE(v.barcode, ''), v.stock_qty, u.code, v.min_stock, v.cost_price, v.sale_price "
                + "FROM product_variants v JOIN products p ON p.id = v.product_id JOIN units u ON u.id = p.unit_id "
                + "LEFT JOIN categories c ON c.id = p.category_id WHERE p.active = 1 AND v.active = 1 "
                + "ORDER BY p.name COLLATE NOCASE, v.sort_order", rs -> {
                    long stock = rs.getLong(4);
                    long min = rs.getLong(6);
                    StockStatus st = StockStatus.of(stock, min);
                    if (only == null || st == only) {
                        long cost = rs.getLong(7);
                        r.row(rs.getString(1), rs.getString(2), rs.getString(3), stock, rs.getString(5), min, cost,
                                rs.getLong(8), Money.mulQty(cost, stock), st.label());
                    }
                    return null;
                });
        r.setTotals("Total", r.rows().size() + " items", null, null, null, null, null, null, r.sum(8), null);
        return r;
    }

    private ReportTable valuation() {
        ReportTable r = new ReportTable("Stock valuation")
                .col("Category", Type.TEXT, 4).col("Items", Type.INT).col("Cost value", Type.MONEY)
                .col("Retail value", Type.MONEY).col("Potential profit", Type.MONEY);
        Map<String, long[]> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        db.list("SELECT COALESCE(c.name, 'Uncategorised'), v.stock_qty, v.cost_price, v.sale_price "
                + "FROM product_variants v JOIN products p ON p.id = v.product_id LEFT JOIN categories c ON c.id = p.category_id "
                + "WHERE p.active = 1 AND v.active = 1", rs -> {
                    long[] v = m.computeIfAbsent(rs.getString(1), k -> new long[3]);
                    long stock = rs.getLong(2);
                    v[0]++;
                    v[1] += Money.mulQty(rs.getLong(3), stock);
                    v[2] += Money.mulQty(rs.getLong(4), stock);
                    return null;
                });
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            long[] v = e.getValue();
            r.row(e.getKey(), v[0], v[1], v[2], v[2] - v[1]);
        }
        r.setTotals("Total", r.sum(1), r.sum(2), r.sum(3), r.sum(4));
        return r;
    }

    private ReportTable movements(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Stock movements")
                .col("Date", Type.DATETIME, 3).col("Product", Type.TEXT, 4).col("Type", Type.TEXT, 2)
                .col("Change", Type.QTY).col("Balance", Type.QTY).col("Unit", Type.TEXT, 1).col("Reference", Type.TEXT, 2)
                .col("Reason", Type.TEXT, 3);
        db.list("SELECT m.created_at, CASE WHEN v.name = '' THEN p.name ELSE p.name || ' (' || v.name || ')' END, m.type, "
                + "m.qty_change, m.balance_after, u.code, COALESCE(m.reference, ''), COALESCE(m.reason, '') "
                + "FROM stock_movements m JOIN product_variants v ON v.id = m.variant_id JOIN products p ON p.id = v.product_id "
                + "JOIN units u ON u.id = p.unit_id WHERE m.created_at >= ? AND m.created_at < ? ORDER BY m.id", rs -> {
                    com.smartbillingpro.model.StockMovement sm = new com.smartbillingpro.model.StockMovement(0, 0, "", "",
                            "", "", rs.getString(3), 0, 0, null, null, null);
                    r.row(rs.getString(1), rs.getString(2), sm.typeLabel(), rs.getLong(4), rs.getLong(5),
                            rs.getString(6), rs.getString(7), rs.getString(8));
                    return null;
                }, f(from), t(to));
        return r;
    }

    private ReportTable purchaseHistory(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Product purchase history")
                .col("Date", Type.DATETIME, 3).col("Purchase", Type.TEXT, 2).col("Supplier", Type.TEXT, 3)
                .col("Product", Type.TEXT, 4).col("Qty", Type.QTY).col("Unit", Type.TEXT, 1)
                .col("Unit cost", Type.MONEY).col("Total", Type.MONEY).col("Status", Type.TEXT, 2);
        db.list("SELECT p.created_at, p.purchase_no, COALESCE(p.supplier_name, 'No supplier'), "
                + "CASE WHEN COALESCE(i.variant_name, '') = '' THEN i.product_name ELSE i.product_name || ' (' || i.variant_name || ')' END, "
                + "i.quantity, i.unit_code, i.unit_cost, i.line_total, p.status FROM purchase_items i "
                + "JOIN purchases p ON p.id = i.purchase_id WHERE p.created_at >= ? AND p.created_at < ? ORDER BY p.id, i.id", rs -> {
                    r.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5),
                            rs.getString(6), rs.getLong(7), rs.getLong(8),
                            "REVERSED".equals(rs.getString(9)) ? "Reversed" : "Received");
                    return null;
                }, f(from), t(to));
        long total = r.rows().stream().filter(o -> "Received".equals(o[8])).mapToLong(o -> (Long) o[7]).sum();
        r.setTotals("Total received", null, null, null, null, null, null, total, null);
        return r;
    }

    // ------------------------------------------------------------ financial

    private ReportTable balances(boolean customers) {
        ReportTable r = new ReportTable(customers ? "Customer balances" : "Supplier balances")
                .col(customers ? "Customer" : "Supplier", Type.TEXT, 4).col("Phone", Type.TEXT, 2)
                .col(customers ? "Total charged" : "Total billed", Type.MONEY).col("Total paid", Type.MONEY)
                .col("Balance", Type.MONEY).col("Status", Type.TEXT, 2);
        String type = customers ? "CUSTOMER" : "SUPPLIER";
        String table = customers ? "customers" : "suppliers";
        String charged = customers ? "debit" : "credit";
        String paid = customers ? "credit" : "debit";
        db.list("SELECT x.name, COALESCE(x.phone, ''), COALESCE(SUM(l." + charged + "), 0), COALESCE(SUM(l." + paid + "), 0) "
                + "FROM " + table + " x LEFT JOIN ledger_entries l ON l.party_type = ? AND l.party_id = x.id "
                + "GROUP BY x.id HAVING SUM(l." + charged + ") - SUM(l." + paid + ") <> 0 "
                + "ORDER BY SUM(l." + charged + ") - SUM(l." + paid + ") DESC", rs -> {
                    long c = rs.getLong(3);
                    long p = rs.getLong(4);
                    long bal = c - p;
                    String status = bal > 0 ? (customers ? "Owes us" : "We owe") : (customers ? "Advance" : "Advance paid");
                    r.row(rs.getString(1), rs.getString(2), c, p, bal, status);
                    return null;
                }, type);
        long outstanding = r.rows().stream().mapToLong(o -> Math.max(0, (Long) o[4])).sum();
        r.setTotals("Total", null, r.sum(2), r.sum(3), r.sum(4), null);
        r.summary(customers ? "Total receivable" : "Total payable", Money.format(outstanding));
        return r;
    }

    private ReportTable payments(LocalDate from, LocalDate to, String direction) {
        boolean in = "IN".equals(direction);
        ReportTable r = new ReportTable(in ? "Cash received" : "Payments made")
                .col("Date", Type.DATETIME, 3).col("Payment", Type.TEXT, 2).col(in ? "From" : "To", Type.TEXT, 3)
                .col("Method", Type.TEXT, 2).col("Details", Type.TEXT, 4).col("Amount", Type.MONEY);
        db.list("SELECT p.created_at, p.payment_no, CASE p.party_type WHEN 'CUSTOMER' THEN (SELECT name FROM customers WHERE id = p.party_id) "
                + "WHEN 'SUPPLIER' THEN (SELECT name FROM suppliers WHERE id = p.party_id) WHEN 'WALK_IN' THEN 'Walk-in customer' "
                + "WHEN 'EXPENSE' THEN 'Expense' ELSE 'Other' END, COALESCE(p.payment_method_name, ''), COALESCE(p.note, ''), p.amount "
                + "FROM payments p WHERE p.direction = ? AND p.created_at >= ? AND p.created_at < ? ORDER BY p.id", rs -> {
                    r.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6));
                    return null;
                }, direction, f(from), t(to));
        r.setTotals("Total", r.rows().size() + " payments", null, null, null, r.sum(5));
        return r;
    }

    private ReportTable creditSales(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Credit sales")
                .col("Receipt", Type.TEXT, 2).col("Date", Type.DATETIME, 3).col("Customer", Type.TEXT, 3)
                .col("Total", Type.MONEY).col("Paid at till", Type.MONEY).col("On credit", Type.MONEY)
                .col("Customer balance now", Type.MONEY);
        db.list("SELECT s.receipt_no, s.created_at, s.customer_name, s.grand_total, s.amount_paid, s.balance_due, "
                + "COALESCE((SELECT SUM(debit - credit) FROM ledger_entries l WHERE l.party_type = 'CUSTOMER' AND l.party_id = s.customer_id), 0) "
                + "FROM sales s WHERE s.balance_due > 0 AND s.created_at >= ? AND s.created_at < ? ORDER BY s.id", rs -> {
                    r.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getLong(6),
                            rs.getLong(7));
                    return null;
                }, f(from), t(to));
        r.setTotals("Total", null, null, r.sum(3), r.sum(4), r.sum(5), null);
        return r;
    }

    private ReportTable cashBook(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Cash book by payment method")
                .col("Payment method", Type.TEXT, 4).col("Money in", Type.MONEY).col("Money out", Type.MONEY)
                .col("Net", Type.MONEY);
        db.list("SELECT COALESCE(payment_method_name, '-'), SUM(CASE WHEN direction = 'IN' THEN amount ELSE 0 END), "
                + "SUM(CASE WHEN direction = 'OUT' THEN amount ELSE 0 END) FROM payments WHERE created_at >= ? AND created_at < ? "
                + "GROUP BY payment_method_name ORDER BY 1", rs -> {
                    r.row(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(2) - rs.getLong(3));
                    return null;
                }, f(from), t(to));
        r.setTotals("Total", r.sum(1), r.sum(2), r.sum(3));
        return r;
    }

    private ReportTable expenses(LocalDate from, LocalDate to) {
        ReportTable r = new ReportTable("Expense summary")
                .col("Category", Type.TEXT, 4).col("Entries", Type.INT).col("Amount", Type.MONEY).col("Share", Type.PERCENT);
        List<Object[]> rows = new ArrayList<>(db.list("SELECT category, COUNT(*), SUM(amount) FROM expenses "
                + "WHERE status = 'ACTIVE' AND expense_date >= ? AND expense_date <= ? GROUP BY category ORDER BY 3 DESC",
                rs -> new Object[] {rs.getString(1), rs.getLong(2), rs.getLong(3)},
                from.format(Dates.ISO_DATE), to.format(Dates.ISO_DATE)));
        long total = rows.stream().mapToLong(o -> (Long) o[2]).sum();
        for (Object[] o : rows) {
            r.row(o[0], o[1], o[2], pct((Long) o[2], total));
        }
        r.setTotals("Total", r.sum(1), total, total == 0 ? null : BigDecimal.valueOf(100));
        return r;
    }
}
