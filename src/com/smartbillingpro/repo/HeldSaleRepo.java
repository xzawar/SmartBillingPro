package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.HeldSale;
import com.smartbillingpro.util.Dates;

import java.util.List;

/** Carts parked with "Hold" (not financial records, so they can be deleted). */
public final class HeldSaleRepo {
    private final Db db;

    public HeldSaleRepo(Db db) {
        this.db = db;
    }

    public long insert(String label, String payload, int items, long total) {
        return db.insert("INSERT INTO held_sales (created_at, label, payload) VALUES (?, ?, ?)", Dates.now(),
                label + "\u001f" + items + "\u001f" + total, payload);
    }

    public List<HeldSale> list() {
        return db.list("SELECT * FROM held_sales ORDER BY id", rs -> {
            String[] parts = rs.getString("label").split("\u001f");
            return new HeldSale(rs.getLong("id"), rs.getString("created_at"), parts[0],
                    rs.getString("payload"), parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                    parts.length > 2 ? Long.parseLong(parts[2]) : 0);
        });
    }

    public void delete(long id) {
        db.update("DELETE FROM held_sales WHERE id = ?", id);
    }

    public long count() {
        return db.scalar("SELECT COUNT(*) FROM held_sales");
    }
}
