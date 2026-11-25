package com.smartbillingpro.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Versioned database schema. The current version is kept in SQLite's
 * {@code PRAGMA user_version}. Each migration runs once, inside a transaction,
 * and only ever adds to the schema - existing data is never dropped.
 *
 * <p>Conventions used by every table:</p>
 * <ul>
 *   <li>Money columns are INTEGER minor units (paisa).</li>
 *   <li>Quantity columns are INTEGER thousandths of the product unit.</li>
 *   <li>Timestamps are local-time TEXT {@code yyyy-MM-dd HH:mm:ss}.</li>
 *   <li>Financial history (sales, payments, ledger, stock movements) is
 *       append-only: triggers reject edits and deletes, corrections are new
 *       reversal / adjustment records.</li>
 * </ul>
 */
public final class Migrations {

    /** Prefix of every message raised by a protection trigger. */
    public static final String PROTECTED = "PROTECTED:";

    public static final int CURRENT_VERSION = 1;

    private Migrations() {
    }

    /** SQL statements for the migration that upgrades the schema to {@code version}. */
    public static List<String> forVersion(int version) {
        if (version == 1) {
            return v1();
        }
        throw new IllegalArgumentException("Unknown schema version " + version);
    }

    private static String noDelete(String table, String what) {
        return "CREATE TRIGGER IF NOT EXISTS trg_" + table + "_no_delete BEFORE DELETE ON " + table
                + " BEGIN SELECT RAISE(ABORT, '" + PROTECTED + " " + what + " cannot be deleted. Record a reversal or adjustment instead.'); END";
    }

    private static String noUpdate(String table, String what) {
        return "CREATE TRIGGER IF NOT EXISTS trg_" + table + "_no_update BEFORE UPDATE ON " + table
                + " BEGIN SELECT RAISE(ABORT, '" + PROTECTED + " " + what + " cannot be changed. Record a reversal or adjustment instead.'); END";
    }

    private static String lockColumns(String table, String columns, String what) {
        return "CREATE TRIGGER IF NOT EXISTS trg_" + table + "_lock BEFORE UPDATE OF " + columns + " ON " + table
                + " BEGIN SELECT RAISE(ABORT, '" + PROTECTED + " " + what + "'); END";
    }

