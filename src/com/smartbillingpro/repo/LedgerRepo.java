package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.LedgerEntry;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.util.Dates;

import java.util.ArrayList;
import java.util.List;

/** Append-only party ledger (customers and suppliers). */
public final class LedgerRepo {
    private final Db db;

    public LedgerRepo(Db db) {
        this.db = db;
    }

    public long add(PartyType party, long partyId, String type, String reference, String description, long debit,
                    long credit, Long saleId, Long purchaseId, Long paymentId, Long returnId) {
        return db.insert("INSERT INTO ledger_entries (created_at, party_type, party_id, entry_type, reference, description, "
                + "debit, credit, sale_id, purchase_id, payment_id, return_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Dates.now(), party.name(), partyId, type, reference, description, debit, credit, saleId, purchaseId,
                paymentId, returnId);
    }

    /** Signed balance from the party's perspective (positive = outstanding). */
    public long balance(PartyType party, long partyId, String beforeExclusive) {
        String expr = party == PartyType.CUSTOMER ? "debit - credit" : "credit - debit";
        if (beforeExclusive == null) {
            return db.scalar("SELECT COALESCE(SUM(" + expr + "), 0) FROM ledger_entries WHERE party_type = ? AND party_id = ?",
                    party.name(), partyId);
        }
        return db.scalar("SELECT COALESCE(SUM(" + expr + "), 0) FROM ledger_entries WHERE party_type = ? AND party_id = ? "
                + "AND created_at < ?", party.name(), partyId, beforeExclusive);
    }

    /** Entries in a period with running balances that start from the opening balance before the period. */
    public List<LedgerEntry> statement(PartyType party, long partyId, String fromInclusive, String toExclusive) {
        long running = fromInclusive == null ? 0 : balance(party, partyId, fromInclusive);
        StringBuilder sql = new StringBuilder("SELECT * FROM ledger_entries WHERE party_type = ? AND party_id = ?");
        List<Object> p = new ArrayList<>(List.of(party.name(), partyId));
        if (fromInclusive != null) {
            sql.append(" AND created_at >= ?");
            p.add(fromInclusive);
        }
        if (toExclusive != null) {
            sql.append(" AND created_at < ?");
            p.add(toExclusive);
        }
        sql.append(" ORDER BY created_at, id");
        List<LedgerEntry> rows = new ArrayList<>();
        final long[] bal = {running};
        db.list(sql.toString(), rs -> {
            long debit = rs.getLong("debit");
            long credit = rs.getLong("credit");
            bal[0] += party == PartyType.CUSTOMER ? debit - credit : credit - debit;
            rows.add(new LedgerEntry(rs.getLong("id"), rs.getString("created_at"), party, partyId,
                    rs.getString("entry_type"), rs.getString("reference"), rs.getString("description"), debit, credit,
                    bal[0]));
            return null;
        }, p.toArray());
        return rows;
    }

    public long totalOutstanding(PartyType party) {
        String expr = party == PartyType.CUSTOMER ? "debit - credit" : "credit - debit";
        return db.scalar("SELECT COALESCE(SUM(b), 0) FROM (SELECT SUM(" + expr + ") AS b FROM ledger_entries "
                + "WHERE party_type = ? GROUP BY party_id HAVING b > 0)", party.name());
    }
}
