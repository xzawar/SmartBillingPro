package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleItem;
import com.smartbillingpro.model.SaleReturn;
import com.smartbillingpro.model.SaleStatus;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Sales, sale lines and returns. */
public final class SaleRepo {
    private final Db db;

    public SaleRepo(Db db) {
        this.db = db;
    }

    public long insertSale(Sale s, String clientToken) {
        return db.insert("INSERT INTO sales (receipt_no, client_token, created_at, customer_id, customer_name, customer_phone, "
                + "subtotal, item_discount, cart_discount, tax_percent, tax_amount, grand_total, amount_paid, cash_received, "
                + "change_given, balance_due, payment_method_id, payment_method_name, status, returned_total, note) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', 0, ?)",
                s.receiptNo(), clientToken, s.createdAt(), s.customerId(), s.customerName(), s.customerPhone(),
                s.subtotal(), s.itemDiscount(), s.cartDiscount(), s.taxPercent(), s.taxAmount(), s.grandTotal(),
                s.amountPaid(), s.cashReceived(), s.changeGiven(), s.balanceDue(), s.paymentMethodId(),
                s.paymentMethodName(), s.note());
    }

    public long insertItem(long saleId, SaleItem i) {
        return db.insert("INSERT INTO sale_items (sale_id, variant_id, product_id, product_name, variant_name, unit_code, "
                + "barcode, category_name, quantity, unit_price, unit_cost, gross_amount, line_discount, cart_discount_share, "
                + "net_amount, tax_amount, line_total, cost_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                saleId, i.variantId(), i.productId(), i.productName(), i.variantName(), i.unitCode(), i.barcode(),
                i.categoryName(), i.quantity(), i.unitPrice(), i.unitCost(), i.grossAmount(), i.lineDiscount(),
                i.cartDiscountShare(), i.netAmount(), i.taxAmount(), i.lineTotal(), i.costAmount());
    }

    public boolean tokenExists(String token) {
        return db.scalar("SELECT COUNT(*) FROM sales WHERE client_token = ?", token) > 0;
    }

