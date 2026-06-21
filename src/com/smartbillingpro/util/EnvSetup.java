package com.smartbillingpro.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes sure the {@code SMARTBILLING_CLOUD_API} environment variable exists on
 * the computer, automatically, every time the app is opened.
 *
 * <p>A process can only change environment variables for itself and the child
 * processes it launches - it cannot rewrite the parent terminal's live session.
 * So "adding it to the computer" means <b>persisting</b> the variable so every
 * future program (and the next launch of this app) can see it:</p>
 * <ul>
 *   <li><b>Windows</b> - runs {@code setx KEY VALUE}, which stores it in the
 *       current user's environment for all new processes.</li>
 *   <li><b>macOS / Linux</b> - appends an {@code export KEY=VALUE} line to the
 *       user's shell profile files ({@code ~/.profile}, {@code ~/.bashrc},
 *       {@code ~/.zshrc}) when it is not already there.</li>
 * </ul>
 *
 * <p>It also sets the value for the running JVM so the rest of this launch can
 * read it immediately. Everything is best-effort: any failure is ignored so it
 * can never stop the app from starting.</p>
 */
public final class EnvSetup {

    /** The environment variable the app wants on every machine. */
    public static final String KEY = "SMARTBILLING_CLOUD_API";

    /** The deployed Vercel API the variable should point at. */
    public static final String VALUE = "https://smartbillingpro.vercel.app";

    private EnvSetup() {
    }

    /**
     * Ensures the variable is present. Safe to call on every startup: it does
     * nothing when the value is already set correctly.
     */
    public static void ensure() {
        try {
            // Always expose it to the current JVM so this run can use it right away.
            System.setProperty(KEY, VALUE);

            String existing = System.getenv(KEY);
            if (existing != null && existing.trim().equals(VALUE)) {
                return; // already in this environment - nothing to do
            }

            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                persistWindows();
            } else {
                persistUnix();
            }
        } catch (Throwable ignored) {
            // Best effort only - never block startup.
        }
    }

    /** Windows: {@code setx} writes the variable to the user environment. */
    private static void persistWindows() {
        try {
            new ProcessBuilder("cmd", "/c", "setx", KEY, VALUE)
                    .redirectErrorStream(true)
                    .redirectOutput(new File("NUL"))
                    .start();
        } catch (Throwable ignored) {
            // best effort
        }
    }

    /**
     * macOS / Linux: append an export line to the common shell profiles so new
     * shells pick the variable up. Skips any file that already mentions the key.
     */
    private static void persistUnix() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            return;
        }
        String line = "export " + KEY + "=\"" + VALUE + "\"";
        String block = System.lineSeparator()
                + "# Added automatically by Smart Billing Pro" + System.lineSeparator()
                + line + System.lineSeparator();

        List<String> profiles = new ArrayList<>();
        profiles.add(".profile");
        profiles.add(".bashrc");
        profiles.add(".zshrc");

        for (String name : profiles) {
            try {
                Path path = new File(home, name).toPath();
                if (Files.exists(path)) {
                    String current = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    if (current.contains(KEY)) {
                        continue; // already configured in this file
                    }
                    Files.write(path,
                            (current.endsWith("\n") ? block : System.lineSeparator() + block)
                                    .getBytes(StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.APPEND);
                } else if (name.equals(".profile")) {
                    // Create ~/.profile so there is at least one place that defines it.
                    Files.write(path, block.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {
                // best effort per file
            }
        }
    }
}
