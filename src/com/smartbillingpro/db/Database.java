package com.smartbillingpro.db;

import com.smartbillingpro.util.Dates;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Opens the local SQLite database file, applies safe pragmas and brings the
 * schema up to date. There is no network involved anywhere.
 */
public final class Database implements AutoCloseable {

    private final Path file;
    private final Path safetyDir;
    private Connection connection;
    private Db db;

    /**
     * @param file      the database file (created when missing)
     * @param safetyDir where an automatic copy is written before a schema upgrade
     */
    public Database(Path file, Path safetyDir) {
        this.file = file.toAbsolutePath();
        this.safetyDir = safetyDir;
    }

    public Path file() {
        return file;
    }

    public Db db() {
        return db;
    }

    public void open() {
        try {
            Files.createDirectories(file.getParent());
            connection = openConnection(file);
            db = new Db(connection);
            migrate();
        } catch (SQLException e) {
            throw new DataException("Could not open the database at " + file + ": " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new DataException("Could not create the data folder " + file.getParent() + ": " + e.getMessage(), e);
        }
    }

    /** A configured connection to any Smart Billing Pro database file. */
    public static Connection openConnection(Path path) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("The SQLite driver (sqlite-jdbc) is missing from the lib folder.", e);
        }
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 8000");
            st.execute("PRAGMA journal_mode = WAL");
            // FULL makes every committed sale durable even on a sudden power cut.
            st.execute("PRAGMA synchronous = FULL");
        }
        return c;
    }

    public int schemaVersion() {
        return (int) db.scalar("PRAGMA user_version");
    }

    private void migrate() throws SQLException {
        int version = schemaVersion();
        if (version > Migrations.CURRENT_VERSION) {
            throw new AppException("This data was created by a newer version of Smart Billing Pro (schema "
                    + version + "). Please install the latest version.");
        }
        if (version > 0) {
            String appId = db.scalarText("SELECT value FROM app_meta WHERE key = 'app_id'");
            if (!"SmartBillingPro-Offline".equals(appId)) {
                throw new AppException("The file " + file + " is not a Smart Billing Pro database.");
            }
        }
        if (version > 0 && version < Migrations.CURRENT_VERSION) {
            safetyCopy("before-upgrade-v" + version);
        }
        for (int v = version + 1; v <= Migrations.CURRENT_VERSION; v++) {
            final int target = v;
            List<String> statements = Migrations.forVersion(target);
            db.txRun(() -> {
                for (String sql : statements) {
                    db.exec(sql);
                }
                db.exec("PRAGMA user_version = " + target);
                db.update("INSERT OR REPLACE INTO app_meta (key, value) VALUES ('schema_version', ?)", String.valueOf(target));
            });
        }
    }

    /** Consistent copy of the live database (used before upgrades). */
    private void safetyCopy(String label) {
        try {
            Files.createDirectories(safetyDir);
            Path target = safetyDir.resolve("SBP-" + label + "_" + LocalDateTime.now().format(Dates.FILE_STAMP) + ".db");
            db.exec("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
        } catch (java.io.IOException | RuntimeException e) {
            throw new AppException("Could not make a safety copy before upgrading the database: " + e.getMessage(), e);
        }
    }

    /** Write a consistent snapshot of the live database to {@code target}. */
    public void snapshotTo(Path target) {
        db.exec("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
    }

    /** Fold the WAL into the main file so the database is a single self-contained file. */
    public void checkpoint() {
        try {
            db.exec("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (RuntimeException ignored) {
            // not critical
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            checkpoint();
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best effort
            }
            connection = null;
        }
    }
}
