// POST /api/register-store
//   { store: { name, phone, address }, owner: { username, password, fullName } }
// Called from the desktop app's "Create a store" screen. It creates the store as
// NOT activated (owner_pending) but immediately returns a session token, so the
// new owner is signed in straight away. Their dashboard shows a "waiting for
// administrator activation" notice and settings stay locked until an admin
// activates the store from the web console.
const { supabase, sha256, uuid, signToken, readBody, handledPreflight } = require("./_lib");

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

  const now = new Date().toISOString();
  const storeId = uuid();
  const { error: se } = await supabase.from("stores").insert({
    id: storeId, local_id: 0, name: store.name, phone: store.phone || null, address: store.address || null,
    enabled: true, activated: false, owner_pending: true, deleted: false,
    created_at: now, updated_at: now,
  });
  if (se) return res.status(500).json({ error: se.message });

  // The owner account is active so it can be used right away; the store-level
  // "activated" flag is what the admin flips to unlock settings.
  const ownerId = uuid();
  const ownerRow = {
    id: ownerId, local_id: 0, store_id: storeId,
    username: String(owner.username).trim(), password_hash: sha256(owner.password),
    full_name: owner.fullName || owner.username, role: "OWNER", active: true, deleted: false,
    updated_at: now,
  };
  const { error: ue } = await supabase.from("users").insert(ownerRow);
  if (ue) return res.status(500).json({ error: ue.message });

  // Give the new store a default settings row.
  await supabase.from("settings").insert({
    id: uuid(), store_id: storeId, business_name: store.name,
    address: store.address || "", phone: store.phone || "",
    invoice_prefix: "INV-", default_tax_percent: "0", currency_symbol: "Rs ",
    footer_message: "Thank you for shopping with us!", low_stock_threshold: 5,
    return_policy_days: 7, loyalty_points_per_100: 1, deleted: false, updated_at: now,
  });

  const token = signToken({ id: ownerId, username: ownerRow.username, role: "OWNER", store_id: storeId });
  return res.status(201).json({
    storeId,
    token,
    role: "OWNER",
    store_id: storeId,
    store: { id: storeId, name: store.name, enabled: true, activated: false },
    pending: true,
    message: "Store created. You are signed in, but it is waiting for administrator activation.",
  });
};
