package com.smartbillingpro.repository;

import com.smartbillingpro.model.Store;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes stores (tenants). Only the administrator uses this. */
public class StoreDao {

    private final Database db;

    public StoreDao(Database db) {
        this.db = db;
    }

    private Store map(ResultSet rs) throws SQLException {
        Store s = new Store();
        s.setId(rs.getInt("id"));
        s.setName(rs.getString("name"));
        s.setPhone(rs.getString("phone"));
        s.setAddress(rs.getString("address"));
        s.setActive(rs.getInt("active") == 1);
        s.setCreatedAt(rs.getString("created_at"));
        s.setInvoiceCounter(rs.getInt("invoice_counter"));
        return s;
    }

    public List<Store> findAll() {
        List<Store> list = new ArrayList<>();
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery("SELECT * FROM stores ORDER BY name")) {
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load stores: " + e.getMessage(), e);
        }
    }

    public Store findById(int id) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT * FROM stores WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find store: " + e.getMessage(), e);
        }
    }

    public Store insert(Store s) {
        String sql = "INSERT INTO stores (name, phone, address, active, created_at, invoice_counter) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getAddress());
            ps.setInt(4, s.isActive() ? 1 : 0);
            ps.setString(5, s.getCreatedAt());
            ps.setInt(6, s.getInvoiceCounter());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    s.setId(keys.getInt(1));
                }
            }
            return s;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create store: " + e.getMessage(), e);
        }
    }

    public void update(Store s) {
        String sql = "UPDATE stores SET name = ?, phone = ?, address = ?, active = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getAddress());
            ps.setInt(4, s.isActive() ? 1 : 0);
            ps.setInt(5, s.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update store: " + e.getMessage(), e);
        }
    }

    public int count() {
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stores")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count stores: " + e.getMessage(), e);
        }
    }

    /**
     * The cloud uuid for this store, used by admin store actions. Returns null
     * when the column is missing (cloud sync never ran) or the store has not
     * synced yet.
     */
    public String remoteUuid(int id) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT remote_uuid FROM stores WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }
}
