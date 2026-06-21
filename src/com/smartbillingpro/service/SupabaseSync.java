package com.smartbillingpro.service;

import com.smartbillingpro.repository.Database;
import com.smartbillingpro.repository.StoreSession;
import com.smartbillingpro.util.CloudClient;
import com.smartbillingpro.util.CloudConfig;
import com.smartbillingpro.util.Json;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Two-way sync between the local SQLite database and the cloud (Supabase, reached
 * through the Vercel API). It runs on a daemon thread: every few seconds it
 * pushes locally-changed rows up and pulls cloud changes down, using
 * last-write-wins by {@code updated_at}.
 *
 * <h3>How change tracking works</h3>
 * Three helper columns are added to every synced table:
 * <ul>
 *   <li>{@code remote_uuid} - the row's global id shared with the cloud,</li>
 *   <li>{@code updated_at}  - ISO timestamp of the last local change,</li>
 *   <li>{@code dirty}       - 1 when the row changed locally and must be pushed.</li>
 * </ul>
 * Triggers set {@code dirty=1} and refresh {@code updated_at} on every insert or
 * update. A temporary {@code sync_guard} flag lets the sync code write rows that
 * came from the cloud WITHOUT marking them dirty again (so they are not bounced
 * straight back).
 *
 * <p>If the cloud is not configured (no {@code SMARTBILLING_CLOUD_API}) or there
 * is no saved session, this class does nothing and the app runs fully offline.</p>
 */
public class SupabaseSync {

    /** Tables in parent-before-child order. */
    private static final String[] TABLES = {
        "stores", "users", "suppliers", "products", "customers", "settings",
        "invoices", "invoice_items", "purchase_orders", "purchase_order_items", "audit_log"
    };

    /** Business columns to sync per table (local column names). */
    private static final Map<String, String[]> COLS = new LinkedHashMap<>();
    static {
        COLS.put("stores", new String[]{"name", "phone", "address", "invoice_counter"});
        COLS.put("users", new String[]{"username", "password_hash", "full_name", "role", "active"});
        COLS.put("suppliers", new String[]{"name", "phone", "address", "notes"});
        COLS.put("products", new String[]{"sku", "name", "category", "cost_price", "sale_price", "quantity", "supplier_id"});
        COLS.put("customers", new String[]{"name", "phone", "address", "loyalty_points"});
        COLS.put("settings", new String[]{"business_name", "address", "phone", "invoice_prefix", "default_tax_percent",
            "currency_symbol", "footer_message", "low_stock_threshold", "return_policy_days", "loyalty_points_per_100"});
        COLS.put("invoices", new String[]{"invoice_number", "customer_id", "customer_name", "customer_phone", "customer_address",
            "subtotal", "discount", "tax_percent", "tax_amount", "grand_total", "amount_paid", "change_due",
            "payment_method", "status", "cashier", "created_at", "void_reason", "loyalty_earned"});
        COLS.put("invoice_items", new String[]{"product_id", "sku", "name", "unit_price", "cost_price", "quantity"});
        COLS.put("purchase_orders", new String[]{"reference", "supplier_id", "supplier_name", "date", "status", "total"});
        COLS.put("purchase_order_items", new String[]{"product_id", "product_name", "quantity", "cost_price"});
        COLS.put("audit_log", new String[]{"timestamp", "username", "action", "details"});
    }

    private final Database db;
    private final StoreSession session;
    private volatile boolean running;
    private Thread worker;
    private long intervalMs = 5000;

    public SupabaseSync(Database db, StoreSession session) {
        this.db = db;
        this.session = session;
    }

    /** Prepare the local database for sync (safe to call once at startup). */
    public void prepareSchema() {
        try {
            Connection c = db.connection();
            try (Statement st = c.createStatement()) {
                // Permanent (not TEMP) so the triggers below can always find it,
                // even on connections/sessions that never ran prepareSchema().
                st.execute("CREATE TABLE IF NOT EXISTS sync_guard (active INTEGER)");
                st.execute("DELETE FROM sync_guard");
                st.execute("INSERT INTO sync_guard (active) VALUES (0)");
            }
            for (String t : TABLES) {
                addColumn(c, t, "remote_uuid", "TEXT");
                addColumn(c, t, "updated_at", "TEXT");
                addColumn(c, t, "dirty", "INTEGER NOT NULL DEFAULT 1");
                createTriggers(c, t);
            }
        } catch (Exception e) {
            System.err.println("[sync] schema prepare failed: " + e.getMessage());
        }
    }

