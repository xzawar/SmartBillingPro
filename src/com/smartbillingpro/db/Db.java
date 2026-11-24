package com.smartbillingpro.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Thin JDBC helper used by every repository: parameter binding, row mapping and
 * transactions. All application database access goes through one connection on
 * the Swing event thread, which keeps transactions simple and race-free.
 */
public final class Db {

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Connection conn;
    private int txDepth = 0;

    public Db(Connection conn) {
        this.conn = conn;
    }

    public Connection connection() {
        return conn;
    }

    public <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = prepare(sql, false, params); ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapper.map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** First row or {@code null}. */
    public <T> T one(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = prepare(sql, false, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? mapper.map(rs) : null;
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** First column of the first row as a long (0 when no row / NULL). */
    public long scalar(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, false, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    public String scalarText(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, false, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    public int update(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, false, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** Executes an INSERT and returns the generated row id. */
    public long insert(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, true, params)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return scalar("SELECT last_insert_rowid()");
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    public void exec(String sql) {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /**
     * Runs {@code work} atomically: either every write inside it is committed or
     * none is. Nested calls join the outer transaction.
     */
    public <T> T tx(Supplier<T> work) {
        boolean outer = txDepth == 0;
        try {
            if (outer) {
                conn.setAutoCommit(false);
            }
            txDepth++;
            T result = work.get();
            txDepth--;
            if (outer) {
                conn.commit();
            }
            return result;
        } catch (RuntimeException e) {
            txDepth--;
            if (outer) {
                rollbackQuietly();
            }
            throw e;
        } catch (SQLException e) {
            txDepth--;
            if (outer) {
                rollbackQuietly();
            }
            throw translate(e);
        } finally {
            if (outer) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // connection is unusable anyway
                }
                txDepth = 0;
            }
        }
    }

    public void txRun(Runnable work) {
        tx(() -> {
            work.run();
            return null;
        });
    }

    public boolean inTransaction() {
        return txDepth > 0;
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // nothing more we can do
        }
    }

    private PreparedStatement prepare(String sql, boolean keys, Object... params) throws SQLException {
        PreparedStatement ps = keys ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            bind(ps, i + 1, params[i]);
        }
        return ps;
    }

    private static void bind(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NULL);
        } else if (value instanceof Long) {
            ps.setLong(index, (Long) value);
        } else if (value instanceof Integer) {
            ps.setInt(index, (Integer) value);
        } else if (value instanceof Boolean) {
            ps.setInt(index, ((Boolean) value) ? 1 : 0);
        } else if (value instanceof BigDecimal) {
            ps.setString(index, ((BigDecimal) value).toPlainString());
        } else if (value instanceof byte[]) {
            ps.setBytes(index, (byte[]) value);
        } else if (value instanceof Enum<?>) {
            ps.setString(index, ((Enum<?>) value).name());
        } else {
            ps.setString(index, value.toString());
        }
    }

    /** Turns low-level SQLite errors into readable messages where we can. */
    static RuntimeException translate(SQLException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("UNIQUE constraint failed: product_variants.barcode")) {
            return new AppException("This barcode is already registered to another product.", e);
        }
        if (msg.contains("UNIQUE constraint failed")) {
            return new AppException("This value already exists and must be unique (" + afterColon(msg) + ").", e);
        }
        if (msg.contains("CHECK constraint failed") && msg.contains("stock_qty")) {
            return new AppException("Not enough stock for this operation.", e);
        }
        if (msg.contains("CHECK constraint failed")) {
            return new AppException("The data is not valid (" + afterColon(msg) + ").", e);
        }
        if (msg.contains("FOREIGN KEY constraint failed")) {
            return new AppException("This record is still used elsewhere and cannot be removed.", e);
        }
        int marker = msg.indexOf(Migrations.PROTECTED);
        if (marker >= 0) {
            String text = msg.substring(marker + Migrations.PROTECTED.length());
            int end = text.lastIndexOf(')');
            if (end >= 0) {
                text = text.substring(0, end);
            }
            text = text.trim();
            return new AppException(text.isEmpty() ? "This record is protected and cannot be changed." : text, e);
        }
        if (msg.contains("SQLITE_BUSY") || msg.contains("database is locked")) {
            return new AppException("The database is busy. Please try again in a moment.", e);
        }
        return new DataException("Database error: " + msg, e);
    }

    private static String afterColon(String msg) {
        int idx = msg.lastIndexOf(':');
        String s = idx >= 0 ? msg.substring(idx + 1) : msg;
        return s.replace(")", "").trim();
    }
}
