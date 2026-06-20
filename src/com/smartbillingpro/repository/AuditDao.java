package com.smartbillingpro.repository;

import com.smartbillingpro.model.AuditEntry;
import com.smartbillingpro.util.Dates;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Records and reads the audit trail for the current store. */
public class AuditDao {

    private final Database db;
    private final StoreSession session;

    public AuditDao(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    public void log(String username, String action, String details) {
        String sql = "INSERT INTO audit_log (timestamp, username, action, details, store_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, Dates.now());
            ps.setString(2, username);
            ps.setString(3, action);
            ps.setString(4, details);
            ps.setInt(5, session.getStoreId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to write audit log: " + e.getMessage(), e);
        }
    }

    public List<AuditEntry> findRecent(int limit) {
        List<AuditEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM audit_log WHERE store_id = ? ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, session.getStoreId());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditEntry entry = new AuditEntry(
                            rs.getString("timestamp"),
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("details"));
                    entry.setId(rs.getInt("id"));
                    list.add(entry);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read audit log: " + e.getMessage(), e);
        }
    }
}
