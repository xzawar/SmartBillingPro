// Admin-only store management.
//   GET  /api/admin/stores                         -> list all stores (with pending flags)
//   POST /api/admin/stores { id, action }           -> action: enable|disable|activate
// "activate" turns on a newly-registered store AND activates its owner account.
// "disable" switches the whole store off, which blocks every account of that
// store from logging in (app and web).
const { supabase, requireAuth, handledPreflight } = require("../_lib");

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res, ["ADMIN"]);
  if (!tok) return;

  if (req.method === "GET") {
    const { data, error } = await supabase
      .from("stores").select("*").eq("deleted", false).order("created_at", { ascending: false });
    if (error) return res.status(500).json({ error: error.message });
    return res.status(200).json({ stores: data || [] });
  }

  if (req.method === "POST") {
    const { id, action } = req.body || (await require("../_lib").readBody(req));
    if (!id || !action) return res.status(400).json({ error: "id and action are required." });

    if (action === "enable" || action === "disable") {
      const { error } = await supabase.from("stores").update({ enabled: action === "enable" }).eq("id", id);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true, enabled: action === "enable" });
    }

    if (action === "activate") {
      const { error: e1 } = await supabase.from("stores")
        .update({ activated: true, owner_pending: false, enabled: true }).eq("id", id);
      if (e1) return res.status(500).json({ error: e1.message });
      // Turn on the owner account(s) for this store.
      const { error: e2 } = await supabase.from("users")
        .update({ active: true }).eq("store_id", id).eq("role", "OWNER");
      if (e2) return res.status(500).json({ error: e2.message });
      return res.status(200).json({ ok: true, activated: true });
    }
    return res.status(400).json({ error: "Unknown action." });
  }
  return res.status(405).json({ error: "Method not allowed." });
};
