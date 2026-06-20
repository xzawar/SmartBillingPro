package com.smartbillingpro.util;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;

/**
 * Uploads a store's database file to a cloud endpoint so the owner or manager
 * can view everything online.
 *
 * <p>It is intentionally backend-agnostic: point it at any HTTPS endpoint that
 * accepts the raw SQLite file - for example a small PHP script, an AWS S3
 * pre-signed URL, or a Supabase Storage upload URL. See
 * {@code CLOUD-AND-WEB-GUIDE.md} for ready-made backend examples.</p>
 *
 * <p>Configure with environment variables on each shop's computer:</p>
 * <ul>
 *   <li>{@code SMARTBILLING_CLOUD_URL} - the upload endpoint. The active store
 *       id is appended as a {@code store} query parameter so each store lands in
 *       its own slot.</li>
 *   <li>{@code SMARTBILLING_CLOUD_TOKEN} - a secret token sent as a
 *       {@code Bearer} authorization header.</li>
 * </ul>
 *
 * <p>If these are not set the app simply skips cloud sync and keeps working
 * fully offline.</p>
 */
public final class CloudSync {

    private CloudSync() {
    }

    /** True when an upload endpoint has been configured. */
    public static boolean isConfigured() {
        String url = System.getenv("SMARTBILLING_CLOUD_URL");
        return url != null && !url.trim().isEmpty();
    }

    /** A quick best-effort internet check (does not prove the endpoint is up). */
    public static boolean isOnline() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("1.1.1.1", 53), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Upload the database file for one store. Returns a short status message.
     *
     * @throws IllegalStateException when cloud sync is not configured / fails.
     */
    public static String upload(File dbFile, int storeId) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Cloud sync is not set up. Set the SMARTBILLING_CLOUD_URL (and "
                            + "SMARTBILLING_CLOUD_TOKEN) environment variables - see "
                            + "CLOUD-AND-WEB-GUIDE.md.");
        }
        if (dbFile == null || !dbFile.exists()) {
            throw new IllegalStateException("Database file not found: " + dbFile);
        }
        String base = System.getenv("SMARTBILLING_CLOUD_URL").trim();
        String token = System.getenv("SMARTBILLING_CLOUD_TOKEN");
        String target = base + (base.contains("?") ? "&" : "?") + "store=" + storeId;

        byte[] data = Files.readAllBytes(dbFile.toPath());
        URL url = new URL(target);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("X-Store-Id", String.valueOf(storeId));
        if (token != null && !token.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        try (OutputStream out = conn.getOutputStream()) {
            out.write(data);
        }
        int code = conn.getResponseCode();
        conn.disconnect();
        if (code >= 200 && code < 300) {
            return "Uploaded " + (data.length / 1024) + " KB to the cloud (store " + storeId + ").";
        }
        throw new IllegalStateException("Cloud upload failed (HTTP " + code + ").");
    }
}
