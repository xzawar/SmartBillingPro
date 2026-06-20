package com.smartbillingpro.repository;

import com.smartbillingpro.model.Supplier;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes suppliers for the current store only. */
public class SupplierDao {

    private final Database db;
    private final StoreSession session;

    public SupplierDao(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    private Supplier map(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getInt("id"));
        s.setName(rs.getString("name"));
        s.setPhone(rs.getString("phone"));
        s.setAddress(rs.getString("address"));
        s.setNotes(rs.getString("notes"));
        return s;
    }

    public List<Supplier> findAll() {
        List<Supplier> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM suppliers WHERE store_id = ? ORDER BY name")) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load suppliers: " + e.getMessage(), e);
        }
    }

    public Supplier findById(int id) {
        String sql = "SELECT * FROM suppliers WHERE id = ? AND store_id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find supplier: " + e.getMessage(), e);
        }
    }

    public Supplier insert(Supplier s) {
        String sql = "INSERT INTO suppliers (name, phone, address, notes, store_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getAddress());
            ps.setString(4, s.getNotes());
            ps.setInt(5, session.getStoreId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    s.setId(keys.getInt(1));
                }
            }
            return s;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create supplier: " + e.getMessage(), e);
        }
    }

    public void update(Supplier s) {
        String sql = "UPDATE suppliers SET name = ?, phone = ?, address = ?, notes = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getAddress());
            ps.setString(4, s.getNotes());
            ps.setInt(5, s.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update supplier: " + e.getMessage(), e);
        }
    }

    public void delete(Supplier s) {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM suppliers WHERE id = ?")) {
            ps.setInt(1, s.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete supplier: " + e.getMessage(), e);
        }
    }

    public int count() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM suppliers WHERE store_id = ?")) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count suppliers: " + e.getMessage(), e);
        }
    }
}
