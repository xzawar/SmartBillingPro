package com.smartbillingpro.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Remembers the last signed-in username so the "Remember me" option can skip the
 * login screen until the user explicitly logs out.
 *
 * <p>Only the username is stored (never the password) in
 * {@code ~/.smartbillingpro/session.properties}. On the next launch the app
 * looks that user up and, if the account is still active, signs them in
 * automatically. Logging out deletes this file.</p>
 */
public final class RememberMe {

    private static final String KEY = "rememberedUsername";

    private RememberMe() {
    }

    private static File file() {
        File dir = new File(System.getProperty("user.home"), ".smartbillingpro");
        dir.mkdirs();
        return new File(dir, "session.properties");
    }

    /** Store the username so the next launch auto-signs-in this account. */
    public static void remember(String username) {
        if (username == null || username.trim().isEmpty()) {
            clear();
            return;
        }
        Properties p = new Properties();
        p.setProperty(KEY, username.trim());
        try (FileOutputStream out = new FileOutputStream(file())) {
            p.store(out, "Smart Billing Pro - remembered session");
        } catch (Exception ignored) {
            // Remembering is only a convenience; ignore write failures.
        }
    }

    /** Forget the remembered user (called on logout). */
    public static void clear() {
        File f = file();
        if (f.exists()) {
            f.delete();
        }
    }

    /** The remembered username, or {@code null} when none is set. */
    public static String get() {
        File f = file();
        if (!f.exists()) {
            return null;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            return null;
        }
        String u = p.getProperty(KEY);
        return (u == null || u.trim().isEmpty()) ? null : u.trim();
    }
}
