package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.PartyRepo;
import com.smartbillingpro.repo.ProductRepo;
import com.smartbillingpro.repo.ReferenceRepo;
import com.smartbillingpro.repo.SettingsRepo;
import com.smartbillingpro.repo.StockRepo;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * One-time import of products, categories, suppliers, customers and shop
 * details from the database of the previous (online, multi-store) version.
 * The old file is opened read-only and is never modified.
 */
public final class LegacyImportService {

    public record LegacyStore(int id, String name, int products, int customers) {
        @Override
        public String toString() {
            return name + " - " + products + " products, " + customers + " customers";
        }
    }

    public record Result(int categories, int suppliers, int products, int customers, List<String> notes) {
    }

    private final Db db;
    private final ProductRepo products;
    private final StockRepo stock;
    private final PartyRepo parties;
    private final ReferenceRepo refs;
    private final SettingsRepo settingsRepo;
    private final SettingsService settings;
    private final AuditRepo audit;

    public LegacyImportService(Db db, ProductRepo products, StockRepo stock, PartyRepo parties, ReferenceRepo refs,
                               SettingsRepo settingsRepo, SettingsService settings, AuditRepo audit) {
        this.db = db;
        this.products = products;
        this.stock = stock;
        this.parties = parties;
        this.refs = refs;
        this.settingsRepo = settingsRepo;
        this.settings = settings;
        this.audit = audit;
    }

    /** True when this installation has no products, customers or sales yet. */
    public boolean currentDatabaseIsEmpty() {
        return db.scalar("SELECT (SELECT COUNT(*) FROM products) + (SELECT COUNT(*) FROM customers) + (SELECT COUNT(*) FROM sales)") == 0;
    }

