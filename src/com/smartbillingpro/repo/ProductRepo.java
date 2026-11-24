package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Dimension;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.util.Dates;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Products and their variants. Stock quantities are changed only via {@link StockRepo}. */
public final class ProductRepo {
    private final Db db;

    public ProductRepo(Db db) {
        this.db = db;
    }

    private static final String PRODUCT_SQL = "SELECT p.id, p.name, p.category_id, c.name AS category_name, p.description, "
            + "p.unit_id, u.code AS unit_code, u.dimension, p.weight_value, p.weight_unit, p.supplier_id, "
            + "s.name AS supplier_name, p.has_variants, p.active, (p.image IS NOT NULL) AS has_image "
            + "FROM products p JOIN units u ON u.id = p.unit_id "
            + "LEFT JOIN categories c ON c.id = p.category_id LEFT JOIN suppliers s ON s.id = p.supplier_id";

    private static final String VARIANT_SQL = "SELECT id, product_id, name, barcode, sku, cost_price, sale_price, "
            + "stock_qty, min_stock, active FROM product_variants";

    private static Variant mapVariant(ResultSet rs) throws SQLException {
        return new Variant(rs.getLong("id"), rs.getLong("product_id"), rs.getString("name"), rs.getString("barcode"),
                rs.getString("sku"), rs.getLong("cost_price"), rs.getLong("sale_price"), rs.getLong("stock_qty"),
                rs.getLong("min_stock"), rs.getInt("active") == 1);
    }

    private static Product mapProduct(ResultSet rs, List<Variant> variants) throws SQLException {
        long cat = rs.getLong("category_id");
        Long categoryId = rs.wasNull() ? null : cat;
        long sup = rs.getLong("supplier_id");
        Long supplierId = rs.wasNull() ? null : sup;
        return new Product(rs.getLong("id"), rs.getString("name"), categoryId, rs.getString("category_name"),
                rs.getString("description"), rs.getLong("unit_id"), rs.getString("unit_code"),
                Dimension.valueOf(rs.getString("dimension")) != Dimension.COUNT, rs.getString("weight_value"),
                rs.getString("weight_unit"), supplierId, rs.getString("supplier_name"), rs.getInt("has_variants") == 1,
                rs.getInt("active") == 1, rs.getInt("has_image") == 1, variants);
    }

    /** All products with their variants (two queries, grouped in memory). */
    public List<Product> list(boolean includeArchived) {
        Map<Long, List<Variant>> byProduct = new LinkedHashMap<>();
        for (Variant v : db.list(VARIANT_SQL + " ORDER BY product_id, sort_order, id", ProductRepo::mapVariant)) {
            byProduct.computeIfAbsent(v.productId(), k -> new ArrayList<>()).add(v);
        }
        return db.list(PRODUCT_SQL + (includeArchived ? "" : " WHERE p.active = 1") + " ORDER BY p.name COLLATE NOCASE",
                rs -> mapProduct(rs, byProduct.getOrDefault(rs.getLong("id"), List.of())));
    }

    public Product find(long id) {
        List<Variant> variants = db.list(VARIANT_SQL + " WHERE product_id = ? ORDER BY sort_order, id",
                ProductRepo::mapVariant, id);
        return db.one(PRODUCT_SQL + " WHERE p.id = ?", rs -> mapProduct(rs, variants), id);
    }

    public Variant variant(long id) {
        return db.one(VARIANT_SQL + " WHERE id = ?", ProductRepo::mapVariant, id);
    }

