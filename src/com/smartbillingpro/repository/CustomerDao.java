package com.smartbillingpro.repository;

import com.smartbillingpro.model.Customer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes customers (and loyalty points) for the current store only. */
public class CustomerDao {

    private final Database db;
    private final StoreSession session;

    public CustomerDao(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setAddress(rs.getString("address"));
        c.setLoyaltyPoints(rs.getInt("loyalty_points"));
        return c;
    }

    public List<Customer> findAll() {
        List<Customer> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM customers WHERE store_id = ? ORDER BY name")) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load customers: " + e.getMessage(), e);
        }
    }

    public List<Customer> search(String term) {
        List<Customer> list = new ArrayList<>();
        String like = "%" + (term == null ? "" : term) + "%";
        String sql = "SELECT * FROM customers WHERE store_id = ? AND (name LIKE ? OR phone LIKE ?) ORDER BY name";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, session.getStoreId());
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search customers: " + e.getMessage(), e);
        }
    }

    public Customer findById(int id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM customers WHERE id = ? AND store_id = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find customer: " + e.getMessage(), e);
        }
    }

    public Customer findByPhone(String phone) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM customers WHERE phone = ? AND store_id = ? LIMIT 1")) {
            ps.setString(1, phone);
            ps.setInt(2, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find customer: " + e.getMessage(), e);
        }
    }

    public Customer insert(Customer c) {
        String sql = "INSERT INTO customers (name, phone, address, loyalty_points, store_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getAddress());
            ps.setInt(4, c.getLoyaltyPoints());
            ps.setInt(5, session.getStoreId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setId(keys.getInt(1));
                }
            }
            return c;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create customer: " + e.getMessage(), e);
        }
    }

    public void update(Customer c) {
        String sql = "UPDATE customers SET name = ?, phone = ?, address = ?, loyalty_points = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getAddress());
            ps.setInt(4, c.getLoyaltyPoints());
            ps.setInt(5, c.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update customer: " + e.getMessage(), e);
        }
    }

    public void delete(Customer c) {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM customers WHERE id = ?")) {
            ps.setInt(1, c.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete customer: " + e.getMessage(), e);
        }
    }

    public void addLoyalty(int customerId, int points) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id = ?")) {
            ps.setInt(1, points);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to add loyalty points: " + e.getMessage(), e);
        }
    }

    public int count() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM customers WHERE store_id = ?")) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count customers: " + e.getMessage(), e);
        }
    }
}
