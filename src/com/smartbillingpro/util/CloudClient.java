package com.smartbillingpro.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal HTTP/JSON client for the cloud API, built on {@link HttpURLConnection}
 * so it needs no external libraries. Every call returns the parsed JSON object.
 */
public final class CloudClient {

    /** Thrown for any non-2xx response; carries the server's error message. */
    public static class CloudException extends RuntimeException {
        private final int status;

        public CloudException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    private final String base;

    public CloudClient(String base) {
        this.base = base;
    }

    public Map<String, Object> get(String path, String token) {
        return request("GET", path, null, token);
    }

    public Map<String, Object> post(String path, Object body, String token) {
        return request("POST", path, body, token);
    }

    private Map<String, Object> request(String method, String path, Object body, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(base + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }
            int status = conn.getResponseCode();
            String text = readStream(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            Map<String, Object> json = Json.parseObject(text);
            if (status >= 400) {
                Object err = json.get("error");
                throw new CloudException(status, err != null ? err.toString() : ("Request failed (" + status + ")"));
            }
            return json;
        } catch (CloudException e) {
            throw e;
        } catch (IOException e) {
            throw new CloudException(0, "Network error: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Quick connectivity probe used to decide online/offline behaviour. */
    public boolean ping() {
        try {
            get("/api/me", CloudConfig.token());
            return true;
        } catch (CloudException e) {
            // Any HTTP answer (even 401) means we reached the server.
            return e.getStatus() != 0;
        }
    }
}
