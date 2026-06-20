// POST /api/login  { username, password }
// Used by BOTH the website and (optionally) the desktop app for online auth.
// Returns a JWT plus the profile and the store, after checking the account is
// active, the store is enabled, and (for owners) that the owner was activated.
const { supabase, sha256, signToken, readBody, handledPreflight } = require("./_lib");

module.exports = async (req, res) => {
  if (handledPreflight(req, res)) return;
  if (req.method !== "POST") return res.status(405).json({ error: "Use POST." });

  const { username, password } = await readBody(req);
  if (!username || !password) return res.status(400).json({ error: "Enter a username and password." });

  const { data: user, error } = await supabase
    .from("users").select("*").eq("username", String(username).trim()).maybeSingle();
  if (error) return res.status(500).json({ error: error.message });
  if (!user || user.deleted) return res.status(401).json({ error: "Invalid username or password." });
  if (user.password_hash !== sha256(password)) return res.status(401).json({ error: "Invalid username or password." });
  if (!user.active) return res.status(403).json({ error: "This account is disabled. Contact your administrator." });

  let store = null;
  if (user.store_id) {
    const { data: s } = await supabase.from("stores").select("*").eq("id", user.store_id).maybeSingle();
    store = s || null;
    if (store && !store.enabled) return res.status(403).json({ error: "This store has been disabled by the administrator." });
    if (store && user.role === "OWNER" && !store.activated) {
      return res.status(403).json({ error: "Your store is still waiting for administrator activation." });
    }
  }

  // Admin lists every store for the console.
  let stores = [];
  if (user.role === "ADMIN") {
    const { data } = await supabase.from("stores").select("id,name").eq("deleted", false).order("name");
    stores = data || [];
  } else if (store) {
    stores = [{ id: store.id, name: store.name }];
  }

  return res.status(200).json({
    token: signToken(user),
    name: user.full_name || user.username,
    role: user.role,
    store_id: user.store_id || null,
    store: store ? { id: store.id, name: store.name, enabled: store.enabled, activated: store.activated } : null,
    stores,
  });
};
