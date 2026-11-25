package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.PurchaseItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PurchaseRepo {
    private final Db db;

    public PurchaseRepo(Db db) {
        this.db = db;
    }

    public long insert(Purchase p) {
        return db.insert("INSERT INTO purchases (purchase_no, supplier_id, supplier_name, created_at, bill_ref, subtotal, "
                + "discount, total, amount_paid, balance_due, payment_method_id, payment_method_name, status, note) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)",
                p.purchaseNo(), p.supplierId(), p.supplierName(), p.createdAt(), p.billRef(), p.subtotal(),
                p.discount(), p.total(), p.amountPaid(), p.balanceDue(), p.paymentMethodId(), p.paymentMethodName(),
                p.note());
    }

    public void insertItem(long purchaseId, PurchaseItem i) {
        db.insert("INSERT INTO purchase_items (purchase_id, variant_id, product_name, variant_name, unit_code, quantity, "
                + "unit_cost, line_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", purchaseId, i.variantId(), i.productName(),
                i.variantName(), i.unitCode(), i.quantity(), i.unitCost(), i.lineTotal());
    }

    public void markReversed(long id, String reason) {
        db.update("UPDATE purchases SET status = 'REVERSED', reversal_reason = ? WHERE id = ?", reason, id);
    }

    private static Long nl(ResultSet rs, String c) throws SQLException {
        long v = rs.getLong(c);
        return rs.wasNull() ? null : v;
    }

    private static Purchase map(ResultSet rs, List<PurchaseItem> items) throws SQLException {
        return new Purchase(rs.getLong("id"), rs.getString("purchase_no"), nl(rs, "supplier_id"),
                rs.getString("supplier_name"), rs.getString("created_at"), rs.getString("bill_ref"),
                rs.getLong("subtotal"), rs.getLong("discount"), rs.getLong("total"), rs.getLong("amount_paid"),
                rs.getLong("balance_due"), nl(rs, "payment_method_id"), rs.getString("payment_method_name"),
                rs.getString("status"), rs.getString("note"), rs.getString("reversal_reason"), items);
    }

    public List<PurchaseItem> items(long purchaseId) {
        return db.list("SELECT * FROM purchase_items WHERE purchase_id = ? ORDER BY id", rs -> new PurchaseItem(
                rs.getLong("id"), rs.getLong("purchase_id"), rs.getLong("variant_id"), rs.getString("product_name"),
                rs.getString("variant_name"), rs.getString("unit_code"), rs.getLong("quantity"),
                rs.getLong("unit_cost"), rs.getLong("line_total")), purchaseId);
    }

    public Purchase find(long id) {
        List<PurchaseItem> items = items(id);
        return db.one("SELECT * FROM purchases WHERE id = ?", rs -> map(rs, items), id);
    }

    public List<Purchase> list(String fromInclusive, String toExclusive, Long supplierId, String search, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM purchases WHERE 1 = 1");
        List<Object> p = new ArrayList<>();
        if (fromInclusive != null) {
            sql.append(" AND created_at >= ?");
            p.add(fromInclusive);
        }
        if (toExclusive != null) {
            sql.append(" AND created_at < ?");
            p.add(toExclusive);
        }
        if (supplierId != null) {
            sql.append(" AND supplier_id = ?");
            p.add(supplierId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (purchase_no LIKE ? OR supplier_name LIKE ? OR bill_ref LIKE ?)");
            String like = "%" + search.trim() + "%";
            p.add(like);
            p.add(like);
            p.add(like);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        p.add(limit);
        return db.list(sql.toString(), rs -> map(rs, List.of()), p.toArray());
    }
}
