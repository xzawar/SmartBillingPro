// POST /api/sync/push  { tables: { products: [ {row}, ... ], invoices: [...], ... } }
// Upserts the rows the desktop app changed since its last push. Conflict rule is
// LAST-WRITE-WINS by updated_at: an incoming row is only written if it is newer
// than the row already in the cloud (or the cloud has no such row yet).
const { supabase, requireAuth, readBody, handledPreflight, SYNC_TABLES } = require("../_lib");

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ error: "Use POST." });
  const tok = requireAuth(req, res);
  if (!tok) return;

  // Cashiers/managers may push their store's sales & data; owners too. Admin any.
  const body = await readBody(req);
  const tables = body.tables || {};
  const storeScope = tok.role === "ADMIN" ? null : tok.store_id;

  let applied = 0, skipped = 0;
  for (const table of SYNC_TABLES) {
    const rows = tables[table];
    if (!Array.isArray(rows) || rows.length === 0) continue;

    // Look up existing updated_at for these ids to enforce last-write-wins.
    const ids = rows.map((r) => r.id).filter(Boolean);
    const existing = {};
    if (ids.length) {
      const { data } = await supabase.from(table).select("id,updated_at").in("id", ids);
      (data || []).forEach((r) => (existing[r.id] = r.updated_at));
    }

    const toWrite = [];
    for (const row of rows) {
      if (!row.id) { skipped++; continue; }
      // Force the row into the caller's store (except admin) so nobody can write
      // into another store.
      if (storeScope && table !== "stores") row.store_id = storeScope;
      const prev = existing[row.id];
      if (prev && new Date(prev) >= new Date(row.updated_at || 0)) { skipped++; continue; }
      toWrite.push(row);
    }
    if (toWrite.length) {
      const { error } = await supabase.from(table).upsert(toWrite, { onConflict: "id" });
      if (error) return res.status(500).json({ error: `${table}: ${error.message}` });
      applied += toWrite.length;
    }
  }
  return res.status(200).json({ applied, skipped, serverTime: new Date().toISOString() });
};