    private void addColumn(Connection c, String table, String col, String type) {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            boolean found = false;
            while (rs.next()) {
                if (col.equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                try (Statement add = c.createStatement()) {
                    add.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + type);
                }
            }
        } catch (SQLException e) {
            // table may not exist yet; ignore
        }
    }

    private void createTriggers(Connection c, String table) throws SQLException {
        String now = "strftime('%Y-%m-%dT%H:%M:%fZ','now')";
        String guard = "WHEN NOT EXISTS (SELECT 1 FROM sync_guard WHERE active = 1)";
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TRIGGER IF NOT EXISTS trg_" + table + "_ai AFTER INSERT ON " + table + " " + guard
                    + " BEGIN UPDATE " + table + " SET dirty = 1, updated_at = " + now + " WHERE rowid = NEW.rowid; END");
            st.execute("CREATE TRIGGER IF NOT EXISTS trg_" + table + "_au AFTER UPDATE ON " + table + " " + guard
                    + " BEGIN UPDATE " + table + " SET dirty = 1, updated_at = " + now + " WHERE rowid = NEW.rowid; END");
        }
    }

    private void setGuard(boolean on) {
        try (Statement st = db.connection().createStatement()) {
            st.execute("UPDATE sync_guard SET active = " + (on ? 1 : 0));
        } catch (SQLException ignored) {
            // best effort
        }
    }

    // ---------------- background loop ----------------

    public void start() {
        if (running || !CloudConfig.isConfigured() || CloudConfig.token() == null) {
            return;
        }
        prepareSchema();
        running = true;
        worker = new Thread(this::loop, "supabase-sync");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void loop() {
        while (running) {
            try {
                syncOnce();
            } catch (Exception e) {
                System.err.println("[sync] cycle error: " + e.getMessage());
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /** One full push+pull cycle. Safe to call manually (e.g. on logout). */
    public synchronized void syncOnce() {
        if (!CloudConfig.isConfigured()) {
            return;
        }
        String token = CloudConfig.token();
        String cloudStore = CloudConfig.storeId();
        if (token == null) {
            return;
        }
        CloudClient client = new CloudClient(CloudConfig.apiBase());
        try {
            // Push BEFORE pull: send everything edited locally to the cloud first,
            // so a pull can never overwrite changes that have not been uploaded
            // yet (this is what used to make freshly-saved settings "revert").
            push(client, token, cloudStore);
            pull(client, token, cloudStore);
        } catch (CloudClient.CloudException e) {
            // Offline or auth error - try again next cycle.
            System.err.println("[sync] " + e.getMessage());
        }
    }

    // ---------------- PUSH ----------------

    private void push(CloudClient client, String token, String cloudStore) {
        Map<String, Object> tables = new LinkedHashMap<>();
        List<int[]> ignore = new ArrayList<>(); // not used; placeholder for clarity
        Map<String, List<Long>> pushedRowIds = new LinkedHashMap<>();
        Connection c = db.connection();

        for (String table : TABLES) {
            List<Object> rows = new ArrayList<>();
            List<Long> rowids = new ArrayList<>();
            String sql = "SELECT rowid, * FROM " + table + " WHERE dirty = 1";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    long rowid = rs.getLong("rowid");
                    // Never push the built-in administrator account. The cloud
                    // seeds its OWN "admin" row, so pushing this local copy would
                    // collide on the unique username and make the whole push
                    // fail - which aborts the cycle before the pull and is why a
                    // fresh admin device never downloaded the stores from the web.
                    if (table.equals("users")
                            && "admin".equalsIgnoreCase(getString(rs, "username"))) {
                        continue;
                    }
                    String uuid = getString(rs, "remote_uuid");
                    if (uuid == null || uuid.isEmpty()) {
                        uuid = UUID.randomUUID().toString();
                        assignUuid(c, table, rowid, uuid);
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", uuid);
                    row.put("local_id", hasColumn(rs, "id") ? (Object) rs.getInt("id") : (Object) rowid);
                    if (!table.equals("stores")) {
                        row.put("store_id", cloudStore);
                    }
                    for (String col : COLS.get(table)) {
                        row.put(col, getString(rs, col));
                    }
                    // Resolve parent uuids for child tables.
                    if (table.equals("invoice_items")) {
                        String parent = lookupUuid(c, "invoices", rs.getInt("invoice_id"));
                        if (parent == null) {
                            continue; // parent not pushed yet; retry next cycle
                        }
                        row.put("invoice_uuid", parent);
                    } else if (table.equals("purchase_order_items")) {
                        String parent = lookupUuid(c, "purchase_orders", rs.getInt("po_id"));
                        if (parent == null) {
                            continue;
                        }
                        row.put("po_uuid", parent);
                    }
                    row.put("updated_at", isoOrNow(getString(rs, "updated_at")));
                    row.put("deleted", false);
                    rows.add(row);
                    rowids.add(rowid);
                }
            } catch (SQLException e) {
                System.err.println("[sync] read " + table + ": " + e.getMessage());
            }
            if (!rows.isEmpty()) {
                tables.put(table, rows);
                pushedRowIds.put(table, rowids);
            }
        }

        if (tables.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tables", tables);
        client.post("/api/sync/push", body, token); // throws on failure -> rows stay dirty

        // Success: clear dirty for everything we pushed (without re-dirtying).
        setGuard(true);
        try {
            for (Map.Entry<String, List<Long>> e : pushedRowIds.entrySet()) {
                clearDirty(c, e.getKey(), e.getValue());
            }
        } finally {
            setGuard(false);
        }
    }

    private void assignUuid(Connection c, String table, long rowid, String uuid) {
        setGuard(true);
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + table + " SET remote_uuid = ? WHERE rowid = ?")) {
            ps.setString(1, uuid);
            ps.setLong(2, rowid);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[sync] assign uuid " + table + ": " + e.getMessage());
        } finally {
            setGuard(false);
        }
    }

    private void clearDirty(Connection c, String table, List<Long> rowids) {
        if (rowids.isEmpty()) {
            return;
        }
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < rowids.size(); i++) {
            in.append(i == 0 ? "" : ",").append(rowids.get(i));
        }
        try (Statement st = c.createStatement()) {
            st.execute("UPDATE " + table + " SET dirty = 0 WHERE rowid IN (" + in + ")");
        } catch (SQLException e) {
            System.err.println("[sync] clear dirty " + table + ": " + e.getMessage());
        }
    }

    // ---------------- PULL ----------------

    private void pull(CloudClient client, String token, String cloudStore) {
        String storeKey = cloudStore == null ? "_admin" : cloudStore;
        String since = CloudConfig.lastPull(storeKey);
        String path = "/api/sync/pull?since=" + urlEncode(since);
        Map<String, Object> resp = client.get(path, token);
        Object tablesObj = resp.get("tables");
        if (!(tablesObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) tablesObj;
        Connection c = db.connection();

        setGuard(true);
        try {
            for (String table : TABLES) {
                Object listObj = tables.get(table);
                if (!(listObj instanceof List)) {
                    continue;
                }
                for (Object rowObj : (List<?>) listObj) {
                    if (rowObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> row = (Map<String, Object>) rowObj;
                        applyRow(c, table, row);
                    }
                }
            }
        } finally {
            setGuard(false);
        }

        Object serverTime = resp.get("serverTime");
        if (serverTime != null) {
            CloudConfig.setLastPull(storeKey, serverTime.toString());
        }
    }

    private void applyRow(Connection c, String table, Map<String, Object> row) {
        String uuid = str(row.get("id"));
        if (uuid == null) {
            return;
        }
        boolean deleted = Boolean.TRUE.equals(row.get("deleted"));
        try {
            // Locate an existing local row by its remote_uuid.
            Long rowid = null;
            String localUpdated = null;
            boolean localDirty = false;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT rowid, updated_at, dirty FROM " + table + " WHERE remote_uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rowid = rs.getLong(1);
                        localUpdated = rs.getString(2);
                        localDirty = rs.getInt(3) == 1;
                    }
                }
            }

            // Never let cloud sync touch the built-in administrator account. It
            // is seeded identically on every device and must always stay ADMIN;
            // a stray cloud value must not demote it (which used to send the
            // admin into the store dashboard instead of the admin panel).
            if (table.equals("users")) {
                if ("admin".equalsIgnoreCase(str(row.get("username")))) {
                    return;
                }
                if (rowid != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT username FROM users WHERE rowid = ?")) {
                        ps.setLong(1, rowid);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && "admin".equalsIgnoreCase(rs.getString(1))) {
                                return;
                            }
                        }
                    }
                }
            }

            // Never overwrite a row that still has unsaved local changes waiting
            // to be pushed. The local edit wins; it will be uploaded on the push
            // half of the cycle. Without this, a pull could clobber values the
            // user just typed (e.g. settings) before they were sent to the cloud.
            if (rowid != null && localDirty && !deleted) {
                return;
            }

            if (deleted) {
                if (rowid != null) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE rowid = ?")) {
                        ps.setLong(1, rowid);
                        ps.executeUpdate();
                    }
                }
                return;
            }

            String cloudUpdated = str(row.get("updated_at"));
            if (rowid != null && !isNewer(cloudUpdated, localUpdated)) {
                return; // local copy is newer or same -> last-write-wins keeps local
            }

            String[] cols = COLS.get(table);
            if (rowid == null) {
                insertRow(c, table, uuid, cols, row);
            } else {
                updateRow(c, table, rowid, cols, row, cloudUpdated);
            }
        } catch (SQLException e) {
            System.err.println("[sync] apply " + table + ": " + e.getMessage());
        }
    }

    private void insertRow(Connection c, String table, String uuid, String[] cols, Map<String, Object> row)
            throws SQLException {
        List<String> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        names.add("remote_uuid");
        values.add(uuid);
        names.add("updated_at");
        values.add(str(row.get("updated_at")));
        names.add("dirty");
        values.add(0);
        if (!table.equals("stores")) {
            names.add("store_id");
            values.add(session.getStoreId());
        }
        for (String col : cols) {
            names.add(col);
            values.add(row.get(col));
        }
        // A store carries its unlock state in two cloud flags (enabled + activated).
        // Map them to the single local "active" column so the desktop app locks or
        // unlocks itself the moment an admin activates the store from the web.
        if (table.equals("stores")) {
            names.add("active");
            values.add(storeActive(row) ? 1 : 0);
        }
        // Re-create parent links from the parent's local id.
        if (table.equals("invoice_items")) {
            Integer pid = localIdForUuid(c, "invoices", str(row.get("invoice_uuid")));
            names.add("invoice_id");
            values.add(pid == null ? 0 : pid);
        } else if (table.equals("purchase_order_items")) {
            Integer pid = localIdForUuid(c, "purchase_orders", str(row.get("po_uuid")));
            names.add("po_id");
            values.add(pid == null ? 0 : pid);
        }
        StringBuilder cb = new StringBuilder();
        StringBuilder qb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            cb.append(i == 0 ? "" : ",").append(names.get(i));
            qb.append(i == 0 ? "" : ",").append("?");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + table + " (" + cb + ") VALUES (" + qb + ")")) {
            bind(ps, values);
            ps.executeUpdate();
        }
    }

    private void updateRow(Connection c, String table, long rowid, String[] cols, Map<String, Object> row,
            String cloudUpdated) throws SQLException {
        StringBuilder sb = new StringBuilder("UPDATE " + table + " SET updated_at = ?, dirty = 0");
        List<Object> values = new ArrayList<>();
        values.add(cloudUpdated);
        for (String col : cols) {
            sb.append(", ").append(col).append(" = ?");
            values.add(row.get(col));
        }
        if (table.equals("stores")) {
            sb.append(", active = ?");
            values.add(storeActive(row) ? 1 : 0);
        }
        sb.append(" WHERE rowid = ?");
        values.add(rowid);
        try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
            bind(ps, values);
            ps.executeUpdate();
        }
    }

    // ---------------- helpers ----------------

    /** A store is usable locally only when it is both enabled and activated. */
    private boolean storeActive(Map<String, Object> row) {
        Object en = row.get("enabled");
        Object ac = row.get("activated");
        boolean enabled = en == null || truthy(en);
        boolean activated = truthy(ac);
        return enabled && activated;
    }

    private boolean truthy(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue() != 0;
        }
        return o != null && "true".equalsIgnoreCase(o.toString());
    }

    private void bind(PreparedStatement ps, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            int idx = i + 1;
            if (v == null) {
                ps.setObject(idx, null);
            } else if (v instanceof Double) {
                double d = (Double) v;
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    ps.setLong(idx, (long) d);
                } else {
                    ps.setDouble(idx, d);
                }
            } else if (v instanceof Integer) {
                ps.setInt(idx, (Integer) v);
            } else if (v instanceof Long) {
                ps.setLong(idx, (Long) v);
            } else if (v instanceof Boolean) {
                ps.setInt(idx, ((Boolean) v) ? 1 : 0);
            } else {
                ps.setString(idx, v.toString());
            }
        }
    }

    private String lookupUuid(Connection c, String table, int localId) {
        try (PreparedStatement ps = c.prepareStatement("SELECT remote_uuid FROM " + table + " WHERE id = ?")) {
            ps.setInt(1, localId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException ignored) {
            // fall through
        }
        return null;
    }

    private Integer localIdForUuid(Connection c, String table, String uuid) {
        if (uuid == null) {
            return null;
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM " + table + " WHERE remote_uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ignored) {
            // fall through
        }
        return null;
    }

    private String getString(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException e) {
            return null;
        }
    }

    private boolean hasColumn(ResultSet rs, String col) {
        try {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (col.equalsIgnoreCase(md.getColumnLabel(i))) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
            // fall through
        }
        return false;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private String isoOrNow(String v) {
        return (v == null || v.isEmpty()) ? java.time.Instant.now().toString() : v;
    }

    /** True when the cloud timestamp is strictly newer than the local one. */
    private boolean isNewer(String cloud, String local) {
        if (local == null || local.isEmpty()) {
            return true;
        }
        if (cloud == null || cloud.isEmpty()) {
            return false;
        }
        long a = epoch(cloud);
        long b = epoch(local);
        if (a > 0 && b > 0) {
            return a > b;
        }
        return cloud.compareTo(local) > 0; // fallback to lexicographic
    }

    private long epoch(String iso) {
        try {
            return java.time.Instant.parse(iso.endsWith("Z") || iso.contains("+") ? iso : iso + "Z").toEpochMilli();
        } catch (Exception e) {
            return -1;
        }
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
