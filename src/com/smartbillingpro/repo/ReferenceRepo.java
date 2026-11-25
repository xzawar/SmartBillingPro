package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Category;
import com.smartbillingpro.model.Dimension;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.util.Dates;

import java.math.BigDecimal;
import java.util.List;

/** Units, payment methods and categories. */
public final class ReferenceRepo {
    private final Db db;

    public ReferenceRepo(Db db) {
        this.db = db;
    }

    // ---------- units ----------
    private static final String UNIT_COLS = "id, code, name, dimension, factor, built_in, active";

    private static Unit mapUnit(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Unit(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                Dimension.valueOf(rs.getString("dimension")), new BigDecimal(rs.getString("factor")),
                rs.getInt("built_in") == 1, rs.getInt("active") == 1);
    }

    public List<Unit> units(boolean activeOnly) {
        return db.list("SELECT " + UNIT_COLS + " FROM units" + (activeOnly ? " WHERE active = 1" : "")
                + " ORDER BY CASE dimension WHEN 'COUNT' THEN 0 WHEN 'WEIGHT' THEN 1 WHEN 'VOLUME' THEN 2 ELSE 3 END, "
                + "CAST(factor AS REAL) DESC", ReferenceRepo::mapUnit);
    }

    public Unit unit(long id) {
        return db.one("SELECT " + UNIT_COLS + " FROM units WHERE id = ?", ReferenceRepo::mapUnit, id);
    }

    public Unit unitByCode(String code) {
        return db.one("SELECT " + UNIT_COLS + " FROM units WHERE code = ? COLLATE NOCASE", ReferenceRepo::mapUnit, code);
    }

    public long insertUnit(String code, String name, Dimension dim, BigDecimal factor) {
        return db.insert("INSERT INTO units (code, name, dimension, factor, built_in) VALUES (?, ?, ?, ?, 0)",
                code, name, dim.name(), factor.toPlainString());
    }

    public void setUnitActive(long id, boolean active) {
        db.update("UPDATE units SET active = ? WHERE id = ?", active, id);
    }

    public long unitUsage(long id) {
        return db.scalar("SELECT COUNT(*) FROM products WHERE unit_id = ?", id);
    }

    // ---------- payment methods ----------
    private static PaymentMethod mapMethod(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PaymentMethod(rs.getLong("id"), rs.getString("name"), rs.getInt("is_cash") == 1,
                rs.getInt("active") == 1, rs.getInt("sort_order"));
    }

    public List<PaymentMethod> methods(boolean activeOnly) {
        return db.list("SELECT * FROM payment_methods" + (activeOnly ? " WHERE active = 1" : "")
                + " ORDER BY sort_order, id", ReferenceRepo::mapMethod);
    }

    public PaymentMethod method(long id) {
        return db.one("SELECT * FROM payment_methods WHERE id = ?", ReferenceRepo::mapMethod, id);
    }

    public long insertMethod(String name, boolean cash) {
        long order = db.scalar("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM payment_methods");
        return db.insert("INSERT INTO payment_methods (name, is_cash, sort_order) VALUES (?, ?, ?)", name, cash, order);
    }

    public void updateMethod(long id, String name, boolean cash, boolean active) {
        db.update("UPDATE payment_methods SET name = ?, is_cash = ?, active = ? WHERE id = ?", name, cash, active, id);
    }

    // ---------- categories ----------
    public List<Category> categories() {
        return db.list("SELECT c.id, c.name, (SELECT COUNT(*) FROM products p WHERE p.category_id = c.id AND p.active = 1) AS n "
                + "FROM categories c ORDER BY c.name COLLATE NOCASE",
                rs -> new Category(rs.getLong(1), rs.getString(2), rs.getInt(3)));
    }

    public Category categoryByName(String name) {
        return db.one("SELECT id, name, 0 FROM categories WHERE name = ? COLLATE NOCASE",
                rs -> new Category(rs.getLong(1), rs.getString(2), 0), name);
    }

    public long insertCategory(String name) {
        return db.insert("INSERT INTO categories (name, created_at) VALUES (?, ?)", name, Dates.now());
    }

    public void renameCategory(long id, String name) {
        db.update("UPDATE categories SET name = ? WHERE id = ?", name, id);
    }

    public long categoryUsage(long id) {
        return db.scalar("SELECT COUNT(*) FROM products WHERE category_id = ?", id);
    }

    public void deleteCategory(long id) {
        db.update("DELETE FROM categories WHERE id = ?", id);
    }
}
