// POST /api/register-store
//   { store: { name, phone, address }, owner: { username, password, fullName } }
// Called from the desktop app's "Create a store" screen. Creates the store as
// NOT activated and the owner account as inactive/pending. The administrator
// then activates both from the web admin console before the owner can sign in.
const { supabase, sha256, uuid, readBody, handledPreflight } = require("./_lib");

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ error: "Use POST." });

  const body = await readBody(req);
  const store = body.store || {};
  const owner = body.owner || {};
  if (!store.name) return res.status(400).json({ error: "Store name is required." });
  if (!owner.username || !owner.password) return res.status(400).json({ error: "Owner username and password are required." });

  // Reject duplicate usernames up front for a friendly message.
  const { data: existing } = await supabase.from("users").select("id").eq("username", String(owner.username).trim()).maybeSingle();
  if (existing) return res.status(409).json({ error: "That username is already taken." });

  const storeId = uuid();
  const { error: se } = await supabase.from("stores").insert({
    id: storeId, local_id: 0, name: store.name, phone: store.phone || null, address: store.address || null,
    enabled: true, activated: false, owner_pending: true, deleted: false,
  });
  if (se) return res.status(500).json({ error: se.message });

  const { error: ue } = await supabase.from("users").insert({
    id: uuid(), local_id: 0, store_id: storeId,
    username: String(owner.username).trim(), password_hash: sha256(owner.password),
    full_name: owner.fullName || owner.username, role: "OWNER", active: false, deleted: false,
  });
  if (ue) return res.status(500).json({ error: ue.message });

  // Give the new store a default settings row.
  await supabase.from("settings").insert({
    id: uuid(), store_id: storeId, business_name: store.name,
    address: store.address || "", phone: store.phone || "",
    invoice_prefix: "INV-", default_tax_percent: "0", currency_symbol: "Rs ",
    footer_message: "Thank you for shopping with us!", low_stock_threshold: 5,
    return_policy_days: 7, loyalty_points_per_100: 1, deleted: false,
  });

  return res.status(201).json({
    storeId,
    message: "Store registered. It is now waiting for administrator activation.",
  });
};
