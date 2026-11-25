package com.smartbillingpro.repo;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.StockMovement;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Qty;

import java.util.ArrayList;
import java.util.List;

/**
 * The only place stock quantities change. Every change updates the variant's
 * stock and writes a {@code stock_movements} row in the same transaction, so
 * inventory is never modified silently.
 */
public final class StockRepo {
    private final Db db;

    public StockRepo(Db db) {
        this.db = db;
    }

    /**
     * Apply a signed change (thousandths) and record it. Fails without changing
     * anything when the result would be negative.
     *
     * @return the new stock balance
     */
    public long apply(long variantId, long delta, String type, Long unitCost, String reference, String reason,
                      Long saleId, Long purchaseId, Long returnId) {
        if (delta == 0) {
            throw new AppException("A stock change must not be zero.");
        }
        if (!db.inTransaction()) {
            throw new IllegalStateException("Stock changes must run inside a transaction.");
        }
        int updated = db.update("UPDATE product_variants SET stock_qty = stock_qty + ?, updated_at = ? "
                + "WHERE id = ? AND stock_qty + ? >= 0", delta, Dates.now(), variantId, delta);
        if (updated == 0) {
            String[] info = db.one("SELECT p.name, v.name, v.stock_qty, u.code FROM product_variants v "
                    + "JOIN products p ON p.id = v.product_id JOIN units u ON u.id = p.unit_id WHERE v.id = ?",
                    rs -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, variantId);
            if (info == null) {
                throw new AppException("The product no longer exists.");
            }
            String name = info[1] == null || info[1].isBlank() ? info[0] : info[0] + " (" + info[1] + ")";
            throw new AppException("Not enough stock of " + name + ". Available: "
                    + Qty.format(Long.parseLong(info[2]), info[3]) + ".");
        }
        long balance = db.scalar("SELECT stock_qty FROM product_variants WHERE id = ?", variantId);
        db.insert("INSERT INTO stock_movements (variant_id, created_at, type, qty_change, balance_after, unit_cost, "
                + "reference, reason, sale_id, purchase_id, return_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                variantId, Dates.now(), type, delta, balance, unitCost, reference, reason, saleId, purchaseId, returnId);
        return balance;
    }

    public List<StockMovement> movements(String fromInclusive, String toExclusive, Long productId, String search,
                                         int limit) {
        StringBuilder sql = new StringBuilder("SELECT m.*, p.name AS product_name, v.name AS variant_name, u.code AS unit_code "
                + "FROM stock_movements m JOIN product_variants v ON v.id = m.variant_id "
                + "JOIN products p ON p.id = v.product_id JOIN units u ON u.id = p.unit_id WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (fromInclusive != null) {
            sql.append(" AND m.created_at >= ?");
            params.add(fromInclusive);
        }
        if (toExclusive != null) {
            sql.append(" AND m.created_at < ?");
            params.add(toExclusive);
        }
        if (productId != null) {
            sql.append(" AND p.id = ?");
            params.add(productId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (p.name LIKE ? OR v.name LIKE ? OR m.reference LIKE ? OR m.reason LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY m.id DESC LIMIT ?");
        params.add(limit);
        return db.list(sql.toString(), rs -> {
            long cost = rs.getLong("unit_cost");
            Long unitCost = rs.wasNull() ? null : cost;
            return new StockMovement(rs.getLong("id"), rs.getLong("variant_id"), rs.getString("product_name"),
                    rs.getString("variant_name"), rs.getString("unit_code"), rs.getString("created_at"),
                    rs.getString("type"), rs.getLong("qty_change"), rs.getLong("balance_after"), unitCost,
                    rs.getString("reference"), rs.getString("reason"));
        }, params.toArray());
    }
}
