package com.smartbillingpro.app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where the application keeps its files. Everything is local to this computer.
 *
 * <ul>
 *   <li>Windows: {@code %LOCALAPPDATA%\SmartBillingPro}</li>
 *   <li>macOS / Linux: {@code ~/.smartbillingpro-pos}</li>
 * </ul>
 * Override with the {@code sbp.dataDir} system property or the
 * {@code SMARTBILLING_DATA_DIR} environment variable (used by tests and for
 * portable installs, e.g. running from a USB drive).
 */
public final class AppPaths {

    public static final String APP_NAME = "Smart Billing Pro";
    public static final String APP_ID = "SmartBillingPro-Offline";
    public static final String VERSION = "4.0.0";

    private final Path dataDir;

    public AppPaths(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath();
    }

    public static AppPaths detect() {
        String override = System.getProperty("sbp.dataDir");
        if (override == null || override.isBlank()) {
            override = System.getenv("SMARTBILLING_DATA_DIR");
        }
        if (override != null && !override.isBlank()) {
            return new AppPaths(Paths.get(override.trim()));
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) {
                return new AppPaths(Paths.get(local, "SmartBillingPro"));
            }
        }
        return new AppPaths(Paths.get(System.getProperty("user.home"), ".smartbillingpro-pos"));
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path databaseFile() {
        return dataDir.resolve("data").resolve("smartbilling.db");
    }

    public Path configFile() {
        return dataDir.resolve("config.properties");
    }

    public Path lockFile() {
        return dataDir.resolve("app.lock");
    }

    public Path logDir() {
        return dataDir.resolve("logs");
    }

    /** Safety copies made automatically before a restore or a schema upgrade. */
    public Path safetyBackupDir() {
        return dataDir.resolve("safety-backups");
    }

    /**
     * Default backup folder: "Documents\SmartBillingPro Backups" so the owner can
     * find (and copy) the files easily; falls back to the data folder.
     */
    public Path defaultBackupDir() {
        if (System.getProperty("sbp.dataDir") != null || System.getenv("SMARTBILLING_DATA_DIR") != null) {
            return dataDir.resolve("backups");
        }
        Path docs = Paths.get(System.getProperty("user.home"), "Documents");
        if (Files.isDirectory(docs)) {
            return docs.resolve("SmartBillingPro Backups");
        }
        return dataDir.resolve("backups");
    }

    public Path defaultExportDir() {
        Path docs = Paths.get(System.getProperty("user.home"), "Documents");
        return Files.isDirectory(docs) ? docs.resolve("SmartBillingPro Exports") : dataDir.resolve("exports");
    }

    /** The database used by the previous (online) version, if any - for one-time import. */
    public static File legacyDatabase() {
        String configured = System.getenv("SMARTBILLING_DB");
        if (configured != null && !configured.isBlank()) {
            return new File(configured.trim());
        }
        File dir = new File(System.getProperty("user.home"), ".smartbillingpro");
        File current = new File(dir, "shoppro.db");
        if (current.isFile()) {
            return current;
        }
        return new File(dir, "shop.db");
    }
}
