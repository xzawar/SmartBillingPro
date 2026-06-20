// Shared helpers for every API route.
//
// Environment variables (set these in Vercel -> Project -> Settings -> Environment Variables):
//   SUPABASE_URL            e.g. https://xxxxxxxx.supabase.co
//   SUPABASE_SERVICE_ROLE   the SERVICE ROLE key (server-only secret!)
//   JWT_SECRET              any long random string used to sign login tokens
//   DEVICE_TOKEN            (optional) shared secret the desktop app sends for sync

const { createClient } = require("@supabase/supabase-js");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");

const JWT_SECRET = process.env.JWT_SECRET || "change-this-secret";

// One Supabase client using the service-role key. It bypasses RLS, which is why
// this key must never leave the server.
const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_ROLE,
  { auth: { persistSession: false } }
);

// SHA-256 hex - identical to the desktop app's PasswordUtil, so a password set
// in the app verifies on the web and vice-versa.
function sha256(raw) {
  return crypto.createHash("sha256").update(String(raw == null ? "" : raw), "utf8").digest("hex");
}

function uuid() {
  return crypto.randomUUID();
}

function signToken(user) {
  return jwt.sign(
    { uid: user.id, username: user.username, role: user.role, store_id: user.store_id || null },
    JWT_SECRET,
    { expiresIn: "12h" }
  );
}

// Reads and verifies the Bearer token. Returns the decoded payload or null.
function readToken(req) {
  const h = req.headers["authorization"] || "";
  const m = h.match(/^Bearer\s+(.+)$/i);
  if (!m) return null;
  try {
    return jwt.verify(m[1], JWT_SECRET);
  } catch {
    return null;
  }
}

// Convenience: require a logged-in user; optionally restrict to certain roles.
function requireAuth(req, res, roles) {
  const tok = readToken(req);
  if (!tok) {
    res.status(401).json({ error: "Not signed in." });
    return null;
  }
  if (roles && roles.length && !roles.includes(tok.role)) {
    res.status(403).json({ error: "You do not have access to this action." });
    return null;
  }
  return tok;
}

// Reads a JSON body (Vercel usually parses it, but be defensive).
async function readBody(req) {
  if (req.body && typeof req.body === "object") return req.body;
  return await new Promise((resolve) => {
    let data = "";
    req.on("data", (c) => (data += c));
    req.on("end", () => {
      try { resolve(JSON.parse(data || "{}")); } catch { resolve({}); }
    });
  });
}

// Handles CORS preflight. Returns true if the request was a preflight (already answered).
function handledPreflight(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") {
    res.status(204).end();
    return true;
  }
  return false;
}

// The tables the desktop app syncs, and the parent each child depends on.
const SYNC_TABLES = [
  "stores", "users", "suppliers", "products", "customers", "settings",
  "invoices", "invoice_items", "purchase_orders", "purchase_order_items", "audit_log",
];

module.exports = {
  supabase, sha256, uuid, signToken, readToken, requireAuth,
  readBody, handledPreflight, SYNC_TABLES, JWT_SECRET,
};
