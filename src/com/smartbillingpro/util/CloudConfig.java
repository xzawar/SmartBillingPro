package com.smartbillingpro.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Holds the cloud connection details and the signed-in token.
 *
 * <p>The base URL of the deployed Vercel API comes from the
 * {@code SMARTBILLING_CLOUD_API} environment variable (for example
 * {@code https://your-app.vercel.app}). If it is not set, cloud sync is simply
 * turned off and the app runs fully offline exactly as before.</p>
 *
 * <p>The login token, the current store id and the last successful pull time are
 * cached in {@code ~/.smartbillingpro/cloud.properties} so a device that has
 * already signed in can keep syncing (and work offline) without logging in to
 * the web every time.</p>
 */
public final class CloudConfig {

    private static final String FILE_NAME = "cloud.properties";

    private CloudConfig() {
    }

    /** The deployed API base URL, or null when cloud sync is not configured. */
    public static String apiBase() {
        String v = System.getenv("SMARTBILLING_CLOUD_API");
        if (v == null || v.trim().isEmpty()) {
            // Allow a saved value too, so non-technical users can set it once in-app.
            return get("apiBase");
        }
        String base = v.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static boolean isConfigured() {
        String base = apiBase();
        return base != null && !base.isEmpty();
    }

    private static File configFile() {
        File dir = new File(System.getProperty("user.home"), ".smartbillingpro");
        dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    private static Properties load() {
        Properties p = new Properties();
        File f = configFile();
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (Exception ignored) {
                // start fresh on any read error
            }
        }
        return p;
    }

    private static void save(Properties p) {
        try (FileOutputStream out = new FileOutputStream(configFile())) {
            p.store(out, "Smart Billing Pro cloud session");
        } catch (Exception ignored) {
            // best effort
        }
    }

    public static String get(String key) {
        return load().getProperty(key);
    }

    public static void set(String key, String value) {
        Properties p = load();
        if (value == null) {
            p.remove(key);
        } else {
            p.setProperty(key, value);
        }
        save(p);
    }

    public static String token() {
        return get("token");
    }

    public static void saveSession(String token, String storeId) {
        Properties p = load();
        if (token != null) {
            p.setProperty("token", token);
        }
        if (storeId != null) {
            p.setProperty("storeId", storeId);
        }
        save(p);
    }

    public static String storeId() {
        return get("storeId");
    }

    /** ISO timestamp of the last successful pull (per store), used as the sync cursor. */
    public static String lastPull(String storeId) {
        String v = get("lastPull." + storeId);
        return v == null ? "1970-01-01T00:00:00Z" : v;
    }

    public static void setLastPull(String storeId, String iso) {
        set("lastPull." + storeId, iso);
    }

    public static void clearSession() {
        Properties p = load();
        p.remove("token");
        save(p);
    }
}