    private static Connection open(File file) throws SQLException {
        Properties p = new Properties();
        p.setProperty("open_mode", "1");
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath(), p);
    }

    private static boolean hasColumn(Statement st, String table, String column) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTable(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /** Stores found in an old database file (one entry with id 0 when the file is single-store). */
    public List<LegacyStore> scan(File file) {
        if (file == null || !file.isFile()) {
            return List.of();
        }
        List<LegacyStore> out = new ArrayList<>();
        try (Connection c = open(file); Statement st = c.createStatement()) {
            if (!hasTable(st, "products")) {
                return List.of();
            }
            boolean scoped = hasColumn(st, "products", "store_id");
            if (scoped && hasTable(st, "stores")) {
                Map<Integer, String> names = new HashMap<>();
                try (ResultSet rs = st.executeQuery("SELECT id, name FROM stores")) {
                    while (rs.next()) {
                        names.put(rs.getInt(1), rs.getString(2));
                    }
                }
                try (ResultSet rs = st.executeQuery("SELECT store_id, COUNT(*) FROM products GROUP BY store_id")) {
                    List<int[]> counts = new ArrayList<>();
                    while (rs.next()) {
                        counts.add(new int[] {rs.getInt(1), rs.getInt(2)});
                    }
                    for (int[] cnt : counts) {
                        out.add(new LegacyStore(cnt[0], names.getOrDefault(cnt[0], "Store " + cnt[0]), cnt[1],
                                countCustomers(st, cnt[0])));
                    }
                }
            } else {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                    rs.next();
                    out.add(new LegacyStore(0, "Previous shop data", rs.getInt(1), countCustomers(st, -1)));
                }
            }
        } catch (SQLException e) {
            return List.of();
        }
        out.removeIf(s -> s.products() == 0 && s.customers() == 0);
        return out;
    }

    private static int countCustomers(Statement st, int storeId) throws SQLException {
        if (!hasTable(st, "customers")) {
            return 0;
        }
        String sql = storeId >= 0 && hasColumn(st, "customers", "store_id")
                ? "SELECT COUNT(*) FROM customers WHERE store_id = " + storeId : "SELECT COUNT(*) FROM customers";
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static BigDecimal decimal(String s) {
        try {
            return s == null || s.isBlank() ? BigDecimal.ZERO : new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public Result importStore(File file, LegacyStore store) {
        if (!currentDatabaseIsEmpty()) {
            throw new AppException("Import is only possible into an empty shop (no products, customers or sales yet).");
        }
        List<String> notes = new ArrayList<>();
        try (Connection c = open(file); Statement st = c.createStatement()) {
            boolean scoped = hasColumn(st, "products", "store_id");
            String where = scoped ? " WHERE store_id = " + store.id() : "";
            List<Object[]> suppliers = new ArrayList<>();
            if (hasTable(st, "suppliers")) {
                boolean notesCol = hasColumn(st, "suppliers", "notes");
                try (ResultSet rs = st.executeQuery("SELECT id, name, phone, address" + (notesCol ? ", notes" : "")
                        + " FROM suppliers" + (scoped && hasColumn(st, "suppliers", "store_id") ? where : ""))) {
                    while (rs.next()) {
                        suppliers.add(new Object[] {rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            notesCol ? rs.getString(5) : null});
                    }
                }
            }
            List<Object[]> prods = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT sku, name, category, cost_price, sale_price, quantity, supplier_id "
                    + "FROM products" + where)) {
                while (rs.next()) {
                    int sup = rs.getInt(7);
                    prods.add(new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6), rs.wasNull() ? null : sup});
                }
            }
            List<String[]> customers = new ArrayList<>();
            if (hasTable(st, "customers")) {
                boolean addr = hasColumn(st, "customers", "address");
                try (ResultSet rs = st.executeQuery("SELECT name, phone" + (addr ? ", address" : "") + " FROM customers"
                        + (scoped && hasColumn(st, "customers", "store_id") ? where : ""))) {
                    while (rs.next()) {
                        customers.add(new String[] {rs.getString(1), rs.getString(2), addr ? rs.getString(3) : null});
                    }
                }
            }
            Map<String, String> shop = new HashMap<>();
            if (hasTable(st, "settings") && hasColumn(st, "settings", "business_name")) {
                String sql = "SELECT business_name, address, phone, invoice_prefix, currency_symbol, footer_message FROM settings"
                        + (hasColumn(st, "settings", "store_id") ? " WHERE store_id = " + store.id() : "") + " LIMIT 1";
                try (ResultSet rs = st.executeQuery(sql)) {
                    if (rs.next()) {
                        put(shop, Settings.STORE_NAME, rs.getString(1));
                        put(shop, Settings.STORE_ADDRESS, rs.getString(2));
                        put(shop, Settings.STORE_PHONE, rs.getString(3));
                        String prefix = rs.getString(4);
                        if (prefix != null && prefix.trim().matches("[A-Za-z0-9]{1,8}")) {
                            shop.put(Settings.RECEIPT_PREFIX, prefix.trim());
                        }
                        String sym = rs.getString(5);
                        if (sym != null && !sym.isBlank() && sym.trim().length() <= 6) {
                            shop.put(Settings.CURRENCY_SYMBOL, sym.trim());
                        }
                        put(shop, Settings.RECEIPT_FOOTER, rs.getString(6));
                    }
                }
            }

            int[] counts = new int[4];
            db.txRun(() -> {
                long pcs = refs.unitByCode("pcs").id();
                Map<Integer, Long> supplierIds = new HashMap<>();
                for (Object[] s : suppliers) {
                    String name = s[1] == null || s[1].toString().isBlank() ? "Supplier " + s[0] : s[1].toString().trim();
                    supplierIds.put((Integer) s[0], parties.insertSupplier(name, blank((String) s[2]), blank((String) s[3]),
                            null, blank((String) s[4])));
                    counts[1]++;
                }
                Map<String, Long> categoryIds = new HashMap<>();
                Set<String> barcodes = new HashSet<>();
                for (Object[] p : prods) {
                    String name = p[1] == null ? "" : p[1].toString().trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    Long categoryId = null;
                    String cat = p[2] == null ? "" : p[2].toString().trim();
                    if (!cat.isEmpty()) {
                        categoryId = categoryIds.get(cat.toLowerCase());
                        if (categoryId == null) {
                            categoryId = refs.insertCategory(cat);
                            categoryIds.put(cat.toLowerCase(), categoryId);
                            counts[0]++;
                        }
                    }
                    Long supplierId = p[6] == null ? null : supplierIds.get((Integer) p[6]);
                    long pid = products.insertProduct(name, categoryId, null, pcs, null, null, supplierId, false, null);
                    String code = p[0] == null ? null : p[0].toString().trim();
                    if (code != null && (code.isEmpty() || !barcodes.add(code))) {
                        if (!code.isEmpty()) {
                            notes.add("Duplicate code " + code + " on \"" + name + "\" was not imported.");
                        }
                        code = null;
                    }
                    long cost = Money.fromDecimal(decimal((String) p[3]));
                    long price = Money.fromDecimal(decimal((String) p[4]));
                    long vid = products.insertVariant(pid, "", code, code, Math.max(0, cost), Math.max(0, price),
                            Qty.of(settings.get().lowStockDefault()), 0);
                    long qty = (Long) p[5];
                    if (qty > 0) {
                        stock.apply(vid, Qty.of(qty), "OPENING", cost, null, "Imported from previous version", null, null, null);
                    } else if (qty < 0) {
                        notes.add("\"" + name + "\" had negative stock (" + qty + ") and was imported with 0.");
                    }
                    counts[2]++;
                }
                Set<String> phones = new HashSet<>();
                for (String[] cu : customers) {
                    if (cu[0] == null || cu[0].isBlank()) {
                        continue;
                    }
                    String phone = blank(cu[1]);
                    if (phone != null && !phones.add(phone)) {
                        phone = null;
                    }
                    parties.insertCustomer(cu[0].trim(), phone, blank(cu[2]), null);
                    counts[3]++;
                }
                for (Map.Entry<String, String> e : shop.entrySet()) {
                    settingsRepo.put(e.getKey(), e.getValue());
                }
                audit.log("LEGACY_IMPORT", "Imported " + counts[2] + " products, " + counts[3] + " customers from "
                        + file.getName() + " (" + store.name() + ")");
            });
            settings.reload();
            notes.add("Old sales history stays in the previous version's file and was not imported; "
                    + "opening balances can be added from each customer's ledger.");
            return new Result(counts[0], counts[1], counts[2], counts[3], notes);
        } catch (SQLException e) {
            throw new AppException("The previous version's data could not be read: " + e.getMessage(), e);
        }
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
