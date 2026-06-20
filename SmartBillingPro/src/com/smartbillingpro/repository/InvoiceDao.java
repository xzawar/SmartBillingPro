package com.smartbillingpro.repository;

import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceItem;
import com.smartbillingpro.model.InvoiceStatus;
import com.smartbillingpro.model.PaymentMethod;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes invoices and their line items for the current store. */
public class InvoiceDao {

    private final Database db;
    private final StoreSession session;

    public InvoiceDao(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    /**
     * Reserve the next invoice number for the current store, e.g. INV-1001.
     * Each store keeps its own counter. Call inside a transaction.
     */
    public String nextInvoiceNumber(String prefix) {
        int storeId = session.getStoreId();
        try (PreparedStatement up = db.connection().prepareStatement(
                "UPDATE stores SET invoice_counter = invoice_counter + 1 WHERE id = ?")) {
            up.setInt(1, storeId);
            up.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate invoice number: " + e.getMessage(), e);
        }
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT invoice_counter FROM stores WHERE id = ?")) {
            ps.setInt(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                int counter = rs.next() ? rs.getInt(1) : 1001;
                return (prefix == null || prefix.isEmpty() ? "INV" : prefix) + "-" + counter;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate invoice number: " + e.getMessage(), e);
        }
    }

    public Invoice insert(Invoice inv) {
        String sql = "INSERT INTO invoices (store_id, invoice_number, customer_id, customer_name, customer_phone, " +
                "customer_address, subtotal, discount, tax_percent, tax_amount, grand_total, amount_paid, change_due, " +
                "payment_method, status, cashier, created_at, void_reason, loyalty_earned) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, session.getStoreId());
            ps.setString(2, inv.getInvoiceNumber());
            if (inv.getCustomerId() == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, inv.getCustomerId());
            }
            ps.setString(4, inv.getCustomerName());
            ps.setString(5, inv.getCustomerPhone());
            ps.setString(6, inv.getCustomerAddress());
            ps.setString(7, Database.toText(inv.getSubtotal()));
            ps.setString(8, Database.toText(inv.getDiscount()));
            ps.setString(9, Database.toText(inv.getTaxPercent()));
            ps.setString(10, Database.toText(inv.getTaxAmount()));
            ps.setString(11, Database.toText(inv.getGrandTotal()));
            ps.setString(12, Database.toText(inv.getAmountPaid()));
            ps.setString(13, Database.toText(inv.getChangeDue()));
            ps.setString(14, inv.getPaymentMethod().name());
            ps.setString(15, inv.getStatus().name());
            ps.setString(16, inv.getCashier());
            ps.setString(17, inv.getCreatedAt());
            ps.setString(18, inv.getVoidReason());
            ps.setInt(19, inv.getLoyaltyEarned());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    inv.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save invoice: " + e.getMessage(), e);
        }
        insertItems(inv);
        return inv;
    }

    private void insertItems(Invoice inv) {
        String sql = "INSERT INTO invoice_items (invoice_id, product_id, sku, name, unit_price, cost_price, quantity) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            for (InvoiceItem item : inv.getItems()) {
                ps.setInt(1, inv.getId());
                ps.setInt(2, item.getProductId());
                ps.setString(3, item.getSku());
                ps.setString(4, item.getName());
                ps.setString(5, Database.toText(item.getUnitPrice()));
                ps.setString(6, Database.toText(item.getCostPrice()));
                ps.setInt(7, item.getQuantity());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save invoice items: " + e.getMessage(), e);
        }
    }

    public void updateStatus(int id, InvoiceStatus status, String reason) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE invoices SET status = ?, void_reason = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, reason);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update invoice status: " + e.getMessage(), e);
        }
    }

    private Invoice mapHeader(ResultSet rs) throws SQLException {
        Invoice inv = new Invoice();
        inv.setId(rs.getInt("id"));
        inv.setInvoiceNumber(rs.getString("invoice_number"));
        int customerId = rs.getInt("customer_id");
        inv.setCustomerId(rs.wasNull() ? null : customerId);
        inv.setCustomerName(rs.getString("customer_name"));
        inv.setCustomerPhone(rs.getString("customer_phone"));
        inv.setCustomerAddress(rs.getString("customer_address"));
        inv.setSubtotal(Database.toMoney(rs.getString("subtotal")));
        inv.setDiscount(Database.toMoney(rs.getString("discount")));
        inv.setTaxPercent(Database.toMoney(rs.getString("tax_percent")));
        inv.setTaxAmount(Database.toMoney(rs.getString("tax_amount")));
        inv.setGrandTotal(Database.toMoney(rs.getString("grand_total")));
        inv.setAmountPaid(Database.toMoney(rs.getString("amount_paid")));
        inv.setChangeDue(Database.toMoney(rs.getString("change_due")));
        inv.setPaymentMethod(PaymentMethod.fromName(rs.getString("payment_method")));
        inv.setStatus(InvoiceStatus.fromName(rs.getString("status")));
        inv.setCashier(rs.getString("cashier"));
        inv.setCreatedAt(rs.getString("created_at"));
        inv.setVoidReason(rs.getString("void_reason"));
        inv.setLoyaltyEarned(rs.getInt("loyalty_earned"));
        return inv;
    }

    public List<Invoice> findAll() {
        List<Invoice> list = new ArrayList<>();
        String sql = "SELECT i.*, (SELECT COUNT(*) FROM invoice_items ii WHERE ii.invoice_id = i.id) AS item_count " +
                "FROM invoices i WHERE i.store_id = ? ORDER BY i.id DESC";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Invoice inv = mapHeader(rs);
                    inv.setItemCount(rs.getInt("item_count"));
                    list.add(inv);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load invoices: " + e.getMessage(), e);
        }
    }

    public Invoice findById(int id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM invoices WHERE id = ? AND store_id = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, session.getStoreId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Invoice inv = mapHeader(rs);
                inv.setItems(findItems(id));
                return inv;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load invoice: " + e.getMessage(), e);
        }
    }

    public List<InvoiceItem> findItems(int invoiceId) {
        List<InvoiceItem> items = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM invoice_items WHERE invoice_id = ? ORDER BY id")) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InvoiceItem item = new InvoiceItem(
                            rs.getInt("product_id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            Database.toMoney(rs.getString("unit_price")),
                            rs.getInt("quantity"));
                    item.setCostPrice(Database.toMoney(rs.getString("cost_price")));
                    items.add(item);
                }
            }
            return items;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load invoice items: " + e.getMessage(), e);
        }
    }
}
