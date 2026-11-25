package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;

/** Gap-free document numbers. Must be called inside the transaction that uses the number. */
public final class SequenceRepo {
    private final Db db;

    public SequenceRepo(Db db) {
        this.db = db;
    }

    public long next(String name) {
        int updated = db.update("UPDATE sequences SET value = value + 1 WHERE name = ?", name);
        if (updated == 0) {
            db.update("INSERT INTO sequences (name, value) VALUES (?, 1)", name);
            return 1;
        }
        return db.scalar("SELECT value FROM sequences WHERE name = ?", name);
    }

    public long peek(String name) {
        return db.scalar("SELECT value FROM sequences WHERE name = ?", name) + 1;
    }

    public static String format(String prefix, long number) {
        return prefix + "-" + String.format("%06d", number);
    }
}
