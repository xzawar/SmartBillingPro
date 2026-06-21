// Account management from the web.
//   GET  /api/accounts                              -> list accounts the caller may see
//   POST /api/accounts { action, ... }
//        action: create  { username, password, fullName, role, storeId? }
//        action: disable { id }
//        action: enable  { id }
//        action: reset   { id, password }
//
// Who can do what:
//   ADMIN  -> any account in any store, can also create OWNER accounts
//   OWNER  -> manager/cashier accounts in their own store only
//   others -> not allowed
const { supabase, sha256, uuid, requireAuth, readBody, handledPreflight } = require("./_lib");

function canManage(actor, targetStoreId, targetRole) {
  if (actor.role === "ADMIN") return true;
  if (actor.role === "OWNER") {
    return targetStoreId === actor.store_id && (targetRole === "MANAGER" || targetRole === "CASHIER");
  }
  return false;
}

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res, ["ADMIN", "OWNER"]);
  if (!tok) return;

  if (req.method === "GET") {
    let q = supabase.from("users").select("id,username,full_name,role,store_id,active").eq("deleted", false);
    if (tok.role === "OWNER") q = q.eq("store_id", tok.store_id);
    // Never show the administrator account in a non-admin's account list.
    if (tok.role !== "ADMIN") q = q.neq("role", "ADMIN");
    const { data, error } = await q.order("role");
    if (error) return res.status(500).json({ error: error.message });
    return res.status(200).json({ accounts: data || [] });
  }

  if (req.method === "POST") {
    const body = await readBody(req);
    const action = body.action;

    if (action === "create") {
      const role = (body.role || "CASHIER").toUpperCase();
      const storeId = tok.role === "ADMIN" ? (body.storeId || null) : tok.store_id;
      if (role === "ADMIN") return res.status(403).json({ error: "Cannot create admin accounts here." });
      if (!canManage(tok, storeId, role)) return res.status(403).json({ error: "Not allowed for your role." });
      if (!body.username || !body.password) return res.status(400).json({ error: "Username and password required." });
      const { data: dup } = await supabase.from("users").select("id").eq("username", String(body.username).trim()).maybeSingle();
      if (dup) return res.status(409).json({ error: "That username is already taken." });
      const { error } = await supabase.from("users").insert({
        id: uuid(), local_id: 0, store_id: storeId, username: String(body.username).trim(),
        password_hash: sha256(body.password), full_name: body.fullName || body.username,
        role, active: true, deleted: false, updated_at: new Date().toISOString(),
      });
      if (error) return res.status(500).json({ error: error.message });
      return res.status(201).json({ ok: true });
    }

    // For enable/disable/reset, load the target first to check permissions.
    const { data: target } = await supabase.from("users").select("*").eq("id", body.id).maybeSingle();
    if (!target) return res.status(404).json({ error: "Account not found." });
    if (!canManage(tok, target.store_id, target.role)) return res.status(403).json({ error: "Not allowed for your role." });

    if (action === "disable" || action === "enable") {
      const { error } = await supabase.from("users").update({ active: action === "enable", updated_at: new Date().toISOString() }).eq("id", body.id);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true, active: action === "enable" });
    }
    if (action === "reset") {
      if (!body.password) return res.status(400).json({ error: "New password required." });
      const { error } = await supabase.from("users").update({ password_hash: sha256(body.password), updated_at: new Date().toISOString() }).eq("id", body.id);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true });
    }
    if (action === "delete") {
      // Soft delete: the account drops into the Recycle Bin for 24 hours, then a
      // scheduled purge (or a manual "delete permanently") removes it for good.
      if (target.role === "ADMIN") return res.status(403).json({ error: "The administrator account cannot be deleted." });
      const { error } = await supabase.from("users")
        .update({ deleted: true, deleted_at: new Date().toISOString(), active: false, updated_at: new Date().toISOString() })
        .eq("id", body.id);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true });
    }
    return res.status(400).json({ error: "Unknown action." });
  }
  return res.status(405).json({ error: "Method not allowed." });
};