    private static List<String> v1() {
        List<String> s = new ArrayList<>();
        s.add("CREATE TABLE IF NOT EXISTS app_meta (key TEXT PRIMARY KEY, value TEXT)");
        s.add("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        s.add("CREATE TABLE IF NOT EXISTS sequences (name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        s.add("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY, created_at TEXT NOT NULL, "
                + "action TEXT NOT NULL, details TEXT)");

        // ---------- reference data ----------
        s.add("CREATE TABLE IF NOT EXISTS units ("
                + "id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE COLLATE NOCASE, name TEXT NOT NULL, "
                + "dimension TEXT NOT NULL CHECK (dimension IN ('COUNT','WEIGHT','VOLUME','LENGTH')), "
                + "factor TEXT NOT NULL DEFAULT '1', built_in INTEGER NOT NULL DEFAULT 0, "
                + "active INTEGER NOT NULL DEFAULT 1)");
        s.add("CREATE TABLE IF NOT EXISTS payment_methods ("
                + "id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                + "is_cash INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1, "
                + "sort_order INTEGER NOT NULL DEFAULT 0)");
        s.add("CREATE TABLE IF NOT EXISTS categories ("
                + "id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE, created_at TEXT NOT NULL)");

        // ---------- parties ----------
        s.add("CREATE TABLE IF NOT EXISTS customers ("
                + "id INTEGER PRIMARY KEY, name TEXT NOT NULL CHECK (length(trim(name)) > 0), phone TEXT, address TEXT, "
                + "notes TEXT, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT)");
        s.add("CREATE INDEX IF NOT EXISTS ix_customers_phone ON customers(phone)");
        s.add("CREATE TABLE IF NOT EXISTS suppliers ("
                + "id INTEGER PRIMARY KEY, name TEXT NOT NULL CHECK (length(trim(name)) > 0), phone TEXT, address TEXT, "
                + "business_info TEXT, notes TEXT, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT)");

        // ---------- catalogue ----------
        s.add("CREATE TABLE IF NOT EXISTS products ("
                + "id INTEGER PRIMARY KEY, name TEXT NOT NULL CHECK (length(trim(name)) > 0), "
                + "category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL, description TEXT, "
                + "unit_id INTEGER NOT NULL REFERENCES units(id), "
                + "weight_value TEXT, weight_unit TEXT, "
                + "supplier_id INTEGER REFERENCES suppliers(id) ON DELETE SET NULL, "
                + "image BLOB, has_variants INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1, "
                + "created_at TEXT NOT NULL, updated_at TEXT)");
        s.add("CREATE INDEX IF NOT EXISTS ix_products_category ON products(category_id)");
        s.add("CREATE TABLE IF NOT EXISTS product_variants ("
                + "id INTEGER PRIMARY KEY, product_id INTEGER NOT NULL REFERENCES products(id), "
                + "name TEXT NOT NULL DEFAULT '', barcode TEXT UNIQUE, sku TEXT, "
                + "cost_price INTEGER NOT NULL DEFAULT 0 CHECK (cost_price >= 0), "
                + "sale_price INTEGER NOT NULL DEFAULT 0 CHECK (sale_price >= 0), "
                + "stock_qty INTEGER NOT NULL DEFAULT 0 CHECK (stock_qty >= 0), "
                + "min_stock INTEGER NOT NULL DEFAULT 0 CHECK (min_stock >= 0), "
                + "active INTEGER NOT NULL DEFAULT 1, sort_order INTEGER NOT NULL DEFAULT 0, "
                + "created_at TEXT NOT NULL, updated_at TEXT, "
                + "CHECK (barcode IS NULL OR length(trim(barcode)) > 0))");
        s.add("CREATE INDEX IF NOT EXISTS ix_variants_product ON product_variants(product_id)");
        s.add("CREATE UNIQUE INDEX IF NOT EXISTS ux_variants_sku ON product_variants(sku) WHERE sku IS NOT NULL AND sku <> ''");

        s.add("CREATE TABLE IF NOT EXISTS stock_movements ("
                + "id INTEGER PRIMARY KEY, variant_id INTEGER NOT NULL REFERENCES product_variants(id), "
                + "created_at TEXT NOT NULL, "
                + "type TEXT NOT NULL CHECK (type IN ('OPENING','SALE','RETURN','PURCHASE','PURCHASE_REVERSAL','ADJUSTMENT_IN','ADJUSTMENT_OUT')), "
                + "qty_change INTEGER NOT NULL CHECK (qty_change <> 0), balance_after INTEGER NOT NULL CHECK (balance_after >= 0), "
                + "unit_cost INTEGER, reference TEXT, reason TEXT, "
                + "sale_id INTEGER, purchase_id INTEGER, return_id INTEGER)");
        s.add("CREATE INDEX IF NOT EXISTS ix_movements_variant ON stock_movements(variant_id, created_at)");
        s.add("CREATE INDEX IF NOT EXISTS ix_movements_time ON stock_movements(created_at)");

        // ---------- sales ----------
        s.add("CREATE TABLE IF NOT EXISTS sales ("
                + "id INTEGER PRIMARY KEY, receipt_no TEXT NOT NULL UNIQUE, client_token TEXT NOT NULL UNIQUE, "
                + "created_at TEXT NOT NULL, customer_id INTEGER REFERENCES customers(id), "
                + "customer_name TEXT NOT NULL, customer_phone TEXT, "
                + "subtotal INTEGER NOT NULL, item_discount INTEGER NOT NULL DEFAULT 0, cart_discount INTEGER NOT NULL DEFAULT 0, "
                + "tax_percent TEXT NOT NULL DEFAULT '0', tax_amount INTEGER NOT NULL DEFAULT 0, grand_total INTEGER NOT NULL, "
                + "amount_paid INTEGER NOT NULL, cash_received INTEGER NOT NULL, change_given INTEGER NOT NULL DEFAULT 0, "
                + "balance_due INTEGER NOT NULL DEFAULT 0, payment_method_id INTEGER REFERENCES payment_methods(id), "
                + "payment_method_name TEXT, "
                + "status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED','PARTIALLY_RETURNED','RETURNED')), "
                + "returned_total INTEGER NOT NULL DEFAULT 0, note TEXT, "
                + "CHECK (subtotal >= 0 AND grand_total >= 0 AND amount_paid >= 0 AND balance_due >= 0 AND change_given >= 0), "
                + "CHECK (amount_paid + balance_due = grand_total), "
                + "CHECK (returned_total >= 0 AND returned_total <= grand_total), "
                + "CHECK (balance_due = 0 OR customer_id IS NOT NULL))");
        s.add("CREATE INDEX IF NOT EXISTS ix_sales_time ON sales(created_at)");
        s.add("CREATE INDEX IF NOT EXISTS ix_sales_customer ON sales(customer_id)");
        s.add("CREATE TABLE IF NOT EXISTS sale_items ("
                + "id INTEGER PRIMARY KEY, sale_id INTEGER NOT NULL REFERENCES sales(id), "
                + "variant_id INTEGER REFERENCES product_variants(id), product_id INTEGER, "
                + "product_name TEXT NOT NULL, variant_name TEXT, unit_code TEXT, barcode TEXT, category_name TEXT, "
                + "quantity INTEGER NOT NULL CHECK (quantity > 0), unit_price INTEGER NOT NULL CHECK (unit_price >= 0), "
                + "unit_cost INTEGER NOT NULL DEFAULT 0, gross_amount INTEGER NOT NULL, line_discount INTEGER NOT NULL DEFAULT 0, "
                + "cart_discount_share INTEGER NOT NULL DEFAULT 0, net_amount INTEGER NOT NULL, tax_amount INTEGER NOT NULL DEFAULT 0, "
                + "line_total INTEGER NOT NULL, cost_amount INTEGER NOT NULL DEFAULT 0, "
                + "returned_qty INTEGER NOT NULL DEFAULT 0 CHECK (returned_qty >= 0 AND returned_qty <= quantity), "
                + "CHECK (net_amount >= 0 AND line_total = net_amount + tax_amount))");
        s.add("CREATE INDEX IF NOT EXISTS ix_sale_items_sale ON sale_items(sale_id)");
        s.add("CREATE INDEX IF NOT EXISTS ix_sale_items_variant ON sale_items(variant_id)");

        s.add("CREATE TABLE IF NOT EXISTS sale_returns ("
                + "id INTEGER PRIMARY KEY, return_no TEXT NOT NULL UNIQUE, sale_id INTEGER NOT NULL REFERENCES sales(id), "
                + "created_at TEXT NOT NULL, reason TEXT, total_amount INTEGER NOT NULL CHECK (total_amount >= 0), "
                + "cash_refunded INTEGER NOT NULL DEFAULT 0 CHECK (cash_refunded >= 0), "
                + "credit_adjusted INTEGER NOT NULL DEFAULT 0 CHECK (credit_adjusted >= 0), "
                + "refund_method_id INTEGER, refund_method_name TEXT, "
                + "CHECK (cash_refunded + credit_adjusted = total_amount))");
        s.add("CREATE INDEX IF NOT EXISTS ix_returns_time ON sale_returns(created_at)");
        s.add("CREATE TABLE IF NOT EXISTS sale_return_items ("
                + "id INTEGER PRIMARY KEY, return_id INTEGER NOT NULL REFERENCES sale_returns(id), "
                + "sale_item_id INTEGER NOT NULL REFERENCES sale_items(id), quantity INTEGER NOT NULL CHECK (quantity > 0), "
                + "net_amount INTEGER NOT NULL, tax_amount INTEGER NOT NULL, total_amount INTEGER NOT NULL, cost_amount INTEGER NOT NULL)");

        s.add("CREATE TABLE IF NOT EXISTS held_sales ("
                + "id INTEGER PRIMARY KEY, created_at TEXT NOT NULL, label TEXT, payload TEXT NOT NULL)");

        // ---------- purchases ----------
        s.add("CREATE TABLE IF NOT EXISTS purchases ("
                + "id INTEGER PRIMARY KEY, purchase_no TEXT NOT NULL UNIQUE, supplier_id INTEGER REFERENCES suppliers(id), "
                + "supplier_name TEXT, created_at TEXT NOT NULL, bill_ref TEXT, "
                + "subtotal INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, "
                + "amount_paid INTEGER NOT NULL DEFAULT 0, balance_due INTEGER NOT NULL DEFAULT 0, "
                + "payment_method_id INTEGER, payment_method_name TEXT, "
                + "status TEXT NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','REVERSED')), "
                + "note TEXT, reversal_reason TEXT, "
                + "CHECK (total >= 0 AND amount_paid >= 0 AND balance_due >= 0 AND discount >= 0), "
                + "CHECK (amount_paid + balance_due = total), "
                + "CHECK (balance_due = 0 OR supplier_id IS NOT NULL))");
        s.add("CREATE INDEX IF NOT EXISTS ix_purchases_time ON purchases(created_at)");
        s.add("CREATE TABLE IF NOT EXISTS purchase_items ("
                + "id INTEGER PRIMARY KEY, purchase_id INTEGER NOT NULL REFERENCES purchases(id), "
                + "variant_id INTEGER NOT NULL REFERENCES product_variants(id), product_name TEXT NOT NULL, "
                + "variant_name TEXT, unit_code TEXT, quantity INTEGER NOT NULL CHECK (quantity > 0), "
                + "unit_cost INTEGER NOT NULL CHECK (unit_cost >= 0), line_total INTEGER NOT NULL)");
        s.add("CREATE INDEX IF NOT EXISTS ix_purchase_items_purchase ON purchase_items(purchase_id)");

        // ---------- money ----------
        s.add("CREATE TABLE IF NOT EXISTS expenses ("
                + "id INTEGER PRIMARY KEY, expense_no TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL, "
                + "expense_date TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL CHECK (amount > 0), "
                + "payment_method_id INTEGER, payment_method_name TEXT, note TEXT, "
                + "status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','VOIDED')), void_reason TEXT)");
        s.add("CREATE TABLE IF NOT EXISTS payments ("
                + "id INTEGER PRIMARY KEY, payment_no TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL, "
                + "direction TEXT NOT NULL CHECK (direction IN ('IN','OUT')), "
                + "party_type TEXT NOT NULL CHECK (party_type IN ('CUSTOMER','SUPPLIER','WALK_IN','EXPENSE','OTHER')), "
                + "party_id INTEGER, amount INTEGER NOT NULL CHECK (amount > 0), "
                + "payment_method_id INTEGER, payment_method_name TEXT, "
                + "sale_id INTEGER, purchase_id INTEGER, return_id INTEGER, expense_id INTEGER, "
                + "reverses_payment_id INTEGER UNIQUE REFERENCES payments(id), note TEXT)");
        s.add("CREATE INDEX IF NOT EXISTS ix_payments_time ON payments(created_at)");
        s.add("CREATE INDEX IF NOT EXISTS ix_payments_party ON payments(party_type, party_id)");
        s.add("CREATE TABLE IF NOT EXISTS ledger_entries ("
                + "id INTEGER PRIMARY KEY, created_at TEXT NOT NULL, "
                + "party_type TEXT NOT NULL CHECK (party_type IN ('CUSTOMER','SUPPLIER')), party_id INTEGER NOT NULL, "
                + "entry_type TEXT NOT NULL CHECK (entry_type IN ('OPENING','SALE','SALE_PAYMENT','PAYMENT','RETURN','REFUND',"
                + "'PURCHASE','PURCHASE_PAYMENT','PURCHASE_REVERSAL','ADJUSTMENT','REVERSAL')), "
                + "reference TEXT, description TEXT, "
                + "debit INTEGER NOT NULL DEFAULT 0 CHECK (debit >= 0), credit INTEGER NOT NULL DEFAULT 0 CHECK (credit >= 0), "
                + "sale_id INTEGER, purchase_id INTEGER, payment_id INTEGER, return_id INTEGER, "
                + "CHECK (debit > 0 OR credit > 0))");
        s.add("CREATE INDEX IF NOT EXISTS ix_ledger_party ON ledger_entries(party_type, party_id, created_at)");

        // ---------- protection of financial history ----------
        s.add(noUpdate("ledger_entries", "Ledger entries"));
        s.add(noDelete("ledger_entries", "Ledger entries"));
        s.add(noUpdate("payments", "Payments"));
        s.add(noDelete("payments", "Payments"));
        s.add(noUpdate("stock_movements", "Stock movements"));
        s.add(noDelete("stock_movements", "Stock movements"));
        s.add(noUpdate("sale_returns", "Returns"));
        s.add(noDelete("sale_returns", "Returns"));
        s.add(noUpdate("sale_return_items", "Returns"));
        s.add(noDelete("sale_return_items", "Returns"));
        s.add(noDelete("sales", "Sales"));
        s.add(noDelete("sale_items", "Sale lines"));
        s.add(noDelete("purchases", "Purchases"));
        s.add(noUpdate("purchase_items", "Purchase lines"));
        s.add(noDelete("purchase_items", "Purchase lines"));
        s.add(noDelete("expenses", "Expenses"));
        s.add(lockColumns("sales", "receipt_no, client_token, created_at, customer_id, customer_name, subtotal, item_discount, "
                + "cart_discount, tax_percent, tax_amount, grand_total, amount_paid, cash_received, change_given, balance_due, "
                + "payment_method_id", "Completed sales cannot be edited. Use a return instead."));
        s.add(lockColumns("sale_items", "sale_id, variant_id, quantity, unit_price, unit_cost, gross_amount, line_discount, "
                + "cart_discount_share, net_amount, tax_amount, line_total, cost_amount",
                "Sale lines cannot be edited. Use a return instead."));
        s.add(lockColumns("purchases", "purchase_no, supplier_id, created_at, subtotal, discount, total, amount_paid, balance_due",
                "Recorded purchases cannot be edited. Reverse the purchase and record it again."));
        s.add(lockColumns("expenses", "expense_no, created_at, expense_date, category, amount, payment_method_id",
                "Recorded expenses cannot be edited. Void it and record it again."));
        // Stock may only change through the inventory service, which always writes
        // a stock movement in the same transaction; this blocks moving a variant to
        // another product (which would corrupt its history).
        s.add(lockColumns("product_variants", "product_id", "A variation cannot be moved to another product."));

        // ---------- seed data ----------
        s.add("INSERT OR IGNORE INTO app_meta (key, value) VALUES ('app_id', 'SmartBillingPro-Offline')");
        s.add("INSERT OR IGNORE INTO app_meta (key, value) VALUES ('created_at', datetime('now','localtime'))");
        for (String seq : new String[] {"receipt", "return", "purchase", "payment", "expense"}) {
            s.add("INSERT OR IGNORE INTO sequences (name, value) VALUES ('" + seq + "', 0)");
        }
        String[][] units = {
            {"pcs", "Pieces", "COUNT", "1"},
            {"kg", "Kilogram", "WEIGHT", "1000"},
            {"g", "Gram", "WEIGHT", "1"},
            {"mg", "Milligram", "WEIGHT", "0.001"},
            {"L", "Litre", "VOLUME", "1000"},
            {"ml", "Millilitre", "VOLUME", "1"},
            {"m", "Metre", "LENGTH", "100"},
            {"cm", "Centimetre", "LENGTH", "1"},
        };
        for (String[] u : units) {
            s.add("INSERT OR IGNORE INTO units (code, name, dimension, factor, built_in) VALUES ('" + u[0] + "', '"
                    + u[1] + "', '" + u[2] + "', '" + u[3] + "', 1)");
        }
        String[] methods = {"Cash", "Bank Transfer", "Card", "Mobile Wallet"};
        for (int i = 0; i < methods.length; i++) {
            s.add("INSERT OR IGNORE INTO payment_methods (name, is_cash, sort_order) VALUES ('" + methods[i] + "', "
                    + (i == 0 ? 1 : 0) + ", " + i + ")");
        }
        return s;
    }
}