    public Long idForToken(String token) {
        return db.one("SELECT id FROM sales WHERE client_token = ?", rs -> rs.getLong(1), token);
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Sale mapSale(ResultSet rs, List<SaleItem> items) throws SQLException {
        return new Sale(rs.getLong("id"), rs.getString("receipt_no"), rs.getString("created_at"),
                nullableLong(rs, "customer_id"), rs.getString("customer_name"), rs.getString("customer_phone"),
                rs.getLong("subtotal"), rs.getLong("item_discount"), rs.getLong("cart_discount"),
                new BigDecimal(rs.getString("tax_percent")), rs.getLong("tax_amount"), rs.getLong("grand_total"),
                rs.getLong("amount_paid"), rs.getLong("cash_received"), rs.getLong("change_given"),
                rs.getLong("balance_due"), nullableLong(rs, "payment_method_id"), rs.getString("payment_method_name"),
                SaleStatus.valueOf(rs.getString("status")), rs.getLong("returned_total"), rs.getString("note"), items);
    }

    private static SaleItem mapItem(ResultSet rs) throws SQLException {
        return new SaleItem(rs.getLong("id"), rs.getLong("sale_id"), nullableLong(rs, "variant_id"),
                nullableLong(rs, "product_id"), rs.getString("product_name"), rs.getString("variant_name"),
                rs.getString("unit_code"), rs.getString("barcode"), rs.getString("category_name"),
                rs.getLong("quantity"), rs.getLong("unit_price"), rs.getLong("unit_cost"), rs.getLong("gross_amount"),
                rs.getLong("line_discount"), rs.getLong("cart_discount_share"), rs.getLong("net_amount"),
                rs.getLong("tax_amount"), rs.getLong("line_total"), rs.getLong("cost_amount"), rs.getLong("returned_qty"));
    }

    public List<SaleItem> items(long saleId) {
        return db.list("SELECT * FROM sale_items WHERE sale_id = ? ORDER BY id", SaleRepo::mapItem, saleId);
    }

    public SaleItem item(long itemId) {
        return db.one("SELECT * FROM sale_items WHERE id = ?", SaleRepo::mapItem, itemId);
    }

    public Sale find(long id) {
        List<SaleItem> items = items(id);
        return db.one("SELECT * FROM sales WHERE id = ?", rs -> mapSale(rs, items), id);
    }

    public Sale findByReceipt(String receiptNo) {
        Long id = db.one("SELECT id FROM sales WHERE receipt_no = ?", rs -> rs.getLong(1), receiptNo);
        return id == null ? null : find(id);
    }

    /** Sale headers (without lines) matching the filter, newest first. */
    public List<Sale> list(String fromInclusive, String toExclusive, String search, Long methodId, Long customerId,
                           SaleStatus status, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sales WHERE 1 = 1");
        List<Object> p = new ArrayList<>();
        if (fromInclusive != null) {
            sql.append(" AND created_at >= ?");
            p.add(fromInclusive);
        }
        if (toExclusive != null) {
            sql.append(" AND created_at < ?");
            p.add(toExclusive);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (receipt_no LIKE ? OR customer_name LIKE ? OR customer_phone LIKE ?)");
            String like = "%" + search.trim() + "%";
            p.add(like);
            p.add(like);
            p.add(like);
        }
        if (methodId != null) {
            sql.append(" AND payment_method_id = ?");
            p.add(methodId);
        }
        if (customerId != null) {
            sql.append(" AND customer_id = ?");
            p.add(customerId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            p.add(status.name());
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        p.add(limit);
        return db.list(sql.toString(), rs -> mapSale(rs, List.of()), p.toArray());
    }

    public void addReturnedQty(long itemId, long qty) {
        db.update("UPDATE sale_items SET returned_qty = returned_qty + ? WHERE id = ?", qty, itemId);
    }

    public void updateReturnState(long saleId, SaleStatus status, long returnedTotal) {
        db.update("UPDATE sales SET status = ?, returned_total = ? WHERE id = ?", status.name(), returnedTotal, saleId);
    }

    public long insertReturn(String returnNo, long saleId, String createdAt, String reason, long total, long cash,
                             long credit, Long methodId, String methodName) {
        return db.insert("INSERT INTO sale_returns (return_no, sale_id, created_at, reason, total_amount, cash_refunded, "
                + "credit_adjusted, refund_method_id, refund_method_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                returnNo, saleId, createdAt, reason, total, cash, credit, methodId, methodName);
    }

    public void insertReturnItem(long returnId, long saleItemId, long qty, long net, long tax, long total, long cost) {
        db.insert("INSERT INTO sale_return_items (return_id, sale_item_id, quantity, net_amount, tax_amount, total_amount, "
                + "cost_amount) VALUES (?, ?, ?, ?, ?, ?, ?)", returnId, saleItemId, qty, net, tax, total, cost);
    }

    private static SaleReturn mapReturn(ResultSet rs) throws SQLException {
        return new SaleReturn(rs.getLong("id"), rs.getString("return_no"), rs.getLong("sale_id"),
                rs.getString("receipt_no"), rs.getString("created_at"), rs.getString("reason"),
                rs.getLong("total_amount"), rs.getLong("cash_refunded"), rs.getLong("credit_adjusted"),
                rs.getString("refund_method_name"), rs.getString("customer_name"));
    }

    private static final String RETURN_SQL = "SELECT r.*, s.receipt_no, s.customer_name FROM sale_returns r "
            + "JOIN sales s ON s.id = r.sale_id";

    public List<SaleReturn> returnsForSale(long saleId) {
        return db.list(RETURN_SQL + " WHERE r.sale_id = ? ORDER BY r.id", SaleRepo::mapReturn, saleId);
    }

    public List<SaleReturn> returns(String fromInclusive, String toExclusive) {
        return db.list(RETURN_SQL + " WHERE r.created_at >= ? AND r.created_at < ? ORDER BY r.id DESC",
                SaleRepo::mapReturn, fromInclusive, toExclusive);
    }

    /** Cash already refunded against a sale. */
    public long cashRefunded(long saleId) {
        return db.scalar("SELECT COALESCE(SUM(cash_refunded), 0) FROM sale_returns WHERE sale_id = ?", saleId);
    }
}
