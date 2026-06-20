package com.smartbillingpro.service;

import com.smartbillingpro.model.User;
import com.smartbillingpro.util.CloudClient;
import com.smartbillingpro.util.CloudConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Online account actions used by the login screen:
 *
 * <ul>
 *   <li>{@link #registerStore} - a brand-new shop registers itself in the cloud;
 *       the store and its owner account stay disabled until the administrator
 *       activates them from the web console.</li>
 *   <li>{@link #signInOnline} - on a new device the user signs in against the
 *       cloud, which downloads that store's whole database locally, after which
 *       the app works offline and keeps syncing in the background.</li>
 * </ul>
 *
 * All of this is only available when {@code SMARTBILLING_CLOUD_API} is set.
 */
public final class CloudAccess {

    /** Local store id used on a single-store device (one shop per machine). */
    public static final int LOCAL_STORE_ID = 1;

    private CloudAccess() {
    }

    public static boolean isConfigured() {
        return CloudConfig.isConfigured();
    }

    /**
     * Register a new store + owner in the cloud straight from the desktop app,
     * then immediately sign the new owner in on this device.
     *
     * <p>The cloud creates the store as <em>not activated</em> but returns a
     * session token right away, so we download the (still-locked) store locally
     * and log the owner in. Their dashboard opens with a "waiting for activation"
     * banner and settings stay locked until an administrator activates the store
     * from the web console; the unlock then arrives automatically on the next
     * background sync.
     *
     * @return the matching local owner {@link User}, ready to open the app.
     */
    public static User registerStore(AppContext context, String storeName, String phone, String address,
            String ownerUsername, String ownerPassword, String ownerFullName) {
        CloudClient client = new CloudClient(CloudConfig.apiBase());
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("name", storeName);
        store.put("phone", phone);
        store.put("address", address);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("username", ownerUsername);
        owner.put("password", ownerPassword);
        owner.put("fullName", ownerFullName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("store", store);
        body.put("owner", owner);
        Map<String, Object> resp = client.post("/api/register-store", body, null);

        String token = str(resp.get("token"));
        String cloudStoreId = str(resp.get("storeId"));
        if (cloudStoreId == null) {
            cloudStoreId = str(resp.get("store_id"));
        }

        // Point this device at the new store BEFORE downloading so every pulled
        // row lands under the correct local store id.
        CloudConfig.saveSession(token, cloudStoreId);
        context.getStoreSession().setStoreId(LOCAL_STORE_ID);

        // Initial download: pulls the new (not-yet-activated) store, its owner
        // account and a default settings row into the local database.
        context.getCloudSync().prepareSchema();
        context.getCloudSync().syncOnce();

        // The owner account now exists locally - authenticate against it so the
        // app can open straight into the (locked) dashboard.
        return context.getAuthService().authenticate(ownerUsername, ownerPassword);
    }

    /**
     * Sign in against the cloud and download this store's data locally.
     *
     * @return the matching local {@link User} after the initial download, or
     *         {@code null} if the download did not yield a usable local account.
     */
    public static User signInOnline(AppContext context, String username, String password) {
        CloudClient client = new CloudClient(CloudConfig.apiBase());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        Map<String, Object> resp = client.post("/api/login", body, null);

        String token = str(resp.get("token"));
        String role = str(resp.get("role"));
        String cloudStoreId = null;
        Object storeObj = resp.get("store");
        if (storeObj instanceof Map) {
            cloudStoreId = str(((Map<?, ?>) storeObj).get("id"));
        }
        if (cloudStoreId == null) {
            cloudStoreId = str(resp.get("store_id"));
        }

        boolean admin = "ADMIN".equalsIgnoreCase(role);
        int localStore = admin ? 0 : LOCAL_STORE_ID;

        // Save the session and point this device at the right local store BEFORE
        // downloading, so every pulled row lands under the correct store id.
        CloudConfig.saveSession(token, cloudStoreId);
        context.getStoreSession().setStoreId(localStore);

        // Initial full download (and an immediate push of anything local).
        context.getCloudSync().prepareSchema();
        context.getCloudSync().syncOnce();

        // The account now exists locally - authenticate against the local copy.
        return context.getAuthService().authenticate(username, password);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
