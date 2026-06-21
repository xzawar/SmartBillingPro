// Admin-only Recycle Bin.
//   GET  /api/admin/trash
//        -> first auto-purges anything soft-deleted more than 24h ago, then
//           returns the remaining deleted stores and accounts.
//   POST /api/admin/trash { action, kind, id }
//        action: restore  kind: "store" | "account"  -> bring it back
//        action: purge    kind: "store" | "account"  -> delete it permanently
//
// Deleting a store also soft-deletes its accounts (see admin/stores.js), and
// purging a store removes the store plus every row that belongs to it.
const { supabase, requireAuth, readBody, handledPreflight, SYNC_TABLES } = require("../_lib");

const DAY_MS = 24 * 60 * 60 * 1000;

// Permanently remove a store and all of its data.
async function purgeStore(id) {
  for (const table of SYNC_TABLES) {
    if (table === "stores") continue;
    await supabase.from(table).delete().eq("store_id", id);
  }
  await supabase.from("stores").delete().eq("id", id);
}

// Hard-delete everything that has been sitting in the bin for over 24 hours.
async function autoPurge() {
  const cutoff = new Date(Date.now() - DAY_MS).toISOString();
  const { data: oldStores } = await supabase
    .from("stores").select("id").eq("deleted", true).lt("deleted_at", cutoff);
  for (const s of oldStores || []) await purgeStore(s.id);
  // Remaining stale accounts (whose store was not purged above).
  await supabase.from("users").delete().eq("deleted", true).lt("deleted_at", cutoff);
}

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res, ["ADMIN"]);
  if (!tok) return;

  if (req.method === "GET") {
    await autoPurge();
    const { data: stores } = await supabase
      .from("stores").select("id,name,phone,deleted_at").eq("deleted", true).order("deleted_at", { ascending: false });
    const { data: accounts } = await supabase
      .from("users").select("id,username,full_name,role,store_id,deleted_at").eq("deleted", true).order("deleted_at", { ascending: false });
    return res.status(200).json({ stores: stores || [], accounts: accounts || [] });
  }

  if (req.method === "POST") {
    const body = await readBody(req);
    const { action, kind, id } = body;
    if (!id) return res.status(400).json({ error: "id is required." });
    const now = new Date().toISOString();

    if (action === "restore") {
      if (kind === "store") {
        const { error: e1 } = await supabase.from("stores")
          .update({ deleted: false, deleted_at: null, updated_at: now }).eq("id", id);
        if (e1) return res.status(500).json({ error: e1.message });
        // Bring back the accounts that were removed together with the store.
        await supabase.from("users")
          .update({ deleted: false, deleted_at: null, updated_at: now }).eq("store_id", id);
        return res.status(200).json({ ok: true });
      }
      const { error } = await supabase.from("users")
        .update({ deleted: false, deleted_at: null, updated_at: now }).eq("id", id);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true });
    }

    if (action === "purge") {
      if (kind === "store") {
        await purgeStore(id);
        return res.status(200).json({ ok: true });
      }
      const { error } = await supabase.from("users").delete().eq("id", id);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true });
    }
    return res.status(400).json({ error: "Unknown action." });
  }
  return res.status(405).json({ error: "Method not allowed." });
};
