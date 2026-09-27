/*
 * Smart Billing Pro - online dashboard (front-end only).
 *
 * This is a static single-page app. Point API_BASE at your deployed backend
 * (see CLOUD-AND-WEB-GUIDE.md for a ready-to-use Node/Express backend that
 * reads the per-store SQLite files your shops upload to the cloud).
 *
 * Expected backend endpoints (all return JSON; protected ones need the
 * Authorization: Bearer <token> header returned by /api/login):
 *   POST /api/login            { username, password } -> { token, name, role, stores: [{id,name}] }
 *   GET  /api/summary?store=ID  -> { sales, invoices, products, lowStock,
 *                                    recentInvoices: [{number,date,cashier,total}],
 *                                    lowStockItems:  [{sku,name,category,quantity}],
 *                                    updatedAt }
 */

const API_BASE = "https://smartbillingapi.vercel.app"; // <-- change to your deployed backend URL
const POLL_MS = 5000;

const $ = (id) => document.getElementById(id);
let token = localStorage.getItem("sbp_token") || null;
let stores = [];
let pollTimer = null;

/* ---------------- auth ---------------- */
async function login(username, password) {
  const res = await fetch(API_BASE + "/api/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error("Invalid username or password.");
  return res.json();
}

function saveSession(data, remember) {
  token = data.token;
  if (remember) {
    localStorage.setItem("sbp_token", token);
    localStorage.setItem("sbp_name", data.name || "");
  } else {
    sessionStorage.setItem("sbp_token", token);
  }
  stores = data.stores || [];
  $("who").textContent = data.name ? `${data.name} (${data.role || ""})` : "";
}

function logout() {
  token = null;
  localStorage.removeItem("sbp_token");
  localStorage.removeItem("sbp_name");
  sessionStorage.removeItem("sbp_token");
  if (pollTimer) clearInterval(pollTimer);
  $("dashboard-view").classList.add("hidden");
  $("login-view").classList.remove("hidden");
}

/* ---------------- data ---------------- */
async function fetchSummary(storeId) {
  const res = await fetch(`${API_BASE}/api/summary?store=${encodeURIComponent(storeId)}`, {
    headers: { Authorization: "Bearer " + token },
  });
  if (res.status === 401) { logout(); throw new Error("Session expired"); }
  if (!res.ok) throw new Error("Could not load data.");
  return res.json();
}

function money(n) {
  if (n == null) return "-";
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function render(summary) {
  $("kpi-sales").textContent = money(summary.sales);
  $("kpi-invoices").textContent = summary.invoices ?? "-";
  $("kpi-products").textContent = summary.products ?? "-";
  $("kpi-lowstock").textContent = summary.lowStock ?? "-";

  const inv = (summary.recentInvoices || []).map((r) =>
    `<tr><td>${r.number}</td><td>${r.date || ""}</td><td>${r.cashier || ""}</td><td class="num">${money(r.total)}</td></tr>`
  ).join("");
  $("invoices-body").innerHTML = inv || `<tr><td colspan="4" class="muted">No invoices yet.</td></tr>`;

  const low = (summary.lowStockItems || []).map((r) =>
    `<tr><td>${r.sku || ""}</td><td>${r.name || ""}</td><td>${r.category || ""}</td><td class="num">${r.quantity}</td></tr>`
  ).join("");
  $("lowstock-body").innerHTML = low || `<tr><td colspan="4" class="muted">Nothing low on stock.</td></tr>`;
}

async function refresh() {
  const storeId = $("store-select").value;
  if (!storeId) return;
  try {
    render(await fetchSummary(storeId));
  } catch (e) {
    console.warn(e.message);
  }
}

function startDashboard() {
  $("login-view").classList.add("hidden");
  $("dashboard-view").classList.remove("hidden");

  const sel = $("store-select");
  sel.innerHTML = stores.map((s) => `<option value="${s.id}">${s.name}</option>`).join("")
    || `<option value="0">My Store</option>`;
  sel.onchange = refresh;

  refresh();
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(refresh, POLL_MS); // live updates while online
}

/* ---------------- wire up ---------------- */
$("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("login-error").textContent = "";
  try {
    const data = await login($("username").value.trim(), $("password").value);
    saveSession(data, $("remember").checked);
    startDashboard();
  } catch (err) {
    $("login-error").textContent = err.message;
  }
});
$("logout-btn").addEventListener("click", logout);
$("refresh-btn").addEventListener("click", refresh);

// If a remembered token exists, try to go straight to the dashboard.
if (token) {
  $("who").textContent = localStorage.getItem("sbp_name") || "";
  // We still need the store list; a real backend should expose /api/me.
  startDashboard();
}
