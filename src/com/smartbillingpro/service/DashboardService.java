package com.smartbillingpro.service;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.StockStatus;
import com.smartbillingpro.repo.LedgerRepo;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real figures for the dashboard cards and charts - nothing is estimated or faked. */
public final class DashboardService {

    public record Point(String key, String label, long value, long value2) {
    }

    public record Overview(long todaySales, long todayReceipts, long todayProfit, long purchases, long expenses,
                           long inventoryCost, long inventoryRetail, long productCount, long lowStock, long outOfStock,
                           long customers, long receivable, long payable, long periodSales, long periodProfit) {
    }

    private final Db db;
    private final ReportService reports;
    private final LedgerRepo ledger;

    public DashboardService(Db db, ReportService reports, LedgerRepo ledger) {
        this.db = db;
        this.reports = reports;
        this.ledger = ledger;
    }

    public Overview overview(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        ReportService.ProfitFigures t = reports.profit(today, today);
        long todayNet = db.scalar("SELECT COALESCE(SUM(grand_total), 0) FROM sales WHERE created_at >= ? AND created_at < ?",
                Dates.startOf(today), Dates.endExclusive(today)) - t.returnsValue();
        ReportService.ProfitFigures p = reports.profit(from, to);
        long periodNet = db.scalar("SELECT COALESCE(SUM(grand_total), 0) FROM sales WHERE created_at >= ? AND created_at < ?",
                Dates.startOf(from), Dates.endExclusive(to)) - p.returnsValue();
        long purchases = db.scalar("SELECT COALESCE(SUM(total), 0) FROM purchases WHERE status = 'RECEIVED' "
                + "AND created_at >= ? AND created_at < ?", Dates.startOf(from), Dates.endExclusive(to));
        long[] inv = new long[4];
        Map<Long, StockStatus> worst = new java.util.HashMap<>();
        db.list("SELECT v.stock_qty, v.min_stock, v.cost_price, v.sale_price, p.id FROM product_variants v "
                + "JOIN products p ON p.id = v.product_id WHERE p.active = 1 AND v.active = 1", rs -> {
                    long stock = rs.getLong(1);
                    inv[0] += Money.mulQty(rs.getLong(3), Math.max(0, stock));
                    inv[1] += Money.mulQty(rs.getLong(4), Math.max(0, stock));
                    StockStatus st = StockStatus.of(stock, rs.getLong(2));
                    worst.merge(rs.getLong(5), st, (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
                    return null;
                });
        // counted per product (a product with one size out of stock counts as out), like the Inventory screen
        for (StockStatus st : worst.values()) {
            if (st == StockStatus.LOW) {
                inv[2]++;
            } else if (st == StockStatus.OUT) {
                inv[3]++;
            }
        }
        long products = db.scalar("SELECT COUNT(*) FROM products WHERE active = 1");
        long customers = db.scalar("SELECT COUNT(*) FROM customers WHERE active = 1");
        return new Overview(todayNet, t.receipts(), t.grossProfit(), purchases, p.expenses(), inv[0], inv[1], products,
                inv[2], inv[3], customers, ledger.totalOutstanding(PartyType.CUSTOMER),
                ledger.totalOutstanding(PartyType.SUPPLIER), periodNet, p.grossProfit());
    }

    /** Net sales (value) and gross profit (value2) per day / week / month; empty buckets included. */
    public List<Point> salesTrend(LocalDate from, LocalDate to, ReportService.Grouping g) {
        Map<String, long[]> data = reports.profitSeries(from, to, g);
        List<Point> out = new ArrayList<>();
        for (String key : buckets(from, to, g)) {
            long[] v = data.getOrDefault(key, new long[4]);
            out.add(new Point(key, shortLabel(key, g), v[3], v[0] - v[1]));
        }
        return out;
    }

    /** Gross profit (value) vs expenses (value2) per bucket. */
    public List<Point> profitVsExpenses(LocalDate from, LocalDate to, ReportService.Grouping g) {
        Map<String, long[]> data = reports.profitSeries(from, to, g);
        List<Point> out = new ArrayList<>();
        for (String key : buckets(from, to, g)) {
            long[] v = data.getOrDefault(key, new long[4]);
            out.add(new Point(key, shortLabel(key, g), v[0] - v[1], v[2]));
        }
        return out;
    }

    public List<Point> topProducts(LocalDate from, LocalDate to, int limit) {
        return db.list("SELECT CASE WHEN COALESCE(si.variant_name, '') = '' THEN si.product_name "
                + "ELSE si.product_name || ' (' || si.variant_name || ')' END AS name, "
                + "SUM(si.line_total) - COALESCE(SUM((SELECT SUM(ri.total_amount) FROM sale_return_items ri "
                + "WHERE ri.sale_item_id = si.id)), 0) AS net, SUM(si.quantity) "
                + "FROM sale_items si JOIN sales s ON s.id = si.sale_id WHERE s.created_at >= ? AND s.created_at < ? "
                + "GROUP BY COALESCE(si.variant_id, si.product_name), name HAVING net > 0 ORDER BY net DESC LIMIT ?",
                rs -> new Point(rs.getString(1), rs.getString(1), rs.getLong(2), rs.getLong(3)),
                Dates.startOf(from), Dates.endExclusive(to), limit);
    }

    public List<Point> salesByCategory(LocalDate from, LocalDate to, int limit) {
        List<Point> all = db.list("SELECT COALESCE(NULLIF(si.category_name, ''), 'Uncategorised') AS c, "
                + "SUM(si.line_total) - COALESCE(SUM((SELECT SUM(ri.total_amount) FROM sale_return_items ri "
                + "WHERE ri.sale_item_id = si.id)), 0) AS net FROM sale_items si JOIN sales s ON s.id = si.sale_id "
                + "WHERE s.created_at >= ? AND s.created_at < ? GROUP BY c HAVING net > 0 ORDER BY net DESC",
                rs -> new Point(rs.getString(1), rs.getString(1), rs.getLong(2), 0),
                Dates.startOf(from), Dates.endExclusive(to));
        if (all.size() <= limit) {
            return all;
        }
        List<Point> top = new ArrayList<>(all.subList(0, limit - 1));
        long rest = all.subList(limit - 1, all.size()).stream().mapToLong(Point::value).sum();
        top.add(new Point("Other", "Other categories", rest, 0));
        return top;
    }

    /** Stock value at cost per category (value) and number of items (value2). */
    public List<Point> stockByCategory(int limit) {
        Map<String, long[]> m = new LinkedHashMap<>();
        db.list("SELECT COALESCE(c.name, 'Uncategorised'), v.stock_qty, v.cost_price FROM product_variants v "
                + "JOIN products p ON p.id = v.product_id LEFT JOIN categories c ON c.id = p.category_id "
                + "WHERE p.active = 1 AND v.active = 1", rs -> {
                    long[] v = m.computeIfAbsent(rs.getString(1), k -> new long[2]);
                    v[0] += Money.mulQty(rs.getLong(3), rs.getLong(2));
                    v[1]++;
                    return null;
                });
        List<Point> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new Point(k, k, v[0], v[1])));
        out.sort((a, b) -> Long.compare(b.value(), a.value()));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /** Count of stock lines per status: in stock, low, out. */
    public long[] stockStatusCounts() {
        long[] c = new long[3];
        db.list("SELECT v.stock_qty, v.min_stock FROM product_variants v JOIN products p ON p.id = v.product_id "
                + "WHERE p.active = 1 AND v.active = 1", rs -> {
                    c[StockStatus.of(rs.getLong(1), rs.getLong(2)).ordinal()]++;
                    return null;
                });
        return c;
    }

    public List<String[]> lowStockItems(int limit) {
        return db.list("SELECT CASE WHEN v.name = '' THEN p.name ELSE p.name || ' (' || v.name || ')' END, v.stock_qty, "
                + "u.code, v.min_stock FROM product_variants v JOIN products p ON p.id = v.product_id "
                + "JOIN units u ON u.id = p.unit_id WHERE p.active = 1 AND v.active = 1 "
                + "AND (v.stock_qty <= 0 OR (v.min_stock > 0 AND v.stock_qty <= v.min_stock)) "
                + "ORDER BY v.stock_qty, p.name LIMIT ?",
                rs -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, limit);
    }

    /** Quantity sold (value, in thousandths, after returns) and money taken (value2) per day for one product. */
    public List<Point> productSales(long productId, LocalDate from, LocalDate to) {
        Map<String, long[]> m = new java.util.HashMap<>();
        db.list("SELECT substr(s.created_at, 1, 10), SUM(si.quantity - si.returned_qty), SUM(si.line_total) "
                + "FROM sale_items si JOIN sales s ON s.id = si.sale_id "
                + "WHERE si.product_id = ? AND s.created_at >= ? AND s.created_at < ? GROUP BY 1", rs -> {
                    m.put(rs.getString(1), new long[] {rs.getLong(2), rs.getLong(3)});
                    return null;
                }, productId, Dates.startOf(from), Dates.endExclusive(to));
        List<Point> out = new ArrayList<>();
        for (String key : buckets(from, to, ReportService.Grouping.DAY)) {
            long[] v = m.getOrDefault(key, new long[2]);
            out.add(new Point(key, shortLabel(key, ReportService.Grouping.DAY), v[0], v[1]));
        }
        return out;
    }

    /** What one customer bought (value, after returns) per month for the last {@code months} months, oldest first. */
    public List<Point> customerMonthly(long customerId, int months) {
        LocalDate from = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        Map<String, Long> m = new java.util.HashMap<>();
        db.list("SELECT substr(created_at, 1, 7), SUM(grand_total - returned_total) FROM sales "
                + "WHERE customer_id = ? AND created_at >= ? GROUP BY 1", rs -> {
                    m.put(rs.getString(1), rs.getLong(2));
                    return null;
                }, customerId, Dates.startOf(from));
        return months(from, months, m);
    }

    /** What the shop bought from one supplier (value) per month for the last {@code months} months, oldest first. */
    public List<Point> supplierMonthly(long supplierId, int months) {
        LocalDate from = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        Map<String, Long> m = new java.util.HashMap<>();
        db.list("SELECT substr(created_at, 1, 7), SUM(total) FROM purchases "
                + "WHERE supplier_id = ? AND status = 'RECEIVED' AND created_at >= ? GROUP BY 1", rs -> {
                    m.put(rs.getString(1), rs.getLong(2));
                    return null;
                }, supplierId, Dates.startOf(from));
        return months(from, months, m);
    }

    private static List<Point> months(LocalDate from, int months, Map<String, Long> values) {
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            LocalDate d = from.plusMonths(i);
            String key = String.format("%04d-%02d", d.getYear(), d.getMonthValue());
            out.add(new Point(key, shortLabel(d.format(Dates.ISO_DATE), ReportService.Grouping.MONTH),
                    values.getOrDefault(key, 0L), 0));
        }
        return out;
    }

