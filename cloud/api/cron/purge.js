// GET /api/cron/purge
// Scheduled clean-up (see cloud/vercel.json -> crons). Permanently removes any
// store or account that has been in the Recycle Bin for more than 24 hours.
// This runs without a user token, so it is safe to leave unauthenticated: it can
// only ever hard-delete rows that were ALREADY soft-deleted 24h+ ago. If you set
// a CRON_SECRET env var, the request must send it as ?key= or x-cron-key.
const { supabase, handledPreflight, SYNC_TABLES } = require("../_lib");

const DAY_MS = 24 * 60 * 60 * 1000;

async function purgeStore(id) {
  for (const table of SYNC_TABLES) {
    if (table === "stores") continue;
    await supabase.from(table).delete().eq("store_id", id);
  }
  await supabase.from("stores").delete().eq("id", id);
}

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;

  const secret = process.env.CRON_SECRET;
  if (secret) {
    const key = (req.query && req.query.key) || req.headers["x-cron-key"];
    if (key !== secret) return res.status(401).json({ error: "Bad cron key." });
  }

  const cutoff = new Date(Date.now() - DAY_MS).toISOString();
  const { data: oldStores } = await supabase
    .from("stores").select("id").eq("deleted", true).lt("deleted_at", cutoff);
  let purgedStores = 0;
  for (const s of oldStores || []) { await purgeStore(s.id); purgedStores++; }
  const { data: oldUsers } = await supabase
    .from("users").delete().eq("deleted", true).lt("deleted_at", cutoff).select("id");

  return res.status(200).json({
    ok: true, purgedStores, purgedAccounts: (oldUsers || []).length,
    ranAt: new Date().toISOString(),
  });
};
