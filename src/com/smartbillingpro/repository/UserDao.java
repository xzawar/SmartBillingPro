package com.smartbillingpro.repository;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes staff accounts. Accounts are global (the administrator can
 * see every account across all stores), but each account stores the id of the
 * store it belongs to.
 */
public class UserDao {

    private final Database db;

    public UserDao(Database db) {
        this.db = db;
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(Role.fromName(rs.getString("role")));
        u.setStoreId(rs.getInt("store_id"));
        u.setActive(rs.getInt("active") == 1);
        return u;
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY username";
        try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load users: " + e.getMessage(), e);
        }
    }

    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ? LIMIT 1";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user: " + e.getMessage(), e);
        }
    }

    /** All accounts that belong to a single store (used by owners and admin drill-down). */
    public List<User> findByStore(int storeId) {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE store_id = ? ORDER BY username";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load users: " + e.getMessage(), e);
        }
    }

    public User insert(User user) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role, store_id, active) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole().name());
            ps.setInt(5, user.getStoreId());
            ps.setInt(6, user.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
            return user;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create user: " + e.getMessage(), e);
        }
    }

    public void update(User user) {
        String sql = "UPDATE users SET username = ?, full_name = ?, role = ?, store_id = ?, active = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getRole().name());
            ps.setInt(4, user.getStoreId());
            ps.setInt(5, user.isActive() ? 1 : 0);
            ps.setInt(6, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update user: " + e.getMessage(), e);
        }
    }

    public void updatePassword(int userId, String passwordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update password: " + e.getMessage(), e);
        }
    }

    public int count() {
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count users: " + e.getMessage(), e);
        }
    }
}
