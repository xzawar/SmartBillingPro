// Owners and managers (and the admin) can now VIEW and EDIT a store's inventory
// straight from the web. Products created in the desktop app appear here once
// they sync, and edits made here sync back down to the desktop app.
//
//   GET  /api/inventory?store=<id>            -> { products: [...] }
//   POST /api/inventory { action, ... }
//        action: "create"  { sku, name, category, cost_price, sale_price, quantity }
//        action: "update"  { id, ...any of the fields above }
//        action: "delete"  { id }
//
// Owners/managers always work on THEIR OWN store (taken from their token). The
// admin may target any store with ?store=<id> (GET) or { store } (POST).
const { supabase, uuid, requireAuth, readBody, handledPreflight } = require("./_lib");

// cost_price / sale_price are stored as TEXT in the schema (mirrors the desktop
// app), quantity is an integer, everything else is plain text.
const FIELDS = ["sku", "name", "category", "cost_price", "sale_price", "quantity"];

function cleanPatch(body) {
  const patch = {};
  for (const k of FIELDS) {
    if (!(k in body)) continue;
    if (k === "quantity") {
      const n = parseInt(body[k], 10);
      patch[k] = Number.isFinite(n) ? n : 0;
    } else if (k === "cost_price" || k === "sale_price") {
      patch[k] = String(body[k] == null || body[k] === "" ? "0" : body[k]);
    } else {
      patch[k] = body[k] == null ? null : String(body[k]);
    }
  }
  return patch;
}

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res, ["ADMIN", "OWNER", "MANAGER"]);
  if (!tok) return;

  // ---- list a store's products ----
  if (req.method === "GET") {
    const storeId = tok.role === "ADMIN" ? req.query.store : tok.store_id;
    if (!storeId) return res.status(400).json({ error: "No store selected." });
    const { data, error } = await supabase
      .from("products")
      .select("id,sku,name,category,cost_price,sale_price,quantity")
      .eq("store_id", storeId)
      .eq("deleted", false)
      .order("name");
    if (error) return res.status(500).json({ error: error.message });
    return res.status(200).json({ products: data || [] });
  }

  // ---- create / update / delete ----
  if (req.method === "POST") {
    const body = await readBody(req);
    const sid = tok.role === "ADMIN" ? (body.store || req.query.store) : tok.store_id;
    if (!sid) return res.status(400).json({ error: "No store selected." });
    const now = new Date().toISOString();
    const action = body.action;

    if (action === "create") {
      const patch = cleanPatch(body);
      if (!patch.sku || !patch.name) return res.status(400).json({ error: "SKU and name are required." });
      if (!("cost_price" in patch)) patch.cost_price = "0";
      if (!("sale_price" in patch)) patch.sale_price = "0";
      if (!("quantity" in patch)) patch.quantity = 0;
      const row = Object.assign(
        { id: uuid(), local_id: 0, store_id: sid, deleted: false, updated_at: now },
        patch
      );
      const { error } = await supabase.from("products").insert(row);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(201).json({ ok: true });
    }

    if (action === "update") {
      if (!body.id) return res.status(400).json({ error: "Product id is required." });
      const patch = cleanPatch(body);
      if (Object.keys(patch).length === 0) return res.status(400).json({ error: "Nothing to update." });
      patch.updated_at = now;
      // Scope to the caller's store so nobody can edit another store's products.
      const { data, error } = await supabase
        .from("products").update(patch)
        .eq("id", body.id).eq("store_id", sid)
        .select("id");
      if (error) return res.status(500).json({ error: error.message });
      if (!data || !data.length) return res.status(404).json({ error: "Product not found in this store." });
      return res.status(200).json({ ok: true });
    }

    if (action === "delete") {
      if (!body.id) return res.status(400).json({ error: "Product id is required." });
      // Soft-delete so the change syncs down to the desktop app cleanly.
      const { error } = await supabase
        .from("products").update({ deleted: true, updated_at: now })
        .eq("id", body.id).eq("store_id", sid);
      if (error) return res.status(500).json({ error: error.message });
      return res.status(200).json({ ok: true });
    }

    return res.status(400).json({ error: "Unknown action." });
  }

  return res.status(405).json({ error: "Method not allowed." });
};
