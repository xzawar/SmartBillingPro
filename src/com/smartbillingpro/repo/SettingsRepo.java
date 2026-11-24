package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingsRepo {
    private final Db db;

    public SettingsRepo(Db db) {
        this.db = db;
    }

    public Map<String, String> loadAll() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] kv : db.list("SELECT key, value FROM settings", rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            map.put(kv[0], kv[1]);
        }
        return map;
    }

    public void put(String key, String value) {
        db.update("INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                key, value == null ? "" : value);
    }
}
