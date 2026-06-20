package com.smartbillingpro.repository;

import com.smartbillingpro.model.PurchaseOrder;
import com.smartbillingpro.model.PurchaseOrderItem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes purchase orders (used to receive stock) for the current store. */
public class PurchaseDao {

    private final Database db;
    private final StoreSession session;

    public PurchaseDao(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    public PurchaseOrder insert(PurchaseOrder po) {
        String sql = "INSERT INTO purchase_orders (reference, supplier_id, supplier_name, date, status, total, store_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, po.getReference());
            ps.setInt(2, po.getSupplierId());
            ps.setString(3, po.getSupplierName());
            ps.setString(4, po.getDate());
            ps.setString(5, po.getStatus());
            ps.setString(6, Database.toText(po.getTotal()));
            ps.setInt(7, session.getStoreId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    po.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save purchase order: " + e.getMessage(), e);
        }
        insertItems(po);
        return po;
    }

    private void insertItems(PurchaseOrder po) {
        String sql = "INSERT INTO purchase_order_items (po_id, product_id, product_name, quantity, cost_price) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            for (PurchaseOrderItem item : po.getItems()) {
                ps.setInt(1, po.getId());
                ps.setInt(2, item.getProductId());
                ps.setString(3, item.getProductName());
                ps.setInt(4, item.getQuantity());
                ps.setString(5, Database.toText(item.getCostPrice()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save purchase order items: " + e.getMessage(), e);
        }
    }

    private PurchaseOrder mapHeader(ResultSet rs) throws SQLException {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(rs.getInt("id"));
        po.setReference(rs.getString("reference"));
        po.setSupplierId(rs.getInt("supplier_id"));
        po.setSupplierName(rs.getString("supplier_name"));
        po.setDate(rs.getString("date"));
        po.setStatus(rs.getString("status"));
        po.setTotal(Database.toMoney(rs.getString("total")));
        return po;
    }

    public List<PurchaseOrder> findAll() {
        List<PurchaseOrder> list = new ArrayList<>();
        String sql = "SELECT * FROM purchase_orders WHERE store_id = ? ORDER BY id DESC";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PurchaseOrder po = mapHeader(rs);
                    po.setItems(findItems(po.getId()));
                    list.add(po);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load purchase orders: " + e.getMessage(), e);
        }
    }

    public PurchaseOrder findById(int id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM purchase_orders WHERE id = ? AND store_id = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PurchaseOrder po = mapHeader(rs);
                po.setItems(findItems(id));
                return po;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load purchase order: " + e.getMessage(), e);
        }
    }

    public List<PurchaseOrderItem> findItems(int poId) {
        List<PurchaseOrderItem> items = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM purchase_order_items WHERE po_id = ? ORDER BY id")) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PurchaseOrderItem item = new PurchaseOrderItem(
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            rs.getInt("quantity"),
                            Database.toMoney(rs.getString("cost_price")));
                    item.setId(rs.getInt("id"));
                    items.add(item);
                }
            }
            return items;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load purchase order items: " + e.getMessage(), e);
        }
    }

    public int count() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM purchase_orders WHERE store_id = ?")) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count purchase orders: " + e.getMessage(), e);
        }
    }
}
