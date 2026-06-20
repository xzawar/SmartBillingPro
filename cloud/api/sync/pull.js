// GET /api/sync/pull?since=<ISO timestamp>
// Returns every row across all synced tables for the caller's store that changed
// after `since` (including soft-deleted rows so deletions propagate). The admin
// can pass &store=<id> to pull a specific store. Drives the "download the whole
// store locally on first login + keep pulling every few seconds" behaviour.
const { supabase, requireAuth, handledPreflight, SYNC_TABLES } = require("../_lib");

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res);
  if (!tok) return;

  const since = (req.query.since && String(req.query.since)) || "1970-01-01T00:00:00Z";
  const storeId = tok.role === "ADMIN" ? (req.query.store || null) : tok.store_id;
  if (!storeId && tok.role !== "ADMIN") return res.status(400).json({ error: "No store on this account." });

  const out = {};
  let serverTime = new Date().toISOString();
  for (const table of SYNC_TABLES) {
    let q = supabase.from(table).select("*").gt("updated_at", since).order("updated_at", { ascending: true });
    // Stores table is keyed by id; everything else by store_id.
    if (storeId) {
      q = table === "stores" ? q.eq("id", storeId) : q.eq("store_id", storeId);
    }
    const { data, error } = await q;
    if (error) return res.status(500).json({ error: `${table}: ${error.message}` });
    out[table] = data || [];
  }
  return res.status(200).json({ serverTime, since, tables: out });
};
