package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.util.Dates;

import java.util.List;

public final class AuditRepo {
    private final Db db;

    public AuditRepo(Db db) {
        this.db = db;
    }

    public void log(String action, String details) {
        db.update("INSERT INTO audit_log (created_at, action, details) VALUES (?, ?, ?)", Dates.now(), action, details);
    }

    public List<String[]> recent(int limit) {
        return db.list("SELECT created_at, action, details FROM audit_log ORDER BY id DESC LIMIT ?",
                rs -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)}, limit);
    }
}
