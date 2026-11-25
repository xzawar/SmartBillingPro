package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Payment;
import com.smartbillingpro.util.Dates;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PaymentRepo {
    private final Db db;

    public PaymentRepo(Db db) {
        this.db = db;
    }

    public long insert(String paymentNo, String direction, String partyType, Long partyId, long amount, Long methodId,
                       String methodName, Long saleId, Long purchaseId, Long returnId, Long expenseId,
                       Long reversesPaymentId, String note) {
        return db.insert("INSERT INTO payments (payment_no, created_at, direction, party_type, party_id, amount, "
                + "payment_method_id, payment_method_name, sale_id, purchase_id, return_id, expense_id, "
                + "reverses_payment_id, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                paymentNo, Dates.now(), direction, partyType, partyId, amount, methodId, methodName, saleId,
                purchaseId, returnId, expenseId, reversesPaymentId, note);
    }

    private static Long nl(ResultSet rs, String c) throws SQLException {
        long v = rs.getLong(c);
        return rs.wasNull() ? null : v;
    }

    private static final String SQL = "SELECT p.*, "
            + "CASE p.party_type WHEN 'CUSTOMER' THEN (SELECT name FROM customers WHERE id = p.party_id) "
            + "WHEN 'SUPPLIER' THEN (SELECT name FROM suppliers WHERE id = p.party_id) "
            + "WHEN 'WALK_IN' THEN 'Walk-in customer' WHEN 'EXPENSE' THEN 'Expense' ELSE '' END AS party_name, "
            + "EXISTS (SELECT 1 FROM payments r WHERE r.reverses_payment_id = p.id) AS is_reversed FROM payments p";

    private static Payment map(ResultSet rs) throws SQLException {
        return new Payment(rs.getLong("id"), rs.getString("payment_no"), rs.getString("created_at"),
                rs.getString("direction"), rs.getString("party_type"), nl(rs, "party_id"), rs.getString("party_name"),
                rs.getLong("amount"), nl(rs, "payment_method_id"), rs.getString("payment_method_name"),
                nl(rs, "sale_id"), nl(rs, "purchase_id"), nl(rs, "return_id"), nl(rs, "expense_id"),
                nl(rs, "reverses_payment_id"), rs.getString("note"), rs.getInt("is_reversed") == 1);
    }

    public Payment find(long id) {
        return db.one(SQL + " WHERE p.id = ?", PaymentRepo::map, id);
    }

    public List<Payment> list(String fromInclusive, String toExclusive, String partyType, Long partyId,
                              String direction, int limit) {
        StringBuilder sql = new StringBuilder(SQL + " WHERE 1 = 1");
        List<Object> p = new ArrayList<>();
        if (fromInclusive != null) {
            sql.append(" AND p.created_at >= ?");
            p.add(fromInclusive);
        }
        if (toExclusive != null) {
            sql.append(" AND p.created_at < ?");
            p.add(toExclusive);
        }
        if (partyType != null) {
            sql.append(" AND p.party_type = ?");
            p.add(partyType);
        }
        if (partyId != null) {
            sql.append(" AND p.party_id = ?");
            p.add(partyId);
        }
        if (direction != null) {
            sql.append(" AND p.direction = ?");
            p.add(direction);
        }
        sql.append(" ORDER BY p.id DESC LIMIT ?");
        p.add(limit);
        return db.list(sql.toString(), PaymentRepo::map, p.toArray());
    }
}
