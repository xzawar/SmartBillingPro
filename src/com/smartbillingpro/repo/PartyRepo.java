package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.util.Dates;

import java.util.List;

/** Customers and suppliers. Balances always come from the ledger. */
public final class PartyRepo {
    private final Db db;

    public PartyRepo(Db db) {
        this.db = db;
    }

    private static final String CUSTOMER_SQL = "SELECT c.*, COALESCE((SELECT SUM(l.debit - l.credit) FROM ledger_entries l "
            + "WHERE l.party_type = 'CUSTOMER' AND l.party_id = c.id), 0) AS balance FROM customers c";

    private static Customer mapCustomer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Customer(rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("address"),
                rs.getString("notes"), rs.getInt("active") == 1, rs.getString("created_at"), rs.getLong("balance"));
    }

    public List<Customer> customers(boolean includeInactive) {
        return db.list(CUSTOMER_SQL + (includeInactive ? "" : " WHERE c.active = 1") + " ORDER BY c.name COLLATE NOCASE",
                PartyRepo::mapCustomer);
    }

    public Customer customer(long id) {
        return db.one(CUSTOMER_SQL + " WHERE c.id = ?", PartyRepo::mapCustomer, id);
    }

    public Customer customerByPhone(String phone) {
        return db.one(CUSTOMER_SQL + " WHERE c.phone = ? AND c.active = 1", PartyRepo::mapCustomer, phone);
    }

    public long insertCustomer(String name, String phone, String address, String notes) {
        return db.insert("INSERT INTO customers (name, phone, address, notes, created_at) VALUES (?, ?, ?, ?, ?)",
                name, phone, address, notes, Dates.now());
    }

    public void updateCustomer(long id, String name, String phone, String address, String notes, boolean active) {
        db.update("UPDATE customers SET name = ?, phone = ?, address = ?, notes = ?, active = ?, updated_at = ? WHERE id = ?",
                name, phone, address, notes, active, Dates.now(), id);
    }

    private static final String SUPPLIER_SQL = "SELECT s.*, COALESCE((SELECT SUM(l.credit - l.debit) FROM ledger_entries l "
            + "WHERE l.party_type = 'SUPPLIER' AND l.party_id = s.id), 0) AS balance FROM suppliers s";

    private static Supplier mapSupplier(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Supplier(rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("address"),
                rs.getString("business_info"), rs.getString("notes"), rs.getInt("active") == 1,
                rs.getString("created_at"), rs.getLong("balance"));
    }

    public List<Supplier> suppliers(boolean includeInactive) {
        return db.list(SUPPLIER_SQL + (includeInactive ? "" : " WHERE s.active = 1") + " ORDER BY s.name COLLATE NOCASE",
                PartyRepo::mapSupplier);
    }

    public Supplier supplier(long id) {
        return db.one(SUPPLIER_SQL + " WHERE s.id = ?", PartyRepo::mapSupplier, id);
    }

    public long insertSupplier(String name, String phone, String address, String business, String notes) {
        return db.insert("INSERT INTO suppliers (name, phone, address, business_info, notes, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                name, phone, address, business, notes, Dates.now());
    }

    public void updateSupplier(long id, String name, String phone, String address, String business, String notes,
                               boolean active) {
        db.update("UPDATE suppliers SET name = ?, phone = ?, address = ?, business_info = ?, notes = ?, active = ?, "
                + "updated_at = ? WHERE id = ?", name, phone, address, business, notes, active, Dates.now(), id);
    }
}
