// Owner/Manager (and admin) can read and update a store's settings from the web.
//   GET  /api/settings?store=<id>
//   POST /api/settings { store, ...fields }
// Per your rules: owners and managers may update settings online; they may NOT
// edit inventory or create sales from the web.
const { supabase, requireAuth, readBody, handledPreflight } = require("./_lib");

const EDITABLE = [
  "business_name", "address", "phone", "invoice_prefix", "default_tax_percent",
  "currency_symbol", "footer_message", "low_stock_threshold", "return_policy_days",
  "loyalty_points_per_100",
];

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res, ["ADMIN", "OWNER", "MANAGER"]);
  if (!tok) return;

  const storeId = tok.role === "ADMIN" ? (req.query.store || req.query.store) : tok.store_id;
  if (!storeId) return res.status(400).json({ error: "No store selected." });

  if (req.method === "GET") {
    const { data, error } = await supabase.from("settings").select("*").eq("store_id", storeId).maybeSingle();
    if (error) return res.status(500).json({ error: error.message });
    return res.status(200).json({ settings: data || null });
  }

  if (req.method === "POST") {
    const body = await readBody(req);
    const patch = {};
    for (const k of EDITABLE) if (k in body) patch[k] = body[k];
    if (Object.keys(patch).length === 0) return res.status(400).json({ error: "Nothing to update." });
    const { error } = await supabase.from("settings").update(patch).eq("store_id", storeId);
    if (error) return res.status(500).json({ error: error.message });
    return res.status(200).json({ ok: true });
  }
  return res.status(405).json({ error: "Method not allowed." });
};
