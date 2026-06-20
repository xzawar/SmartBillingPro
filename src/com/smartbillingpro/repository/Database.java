package com.smartbillingpro.repository;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.PasswordUtil;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

/**
 * Owns the single SQLite connection for the application and creates the schema.
 *
 * <p>The whole app runs on the Swing event thread, so one shared connection is
 * safe and keeps transactions simple. Multiple checkout counters can point at
 * the same database file on a shared folder; SQLite file locking plus a busy
 * timeout keeps concurrent access from different machines safe.
 */
public class Database {

    private final File dbFile;
    private Connection connection;

    public Database(File dbFile) {
        this.dbFile = dbFile;
    }

    /**
     * Where the shared database lives.
     *
     * <p>If the {@code SMARTBILLING_DB} environment variable is set, that path is
     * used - point every counter at the same shared-folder path to share data.
     * Otherwise it defaults to the per-user location ~/.smartbillingpro/shop.db
     */
    public static File defaultDbFile() {
        String configured = System.getenv("SMARTBILLING_DB");
        if (configured != null && !configured.trim().isEmpty()) {
            return new File(configured.trim());
        }
        File dir = new File(System.getProperty("user.home"), ".smartbillingpro");
        return new File(dir, "shoppro.db");
    }

    public Connection connection() {
        return connection;
    }

    /** The file this database is stored in (for live sync, backup and cloud upload). */
    public File getDbFile() {
        return dbFile;
    }

