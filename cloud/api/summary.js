// GET /api/summary?store=<id>
// Read-only dashboard data for the owner/manager web view (and admin drill-down).
const { supabase, requireAuth, handledPreflight } = require("./_lib");

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res);
  if (!tok) return;

  const storeId = tok.role === "ADMIN" ? (req.query.store || null) : tok.store_id;
  if (!storeId) return res.status(400).json({ error: "No store selected." });

  const today = new Date().toISOString().slice(0, 10);

  const { data: invoices } = await supabase
    .from("invoices").select("invoice_number,grand_total,cashier,created_at,status")
    .eq("store_id", storeId).eq("deleted", false).order("created_at", { ascending: false }).limit(500);
  const inv = invoices || [];

  const todays = inv.filter((r) => (r.created_at || "").slice(0, 10) === today && r.status !== "VOID");
  const sales = todays.reduce((s, r) => s + (parseFloat(r.grand_total) || 0), 0);

  const { data: products } = await supabase
    .from("products").select("sku,name,category,quantity").eq("store_id", storeId).eq("deleted", false);
  const prods = products || [];

  const { data: settings } = await supabase
    .from("settings").select("low_stock_threshold").eq("store_id", storeId).maybeSingle();
  const threshold = (settings && settings.low_stock_threshold) || 5;
  const lowItems = prods.filter((p) => (p.quantity || 0) <= threshold)
    .sort((a, b) => (a.quantity || 0) - (b.quantity || 0)).slice(0, 50);

  return res.status(200).json({
    sales,
    invoices: todays.length,
    products: prods.length,
    lowStock: lowItems.length,
    recentInvoices: inv.slice(0, 20).map((r) => ({
      number: r.invoice_number, date: r.created_at, cashier: r.cashier, total: r.grand_total,
    })),
    lowStockItems: lowItems,
    updatedAt: Date.now(),
  });
};