    /** Money received from customers per payment method (refunds subtracted), largest first. */
    public List<Point> paymentSplit(LocalDate from, LocalDate to) {
        return db.list("SELECT COALESCE(NULLIF(payment_method_name, ''), 'Other') AS m, "
                + "SUM(CASE WHEN direction = 'IN' THEN amount ELSE -amount END) AS total FROM payments "
                + "WHERE party_type IN ('CUSTOMER', 'WALK_IN') AND created_at >= ? AND created_at < ? "
                + "GROUP BY m HAVING total > 0 ORDER BY total DESC",
                rs -> new Point(rs.getString(1), rs.getString(1), rs.getLong(2), 0),
                Dates.startOf(from), Dates.endExclusive(to));
    }

    /** Expenses per category in the period, largest first. */
    public List<Point> expensesByCategory(LocalDate from, LocalDate to) {
        return db.list("SELECT category, SUM(amount) AS total FROM expenses WHERE status = 'ACTIVE' "
                + "AND expense_date >= ? AND expense_date <= ? GROUP BY category ORDER BY total DESC",
                rs -> new Point(rs.getString(1), rs.getString(1), rs.getLong(2), 0),
                from.format(Dates.ISO_DATE), to.format(Dates.ISO_DATE));
    }

    private static List<String> buckets(LocalDate from, LocalDate to, ReportService.Grouping g) {
        List<String> keys = new ArrayList<>();
        var bucket = ReportService.bucket(g);
        LocalDate d = from;
        String last = null;
        int guard = 0;
        while (!d.isAfter(to) && guard++ < 4000) {
            String k = bucket.apply(d.format(Dates.ISO_DATE));
            if (!k.equals(last)) {
                keys.add(k);
                last = k;
            }
            d = d.plusDays(1);
        }
        return keys;
    }

    private static String shortLabel(String key, ReportService.Grouping g) {
        LocalDate d = LocalDate.parse(key, Dates.ISO_DATE);
        return switch (g) {
            case DAY -> d.getDayOfMonth() + " " + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                    java.util.Locale.ENGLISH);
            case WEEK -> "Wk " + d.getDayOfMonth() + " " + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                    java.util.Locale.ENGLISH);
            case MONTH -> d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                    + " " + d.getYear();
        };
    }
}
