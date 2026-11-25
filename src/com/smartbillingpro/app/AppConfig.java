package com.smartbillingpro.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Machine-local preferences kept outside the business database (backup folder
 * and backup status). Keeping them out of the database means restoring an older
 * backup never resets "last backup" information or the backup location.
 */
public final class AppConfig {

    public static final String BACKUP_DIR = "backup.dir";
    public static final String BACKUP_AUTO = "backup.auto.enabled";
    public static final String BACKUP_KEEP = "backup.auto.keep";
    public static final String BACKUP_LAST_TIME = "backup.last.time";
    public static final String BACKUP_LAST_FILE = "backup.last.file";
    public static final String BACKUP_LAST_STATUS = "backup.last.status";
    public static final String BACKUP_LAST_ERROR = "backup.last.error";
    public static final String BACKUP_LAST_AUTO_DATE = "backup.last.auto.date";
    public static final String LEGACY_IMPORT_ASKED = "legacy.import.asked";

    private final Path file;
    private final Properties props = new Properties();

    public AppConfig(Path file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            // A damaged preferences file is not fatal: fall back to defaults.
            props.clear();
        }
    }

    public synchronized String get(String key, String fallback) {
        String v = props.getProperty(key);
        return v == null ? fallback : v;
    }

    public synchronized boolean getBool(String key, boolean fallback) {
        String v = props.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }

    public synchronized int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public synchronized void set(String key, String value) {
        if (value == null) {
            props.remove(key);
        } else {
            props.setProperty(key, value);
        }
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "Smart Billing Pro - local preferences");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                try (OutputStream out = Files.newOutputStream(file)) {
                    props.store(out, "Smart Billing Pro - local preferences");
                }
            } catch (IOException ignored) {
                // preferences are best effort
            }
        }
    }
}
