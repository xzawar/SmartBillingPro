package com.smartbillingpro.repository;

import com.smartbillingpro.model.Product;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes inventory products for the current store only. */
public class ProductDao {

    private static final String SELECT =
            "SELECT p.*, s.name AS supplier_name FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id";

    private final Database db;
    private final StoreSession session;

    public ProductDao(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setSku(rs.getString("sku"));
        p.setName(rs.getString("name"));
        p.setCategory(rs.getString("category"));
        p.setCostPrice(Database.toMoney(rs.getString("cost_price")));
        p.setSalePrice(Database.toMoney(rs.getString("sale_price")));
        p.setQuantity(rs.getInt("quantity"));
        int supplierId = rs.getInt("supplier_id");
        p.setSupplierId(rs.wasNull() ? null : supplierId);
        p.setSupplierName(rs.getString("supplier_name"));
        return p;
    }

    public List<Product> findAll() {
        List<Product> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                SELECT + " WHERE p.store_id = ? ORDER BY p.name")) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load products: " + e.getMessage(), e);
        }
    }

    public List<Product> search(String term) {
        List<Product> list = new ArrayList<>();
        String like = "%" + (term == null ? "" : term) + "%";
        String sql = SELECT + " WHERE p.store_id = ? AND (p.name LIKE ? OR p.sku LIKE ? OR p.category LIKE ?) ORDER BY p.name";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, session.getStoreId());
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search products: " + e.getMessage(), e);
        }
    }

    public Product findById(int id) {
        try (PreparedStatement ps = db.connection().prepareStatement(SELECT + " WHERE p.id = ? AND p.store_id = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find product: " + e.getMessage(), e);
        }
    }

    public Product findBySku(String sku) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                SELECT + " WHERE p.sku = ? AND p.store_id = ? LIMIT 1")) {
            ps.setString(1, sku);
            ps.setInt(2, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find product: " + e.getMessage(), e);
        }
    }

    public Product insert(Product p) {
        String sql = "INSERT INTO products (sku, name, category, cost_price, sale_price, quantity, supplier_id, store_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.setInt(8, session.getStoreId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setId(keys.getInt(1));
                }
            }
            return p;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create product: " + e.getMessage(), e);
        }
    }

    public void update(Product p) {
        String sql = "UPDATE products SET sku = ?, name = ?, category = ?, cost_price = ?, sale_price = ?, " +
                "quantity = ?, supplier_id = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            bind(ps, p);
            ps.setInt(8, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update product: " + e.getMessage(), e);
        }
    }

    private void bind(PreparedStatement ps, Product p) throws SQLException {
        ps.setString(1, p.getSku());
        ps.setString(2, p.getName());
        ps.setString(3, p.getCategory());
        ps.setString(4, Database.toText(p.getCostPrice()));
        ps.setString(5, Database.toText(p.getSalePrice()));
        ps.setInt(6, p.getQuantity());
        if (p.getSupplierId() == null) {
            ps.setNull(7, java.sql.Types.INTEGER);
        } else {
            ps.setInt(7, p.getSupplierId());
        }
    }

    public void delete(Product p) {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM products WHERE id = ?")) {
            ps.setInt(1, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete product: " + e.getMessage(), e);
        }
    }

    public void decreaseStock(int productId, int qty) {
        adjustStock(productId, -qty);
    }

    public void increaseStock(int productId, int qty) {
        adjustStock(productId, qty);
    }

    private void adjustStock(int productId, int delta) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE products SET quantity = quantity + ? WHERE id = ?")) {
            ps.setInt(1, delta);
            ps.setInt(2, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to adjust stock: " + e.getMessage(), e);
        }
    }

    public void updateCostPrice(int productId, String costText) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE products SET cost_price = ? WHERE id = ?")) {
            ps.setString(1, costText);
            ps.setInt(2, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update cost price: " + e.getMessage(), e);
        }
    }

    public int count() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM products WHERE store_id = ?")) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products: " + e.getMessage(), e);
        }
    }
}