    public long insertProduct(String name, Long categoryId, String description, long unitId, String weightValue,
                              String weightUnit, Long supplierId, boolean hasVariants, byte[] image) {
        return db.insert("INSERT INTO products (name, category_id, description, unit_id, weight_value, weight_unit, "
                + "supplier_id, has_variants, image, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                name, categoryId, description, unitId, weightValue, weightUnit, supplierId, hasVariants, image, Dates.now());
    }

    public void updateProduct(long id, String name, Long categoryId, String description, long unitId,
                              String weightValue, String weightUnit, Long supplierId, boolean hasVariants) {
        db.update("UPDATE products SET name = ?, category_id = ?, description = ?, unit_id = ?, weight_value = ?, "
                + "weight_unit = ?, supplier_id = ?, has_variants = ?, updated_at = ? WHERE id = ?",
                name, categoryId, description, unitId, weightValue, weightUnit, supplierId, hasVariants, Dates.now(), id);
    }

    public void setImage(long productId, byte[] image) {
        db.update("UPDATE products SET image = ?, updated_at = ? WHERE id = ?", image, Dates.now(), productId);
    }

    public byte[] image(long productId) {
        return db.one("SELECT image FROM products WHERE id = ?", rs -> rs.getBytes(1), productId);
    }

    public void setProductActive(long id, boolean active) {
        db.update("UPDATE products SET active = ?, updated_at = ? WHERE id = ?", active, Dates.now(), id);
    }

    public long insertVariant(long productId, String name, String barcode, String sku, long cost, long price,
                              long minStock, int sortOrder) {
        return db.insert("INSERT INTO product_variants (product_id, name, barcode, sku, cost_price, sale_price, "
                + "stock_qty, min_stock, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                productId, name, barcode, sku, cost, price, minStock, sortOrder, Dates.now());
    }

    public void updateVariant(long id, String name, String barcode, String sku, long cost, long price, long minStock,
                              int sortOrder, boolean active) {
        db.update("UPDATE product_variants SET name = ?, barcode = ?, sku = ?, cost_price = ?, sale_price = ?, "
                + "min_stock = ?, sort_order = ?, active = ?, updated_at = ? WHERE id = ?",
                name, barcode, sku, cost, price, minStock, sortOrder, active, Dates.now(), id);
    }

    public void updateCost(long variantId, long cost) {
        db.update("UPDATE product_variants SET cost_price = ?, updated_at = ? WHERE id = ?", cost, Dates.now(), variantId);
    }

    /** True if anything (stock movement, sale, purchase) refers to the variant. */
    public boolean variantHasHistory(long variantId) {
        return db.scalar("SELECT (SELECT COUNT(*) FROM stock_movements WHERE variant_id = ?) "
                + "+ (SELECT COUNT(*) FROM sale_items WHERE variant_id = ?) "
                + "+ (SELECT COUNT(*) FROM purchase_items WHERE variant_id = ?)", variantId, variantId, variantId) > 0;
    }

    public void deleteVariant(long variantId) {
        db.update("DELETE FROM product_variants WHERE id = ?", variantId);
    }

    public void deleteProduct(long productId) {
        db.update("DELETE FROM product_variants WHERE product_id = ?", productId);
        db.update("DELETE FROM products WHERE id = ?", productId);
    }

    /** Name of the product that already owns {@code barcode}, ignoring one variant (when editing). */
    public String barcodeOwner(String barcode, long excludeVariantId) {
        return db.one("SELECT p.name, v.name, p.active FROM product_variants v JOIN products p ON p.id = v.product_id "
                + "WHERE v.barcode = ? AND v.id <> ?", rs -> {
                    String n = rs.getString(1);
                    String vn = rs.getString(2);
                    String full = vn == null || vn.isBlank() ? n : n + " (" + vn + ")";
                    return rs.getInt(3) == 1 ? full : full + " [archived]";
                }, barcode, excludeVariantId);
    }

    public String skuOwner(String sku, long excludeVariantId) {
        return db.one("SELECT p.name FROM product_variants v JOIN products p ON p.id = v.product_id "
                + "WHERE v.sku = ? AND v.id <> ?", rs -> rs.getString(1), sku, excludeVariantId);
    }

    // ---------- sellable lookups (POS) ----------

    private static final String SELLABLE_SQL = "SELECT v.id AS variant_id, p.id AS product_id, p.name AS product_name, "
            + "v.name AS variant_name, v.barcode, v.sku, p.unit_id, u.code AS unit_code, u.dimension, u.factor, "
            + "c.name AS category_name, v.cost_price, v.sale_price, v.stock_qty, v.min_stock, "
            + "(v.active = 1 AND p.active = 1) AS active "
            + "FROM product_variants v JOIN products p ON p.id = v.product_id JOIN units u ON u.id = p.unit_id "
            + "LEFT JOIN categories c ON c.id = p.category_id";

    private static SellableItem mapSellable(ResultSet rs) throws SQLException {
        return new SellableItem(rs.getLong("variant_id"), rs.getLong("product_id"), rs.getString("product_name"),
                rs.getString("variant_name"), rs.getString("barcode"), rs.getString("sku"), rs.getLong("unit_id"),
                rs.getString("unit_code"), Dimension.valueOf(rs.getString("dimension")),
                new BigDecimal(rs.getString("factor")), rs.getString("category_name"), rs.getLong("cost_price"),
                rs.getLong("sale_price"), rs.getLong("stock_qty"), rs.getLong("min_stock"), rs.getInt("active") == 1);
    }

    public SellableItem sellable(long variantId) {
        return db.one(SELLABLE_SQL + " WHERE v.id = ?", ProductRepo::mapSellable, variantId);
    }

    public SellableItem sellableByCode(String code) {
        SellableItem byBarcode = db.one(SELLABLE_SQL + " WHERE v.barcode = ?", ProductRepo::mapSellable, code);
        if (byBarcode != null) {
            return byBarcode;
        }
        return db.one(SELLABLE_SQL + " WHERE v.sku = ? AND v.sku <> ''", ProductRepo::mapSellable, code);
    }

    public List<SellableItem> sellablesForProduct(long productId) {
        return db.list(SELLABLE_SQL + " WHERE p.id = ? AND v.active = 1 ORDER BY v.sort_order, v.id",
                ProductRepo::mapSellable, productId);
    }

    /** Active variants matching a name / barcode / SKU fragment (for purchase entry and search). */
    public List<SellableItem> searchSellable(String term, int limit) {
        String like = "%" + (term == null ? "" : term.trim()) + "%";
        return db.list(SELLABLE_SQL + " WHERE v.active = 1 AND p.active = 1 AND (p.name LIKE ? OR v.name LIKE ? "
                + "OR v.barcode LIKE ? OR v.sku LIKE ?) ORDER BY p.name COLLATE NOCASE, v.sort_order LIMIT ?",
                ProductRepo::mapSellable, like, like, like, like, limit);
    }

    public long count() {
        return db.scalar("SELECT COUNT(*) FROM products WHERE active = 1");
    }
}
