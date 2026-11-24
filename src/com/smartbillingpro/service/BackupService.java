package com.smartbillingpro.service;

import com.smartbillingpro.app.AppConfig;
import com.smartbillingpro.app.AppPaths;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Database;
import com.smartbillingpro.db.Migrations;
import com.smartbillingpro.util.Dates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local backups. A backup is a complete, consistent SQLite snapshot made with
 * {@code VACUUM INTO} on a separate connection, so it never blocks or corrupts
 * the live database. Each backup is integrity-checked right after it is written
 * and gets a SHA-256 checksum file next to it.
 */
public final class BackupService {

    public enum Kind {
        AUTO, MANUAL, SAFETY
    }

    public record BackupInfo(Path file, LocalDateTime created, Kind kind, long sizeBytes) {
    }

    public record Validation(boolean ok, String message, int schemaVersion, long products, long sales,
                             long customers, String lastSale) {
    }

    private static final Pattern NAME = Pattern.compile(
            "SmartBillingPro_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?:-\\d+)?_(auto|manual|safety)\\.db");
    private static final String[] REQUIRED_TABLES = {"app_meta", "settings", "products", "product_variants", "sales",
        "sale_items", "customers", "suppliers", "ledger_entries", "payments", "stock_movements", "purchases"};

    private final Path databaseFile;
    private final AppPaths paths;
    private final AppConfig config;

    public BackupService(Path databaseFile, AppPaths paths, AppConfig config) {
        this.databaseFile = databaseFile;
        this.paths = paths;
        this.config = config;
    }

    // ------------------------------------------------------------ settings

    public Path backupDir() {
        String dir = config.get(AppConfig.BACKUP_DIR, "");
        return dir.isBlank() ? paths.defaultBackupDir() : Paths.get(dir);
    }

