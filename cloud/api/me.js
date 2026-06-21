// GET /api/me  - validates the saved token and returns the profile + stores.
// Lets the web app restore a "remembered" session without asking to log in again.
const { supabase, requireAuth, handledPreflight } = require("./_lib");

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  const tok = requireAuth(req, res);
  if (!tok) return;

  const { data: user } = await supabase.from("users").select("*").eq("id", tok.uid).maybeSingle();
  if (!user || user.deleted || !user.active) return res.status(401).json({ error: "Session is no longer valid." });
  // Cashiers may not use the web version, even with a previously-saved token.
  if (user.role === "CASHIER") return res.status(403).json({ error: "Cashier accounts can't use the web version. Please use the desktop app." });

  let stores = [];
  let pending = false;
  if (user.role === "ADMIN") {
    const { data } = await supabase.from("stores").select("id,name").eq("deleted", false).order("name");
    stores = data || [];
  } else if (user.store_id) {
    const { data: s } = await supabase.from("stores").select("id,name,enabled,activated").eq("id", user.store_id).maybeSingle();
    if (s && !s.enabled) return res.status(403).json({ error: "This store has been disabled." });
    if (s) { stores = [{ id: s.id, name: s.name }]; pending = !s.activated; }
  }

  return res.status(200).json({
    name: user.full_name || user.username, role: user.role,
    store_id: user.store_id || null, pending, stores,
  });
};
