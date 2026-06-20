package com.smartbillingpro.repository;

import com.smartbillingpro.model.Settings;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads and writes settings. Each store has exactly one settings row (keyed by
 * store id), so every shop keeps its own business name, receipt footer, tax,
 * etc.
 */
public class SettingsDao {

    private final Database db;
    private final StoreSession session;

    public SettingsDao(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    /** True when the current store has no settings row yet. */
    public boolean isEmpty() {
        return !existsFor(session.getStoreId());
    }

    public boolean existsFor(int storeId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM settings WHERE store_id = ?")) {
            ps.setInt(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read settings: " + e.getMessage(), e);
        }
    }

    /** Load the current store's settings. */
    public Settings load() {
        return loadFor(session.getStoreId());
    }

    public Settings loadFor(int storeId) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT * FROM settings WHERE store_id = ?")) {
            ps.setInt(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Settings.defaults();
                }
                Settings s = new Settings();
                s.setBusinessName(rs.getString("business_name"));
                s.setAddress(rs.getString("address"));
                s.setPhone(rs.getString("phone"));
                s.setInvoicePrefix(rs.getString("invoice_prefix"));
                s.setDefaultTaxPercent(Database.toMoney(rs.getString("default_tax_percent")));
                s.setCurrencySymbol(rs.getString("currency_symbol"));
                s.setFooterMessage(rs.getString("footer_message"));
                s.setLowStockThreshold(rs.getInt("low_stock_threshold"));
                s.setReturnPolicyDays(rs.getInt("return_policy_days"));
                s.setLoyaltyPointsPer100(rs.getInt("loyalty_points_per_100"));
                return s;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load settings: " + e.getMessage(), e);
        }
    }

    /** Save the current store's settings. */
    public void save(Settings s) {
        saveFor(session.getStoreId(), s);
    }

    public void saveFor(int storeId, Settings s) {
        String sql = "INSERT INTO settings (store_id, business_name, address, phone, invoice_prefix, default_tax_percent, " +
                "currency_symbol, footer_message, low_stock_threshold, return_policy_days, loyalty_points_per_100) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(store_id) DO UPDATE SET business_name = excluded.business_name, address = excluded.address, " +
                "phone = excluded.phone, invoice_prefix = excluded.invoice_prefix, " +
                "default_tax_percent = excluded.default_tax_percent, currency_symbol = excluded.currency_symbol, " +
                "footer_message = excluded.footer_message, low_stock_threshold = excluded.low_stock_threshold, " +
                "return_policy_days = excluded.return_policy_days, loyalty_points_per_100 = excluded.loyalty_points_per_100";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, storeId);
            ps.setString(2, s.getBusinessName());
            ps.setString(3, s.getAddress());
            ps.setString(4, s.getPhone());
            ps.setString(5, s.getInvoicePrefix());
            ps.setString(6, Database.toText(s.getDefaultTaxPercent()));
            ps.setString(7, s.getCurrencySymbol());
            ps.setString(8, s.getFooterMessage());
            ps.setInt(9, s.getLowStockThreshold());
            ps.setInt(10, s.getReturnPolicyDays());
            ps.setInt(11, s.getLoyaltyPointsPer100());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save settings: " + e.getMessage(), e);
        }
    }
}