    /** Open the connection, build the schema and seed starter data. */
    public void init() {
        try {
            File parent = dbFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
                st.execute("PRAGMA journal_mode = WAL");
            }
            createSchema();
            migrate();
            seed();
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Could not open the database. Make sure the SQLite JDBC driver is on the classpath (lib folder). Details: "
                            + e.getMessage(), e);
        }
    }

    private void createSchema() throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS stores (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, address TEXT, " +
                "active INTEGER NOT NULL DEFAULT 1, created_at TEXT, invoice_counter INTEGER NOT NULL DEFAULT 1000)",
            "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, " +
                "full_name TEXT, role TEXT NOT NULL, store_id INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)",
            "CREATE TABLE IF NOT EXISTS suppliers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER NOT NULL DEFAULT 0, name TEXT NOT NULL, phone TEXT, address TEXT, notes TEXT)",
            "CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER NOT NULL DEFAULT 0, sku TEXT NOT NULL, name TEXT NOT NULL, category TEXT, " +
                "cost_price TEXT NOT NULL DEFAULT '0', sale_price TEXT NOT NULL DEFAULT '0', " +
                "quantity INTEGER NOT NULL DEFAULT 0, supplier_id INTEGER)",
            "CREATE TABLE IF NOT EXISTS customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER NOT NULL DEFAULT 0, name TEXT NOT NULL, phone TEXT, address TEXT, " +
                "loyalty_points INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS settings (" +
                "store_id INTEGER PRIMARY KEY, business_name TEXT, address TEXT, phone TEXT, " +
                "invoice_prefix TEXT, default_tax_percent TEXT, currency_symbol TEXT, footer_message TEXT, " +
                "low_stock_threshold INTEGER, return_policy_days INTEGER, loyalty_points_per_100 INTEGER)",
            "CREATE TABLE IF NOT EXISTS invoices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER NOT NULL DEFAULT 0, invoice_number TEXT NOT NULL, customer_id INTEGER, " +
                "customer_name TEXT, customer_phone TEXT, customer_address TEXT, subtotal TEXT, discount TEXT, tax_percent TEXT, tax_amount TEXT, " +
                "grand_total TEXT, amount_paid TEXT, change_due TEXT, payment_method TEXT, status TEXT, " +
                "cashier TEXT, created_at TEXT, void_reason TEXT, loyalty_earned INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS invoice_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id INTEGER NOT NULL, product_id INTEGER, " +
                "sku TEXT, name TEXT, unit_price TEXT, cost_price TEXT, quantity INTEGER, " +
                "FOREIGN KEY (invoice_id) REFERENCES invoices(id))",
            "CREATE TABLE IF NOT EXISTS purchase_orders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER NOT NULL DEFAULT 0, reference TEXT NOT NULL, supplier_id INTEGER, " +
                "supplier_name TEXT, date TEXT, status TEXT, total TEXT)",
            "CREATE TABLE IF NOT EXISTS purchase_order_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, po_id INTEGER NOT NULL, product_id INTEGER, " +
                "product_name TEXT, quantity INTEGER, cost_price TEXT, " +
                "FOREIGN KEY (po_id) REFERENCES purchase_orders(id))",
            "CREATE TABLE IF NOT EXISTS audit_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER NOT NULL DEFAULT 0, timestamp TEXT, username TEXT, action TEXT, details TEXT)",
            // Cloud-sync guard flag. Created here as a PERMANENT table (not a TEMP
            // table) so that the sync triggers, which live in the database file,
            // can always find it - even on a brand new session before cloud sync
            // has run. Without this, any insert/update would fail with
            // "no such table: sync_guard".
            "CREATE TABLE IF NOT EXISTS sync_guard (active INTEGER)"
        };
        try (Statement st = connection.createStatement()) {
            for (String sql : ddl) {
                st.execute(sql);
            }
            // Ensure exactly one guard row exists, defaulting to "not syncing".
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sync_guard")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    st.execute("INSERT INTO sync_guard (active) VALUES (0)");
                }
            }
        }
    }

    /**
     * Lightweight upgrade for databases created by an older version: adds any
     * columns that newer code expects but that an existing table is missing.
     *
     * <p>"CREATE TABLE IF NOT EXISTS" never alters an existing table, so without
     * this an old database would be missing columns such as products.store_id
     * and every insert would fail with "no column named store_id". A brand-new
     * (empty) database already has every column, so this does nothing for it.
     * This lets stores keep their data across app updates.</p>
     */
    private void migrate() throws SQLException {
        ensureColumn("products", "store_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("customers", "store_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("customers", "address", "TEXT");
        ensureColumn("customers", "loyalty_points", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("suppliers", "store_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("suppliers", "notes", "TEXT");
        ensureColumn("users", "store_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("invoices", "store_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("invoices", "customer_name", "TEXT");
        ensureColumn("invoices", "customer_phone", "TEXT");
        ensureColumn("invoices", "customer_address", "TEXT");
        ensureColumn("invoices", "amount_paid", "TEXT");
        ensureColumn("invoices", "change_due", "TEXT");
        ensureColumn("invoices", "void_reason", "TEXT");
        ensureColumn("invoices", "loyalty_earned", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("purchase_orders", "store_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("audit_log", "store_id", "INTEGER NOT NULL DEFAULT 0");
    }

    /** Add a column to a table if the table exists and the column is missing. */
    private void ensureColumn(String table, String column, String definition) throws SQLException {
        if (!tableExists(table) || columnExists(table, column)) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
            return rs.next();
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void seed() throws SQLException {
        if (isEmpty("users")) {
            try (var ps = connection.prepareStatement(
                    "INSERT INTO users (username, password_hash, full_name, role, store_id, active) VALUES (?, ?, ?, ?, 0, 1)")) {
                ps.setString(1, "admin");
                ps.setString(2, PasswordUtil.hash("admin123"));
                ps.setString(3, "System Administrator");
                ps.setString(4, Role.ADMIN.name());
                ps.executeUpdate();
            }
        }
    }

    private boolean isEmpty(String table) throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    /** Run a unit of work inside a transaction, committing on success. */
    public <T> T tx(Function<Connection, T> work) {
        try {
            connection.setAutoCommit(false);
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (RuntimeException e) {
            rollbackQuietly();
            throw e;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new DataAccessException("Transaction failed: " + e.getMessage(), e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best effort
            }
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best effort
            }
        }
    }

    /** Convert a money value to the text we store in SQLite. */
    public static String toText(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    /** Convert stored text back into a money value. */
    public static BigDecimal toMoney(String text) {
        if (text == null || text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