    public void setBackupDir(Path dir) {
        if (dir == null) {
            config.set(AppConfig.BACKUP_DIR, null);
            return;
        }
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, "sbp-write-test", ".tmp");
            Files.delete(probe);
        } catch (IOException e) {
            throw new AppException("Backups cannot be saved in " + dir + ": " + e.getMessage());
        }
        config.set(AppConfig.BACKUP_DIR, dir.toAbsolutePath().toString());
    }

    public boolean autoEnabled() {
        return config.getBool(AppConfig.BACKUP_AUTO, true);
    }

    public void setAutoEnabled(boolean enabled) {
        config.set(AppConfig.BACKUP_AUTO, String.valueOf(enabled));
    }

    /** How many automatic backups to keep (older ones are removed). */
    public int keepCount() {
        return Math.max(3, config.getInt(AppConfig.BACKUP_KEEP, 30));
    }

    public void setKeepCount(int keep) {
        if (keep < 3 || keep > 3650) {
            throw new AppException("Keep between 3 and 3650 automatic backups.");
        }
        config.set(AppConfig.BACKUP_KEEP, String.valueOf(keep));
    }

    public String lastBackupTime() {
        return config.get(AppConfig.BACKUP_LAST_TIME, "");
    }

    public String lastStatus() {
        return config.get(AppConfig.BACKUP_LAST_STATUS, "");
    }

    public String lastError() {
        return config.get(AppConfig.BACKUP_LAST_ERROR, "");
    }

    public String lastFile() {
        return config.get(AppConfig.BACKUP_LAST_FILE, "");
    }

    // ------------------------------------------------------------ create

    public synchronized BackupInfo createBackup(Kind kind) {
        return createBackup(kind, kind == Kind.SAFETY ? paths.safetyBackupDir() : backupDir());
    }

    /** Write, verify and checksum a new backup in {@code dir}. */
    public synchronized BackupInfo createBackup(Kind kind, Path dir) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Path target = dir.resolve("SmartBillingPro_" + now.format(Dates.FILE_STAMP) + "_" + kind.name().toLowerCase() + ".db");
        Path tmp = dir.resolve(target.getFileName() + ".partial");
        try {
            Files.createDirectories(dir);
            int n = 1;
            while (Files.exists(target)) {
                target = dir.resolve("SmartBillingPro_" + now.format(Dates.FILE_STAMP) + "-" + n++ + "_"
                        + kind.name().toLowerCase() + ".db");
            }
            Files.deleteIfExists(tmp);
            if (!Files.isRegularFile(databaseFile)) {
                throw new AppException("The shop database was not found.");
            }
            try (Connection c = Database.openConnection(databaseFile); Statement st = c.createStatement()) {
                st.execute("VACUUM INTO '" + tmp.toAbsolutePath().toString().replace("'", "''") + "'");
            }
            Validation v = validate(tmp);
            if (!v.ok()) {
                throw new AppException("The new backup failed its integrity check: " + v.message());
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(checksumFile(target), sha256(target) + "  " + target.getFileName() + "\n",
                    StandardCharsets.UTF_8);
            if (kind != Kind.SAFETY) {
                config.set(AppConfig.BACKUP_LAST_TIME, Dates.stored(now));
                config.set(AppConfig.BACKUP_LAST_FILE, target.toAbsolutePath().toString());
                config.set(AppConfig.BACKUP_LAST_STATUS, "OK");
                config.set(AppConfig.BACKUP_LAST_ERROR, "");
                if (kind == Kind.AUTO) {
                    config.set(AppConfig.BACKUP_LAST_AUTO_DATE, LocalDate.now().format(Dates.ISO_DATE));
                    pruneAuto(dir);
                }
            }
            return new BackupInfo(target, now, kind, Files.size(target));
        } catch (IOException | SQLException | RuntimeException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // leave it; it is clearly named .partial
            }
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (kind != Kind.SAFETY) {
                config.set(AppConfig.BACKUP_LAST_STATUS, "FAILED");
                config.set(AppConfig.BACKUP_LAST_ERROR, Dates.now() + ": " + msg);
            }
            throw new AppException("Backup failed: " + msg, e);
        }
    }

    /** True when automatic backups are on and none has been made today. */
    public boolean autoBackupDue() {
        return autoEnabled() && !LocalDate.now().format(Dates.ISO_DATE).equals(config.get(AppConfig.BACKUP_LAST_AUTO_DATE, ""));
    }

    /** Makes today's automatic backup if it is due. Returns it, or null when not due. */
    public BackupInfo runAutoIfDue() {
        if (!autoBackupDue()) {
            return null;
        }
        return createBackup(Kind.AUTO);
    }

    private void pruneAuto(Path dir) {
        List<BackupInfo> autos = new ArrayList<>(list(dir).stream().filter(b -> b.kind() == Kind.AUTO).toList());
        autos.sort((a, b) -> b.created().compareTo(a.created()));
        for (int i = keepCount(); i < autos.size(); i++) {
            try {
                Files.deleteIfExists(autos.get(i).file());
                Files.deleteIfExists(checksumFile(autos.get(i).file()));
            } catch (IOException ignored) {
                // will retry next time
            }
        }
    }

    // ------------------------------------------------------------ list

    public List<BackupInfo> list() {
        List<BackupInfo> all = new ArrayList<>(list(backupDir()));
        all.addAll(list(paths.safetyBackupDir()));
        all.sort((a, b) -> b.created().compareTo(a.created()));
        return all;
    }

    private static List<BackupInfo> list(Path dir) {
        List<BackupInfo> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.db")) {
            for (Path p : ds) {
                Matcher m = NAME.matcher(p.getFileName().toString());
                LocalDateTime created;
                Kind kind;
                if (m.matches()) {
                    try {
                        created = LocalDateTime.parse(m.group(1), Dates.FILE_STAMP);
                    } catch (DateTimeParseException e) {
                        created = LocalDateTime.now();
                    }
                    kind = Kind.valueOf(m.group(2).toUpperCase());
                } else {
                    if (!p.getFileName().toString().startsWith("SBP-") && !p.getFileName().toString().startsWith("SmartBillingPro")) {
                        continue;
                    }
                    created = LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), java.time.ZoneId.systemDefault());
                    kind = Kind.SAFETY;
                }
                out.add(new BackupInfo(p, created, kind, Files.size(p)));
            }
        } catch (IOException ignored) {
            // unreadable folder: nothing to list
        }
        return out;
    }

    // ------------------------------------------------------------ validate

    /** Checks that a file is an intact Smart Billing Pro database this version can open. */
    public static Validation validate(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new Validation(false, "The file does not exist.", 0, 0, 0, 0, null);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(16);
            if (!new String(header, StandardCharsets.US_ASCII).startsWith("SQLite format 3")) {
                return new Validation(false, "This is not a Smart Billing Pro backup file.", 0, 0, 0, 0, null);
            }
        } catch (IOException e) {
            return new Validation(false, "The file cannot be read: " + e.getMessage(), 0, 0, 0, 0, null);
        }
        Path sum = checksumFile(file);
        if (Files.isRegularFile(sum)) {
            try {
                String expected = Files.readString(sum, StandardCharsets.UTF_8).trim().split("\\s+")[0];
                if (!expected.equalsIgnoreCase(sha256(file))) {
                    return new Validation(false, "The backup file has been changed or damaged (checksum mismatch).",
                            0, 0, 0, 0, null);
                }
            } catch (IOException e) {
                return new Validation(false, "The checksum file cannot be read.", 0, 0, 0, 0, null);
            }
        }
        Properties props = new Properties();
        props.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath(), props);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                String result = rs.next() ? rs.getString(1) : "no result";
                if (!"ok".equalsIgnoreCase(result)) {
                    return new Validation(false, "The database inside the backup is damaged: " + result, 0, 0, 0, 0, null);
                }
            }
            int version;
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                version = rs.next() ? rs.getInt(1) : 0;
            }
            for (String table : REQUIRED_TABLES) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '"
                        + table + "'")) {
                    if (!rs.next() || rs.getInt(1) == 0) {
                        return new Validation(false, "This is not a Smart Billing Pro backup (missing " + table + ").",
                                version, 0, 0, 0, null);
                    }
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT value FROM app_meta WHERE key = 'app_id'")) {
                if (!rs.next() || !AppPaths.APP_ID.equals(rs.getString(1))) {
                    return new Validation(false, "This is not a Smart Billing Pro backup.", version, 0, 0, 0, null);
                }
            }
            if (version < 1 || version > Migrations.CURRENT_VERSION) {
                return new Validation(false, "This backup was made by a newer version of the app (schema " + version + ").",
                        version, 0, 0, 0, null);
            }
            long products = count(st, "SELECT COUNT(*) FROM products");
            long sales = count(st, "SELECT COUNT(*) FROM sales");
            long customers = count(st, "SELECT COUNT(*) FROM customers");
            String last;
            try (ResultSet rs = st.executeQuery("SELECT MAX(created_at) FROM sales")) {
                last = rs.next() ? rs.getString(1) : null;
            }
            return new Validation(true, "Backup is valid.", version, products, sales, customers, last);
        } catch (SQLException e) {
            return new Validation(false, "The backup cannot be opened: " + e.getMessage(), 0, 0, 0, 0, null);
        }
    }

    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // ------------------------------------------------------------ restore

    /**
     * Replace the database file with {@code backup}. The live database must be
     * CLOSED before calling this. The previous file is kept as {@code *.before-restore}
     * until the caller confirms the restored database opens.
     */
    public void replaceDatabaseFile(Path backup) {
        Validation v = validate(backup);
        if (!v.ok()) {
            throw new AppException(v.message());
        }
        try {
            Path dir = databaseFile.getParent();
            Files.createDirectories(dir);
            Path incoming = dir.resolve(databaseFile.getFileName() + ".incoming");
            Files.copy(backup, incoming, StandardCopyOption.REPLACE_EXISTING);
            if (!validate(incoming).ok()) {
                Files.deleteIfExists(incoming);
                throw new AppException("The backup could not be copied correctly. Nothing was changed.");
            }
            Path previous = dir.resolve(databaseFile.getFileName() + ".before-restore");
            if (Files.exists(databaseFile)) {
                Files.move(databaseFile, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(dir.resolve(databaseFile.getFileName() + "-wal"));
            Files.deleteIfExists(dir.resolve(databaseFile.getFileName() + "-shm"));
            Files.move(incoming, databaseFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new AppException("Restore failed while replacing the database file: " + e.getMessage(), e);
        }
    }

    /** Put the pre-restore database back (used if the restored file fails to open). */
    public void rollbackRestore() {
        Path previous = databaseFile.resolveSibling(databaseFile.getFileName() + ".before-restore");
        try {
            if (Files.exists(previous)) {
                Files.move(previous, databaseFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new AppException("Could not undo the restore: " + e.getMessage(), e);
        }
    }

    public void finishRestore() {
        try {
            Files.deleteIfExists(databaseFile.resolveSibling(databaseFile.getFileName() + ".before-restore"));
        } catch (IOException ignored) {
            // a safety backup also exists
        }
    }

    // ------------------------------------------------------------ helpers

    static Path checksumFile(Path backup) {
        return backup.resolveSibling(backup.getFileName() + ".sha256");
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
