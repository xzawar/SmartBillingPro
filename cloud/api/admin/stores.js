// Admin-only store management.
//   GET  /api/admin/stores                          -> list all stores (with pending flags)
//   POST /api/admin/stores { action, ... }
//        action: create   { name, phone, address }  -> create a new, ready-to-use store
//        action: enable   { id }
//        action: disable  { id }
//        action: activate { id }
// "create" makes a store that is already enabled + activated (the admin made it).
// "activate" turns on a store that was registered from the app AND activates its
// owner account. "disable" switches the whole store off, which blocks every
// account of that store from signing in (app and web).
//
// Every change bumps updated_at so the desktop app pulls it within seconds.
const { supabase, uuid, requireAuth, readBody, handledPreflight } = require("../_lib");

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
    const body = await readBody(req);
    const action = body.action;
    const now = new Date().toISOString();

    // Admin creates a brand-new store straight from the web. It is immediately
    // enabled and activated, ready for accounts to be assigned to it.
    if (action === "create") {
      const name = String(body.name || "").trim();
      if (!name) return res.status(400).json({ error: "Store name is required." });
      const storeId = uuid();
      const { error: se } = await supabase.from("stores").insert({
        id: storeId, local_id: 0, name, phone: body.phone || null, address: body.address || null,
        enabled: true, activated: true, owner_pending: false, deleted: false,
        created_at: now, updated_at: now,
      });
      if (se) return res.status(500).json({ error: se.message });
      // Seed a default settings row so receipts already show the store name.
      await supabase.from("settings").insert({
        id: uuid(), store_id: storeId, business_name: name,
        address: body.address || "", phone: body.phone || "",
        invoice_prefix: "INV-", default_tax_percent: "0", currency_symbol: "Rs ",
        footer_message: "Thank you for shopping with us!", low_stock_threshold: 5,
        return_policy_days: 7, loyalty_points_per_100: 1, deleted: false, updated_at: now,
      });
      return res.status(201).json({ ok: true, id: storeId });
    }

    const id = body.id;
    if (!id) return res.status(400).json({ error: "id is required." });

    if (action === "enable" || action === "disable") {
      const { error } = await supabase.from("stores")
        .update({ enabled: action === "enable", updated_at: now }).eq("id", id);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true, enabled: action === "enable" });
    }

    if (action === "activate") {
      const { error: e1 } = await supabase.from("stores")
        .update({ activated: true, owner_pending: false, enabled: true, updated_at: now }).eq("id", id);
      if (e1) return res.status(500).json({ error: e1.message });
      // Turn on the owner account(s) for this store.
      const { error: e2 } = await supabase.from("users")
        .update({ active: true, updated_at: now }).eq("store_id", id).eq("role", "OWNER");
      if (e2) return res.status(500).json({ error: e2.message });
      return res.status(200).json({ ok: true, activated: true });
    }

    if (action === "delete") {
      // Soft delete the store AND its accounts so they all land in the Recycle
      // Bin together. A scheduled purge (or "delete permanently") clears them.
      const { error: e1 } = await supabase.from("stores")
        .update({ deleted: true, deleted_at: now, updated_at: now }).eq("id", id);
      if (e1) return res.status(500).json({ error: e1.message });
      const { error: e2 } = await supabase.from("users")
        .update({ deleted: true, deleted_at: now, active: false, updated_at: now }).eq("store_id", id);
      if (e2) return res.status(500).json({ error: e2.message });
      return res.status(200).json({ ok: true, deleted: true });
    }
    return res.status(400).json({ error: "Unknown action." });
  }
  return res.status(405).json({ error: "Method not allowed." });
};
