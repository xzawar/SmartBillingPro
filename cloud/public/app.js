/* Smart Billing Pro - online dashboard front-end.
 * Talks only to this site's own /api/* functions (which hold the Supabase keys).
 * Roles:
 *   ADMIN  -> Stores console (activate / enable / disable) + Accounts
 *   OWNER  -> Dashboard + Accounts (manager/cashier) + Settings
 *   MANAGER-> Dashboard + Settings (no account management, no inventory edits)
 *   CASHIER-> Dashboard (records only)
 */
const API = "/api";
const POLL_MS = 5000;
const $ = (id) => document.getElementById(id);

let token = localStorage.getItem("sbp_token") || sessionStorage.getItem("sbp_token") || null;
let profile = null;
let stores = [];
let pollTimer = null;
let activeTab = "dash";

/* ---------- HTTP ---------- */
async function api(path, opts = {}) {
  const headers = Object.assign({ "Content-Type": "application/json" }, opts.headers || {});
  if (token) headers.Authorization = "Bearer " + token;
  const res = await fetch(API + path, Object.assign({}, opts, { headers }));
  const data = await res.json().catch(() => ({}));
  if (res.status === 401) { logout(); throw new Error(data.error || "Session expired"); }
  if (!res.ok) throw new Error(data.error || "Request failed");
  return data;
}
const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const money = (n) => n == null ? "-" : Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const curStore = () => $("store-select").value || (stores[0] && stores[0].id) || "";

/* ---------- auth ---------- */
async function login(username, password, remember) {
  const data = await api("/login", { method: "POST", body: JSON.stringify({ username, password }) });
  token = data.token;
  (remember ? localStorage : sessionStorage).setItem("sbp_token", token);
  profile = data; stores = data.stores || [];
  startApp();
}
function logout() {
  token = null; profile = null;
  localStorage.removeItem("sbp_token"); sessionStorage.removeItem("sbp_token");
  if (pollTimer) clearInterval(pollTimer);
  $("app-view").classList.add("hidden");
  $("login-view").classList.remove("hidden");
}

/* ---------- shell ---------- */
function tabsForRole(role) {
  if (role === "ADMIN") return [["stores", "Stores"], ["accounts", "Accounts"]];
  if (role === "OWNER") return [["dash", "Dashboard"], ["accounts", "Accounts"], ["settings", "Settings"]];
  if (role === "MANAGER") return [["dash", "Dashboard"], ["settings", "Settings"]];
  return [["dash", "Dashboard"]]; // cashier: records only
}
function startApp() {
  $("login-view").classList.add("hidden");
  $("app-view").classList.remove("hidden");
  $("who").textContent = profile.name || "";
  $("role-tag").textContent = profile.role || "";

  const sel = $("store-select");
  if (stores.length > 1) {
    sel.classList.remove("hidden");
    sel.innerHTML = stores.map((s) => `<option value="${esc(s.id)}">${esc(s.name)}</option>`).join("");
    sel.onchange = () => renderTab();
  } else { sel.classList.add("hidden"); }

  const tabs = tabsForRole(profile.role);
  activeTab = tabs[0][0];
  $("tabbar").innerHTML = tabs.map(([k, l]) =>
    `<button data-tab="${k}">${l}</button>`).join("");
  Array.from($("tabbar").children).forEach((b) => b.onclick = () => { activeTab = b.dataset.tab; renderTab(); });

  renderTab();
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(() => { if (activeTab === "dash") loadDashboard(); }, POLL_MS);
}
function showSection(id) {
  ["admin-section", "dash-section", "accounts-section", "settings-section"].forEach((s) =>
    $(s).classList.add("hidden"));
  $(id).classList.remove("hidden");
  Array.from($("tabbar").children).forEach((b) => b.classList.toggle("active", b.dataset.tab === activeTab));
}
function renderTab() {
  if (activeTab === "stores") { showSection("admin-section"); loadStores(); }
  else if (activeTab === "accounts") { showSection("accounts-section"); loadAccounts(); }
  else if (activeTab === "settings") { showSection("settings-section"); loadSettings(); }
  else { showSection("dash-section"); loadDashboard(); }
}

/* ---------- admin: stores ---------- */
async function loadStores() {
  try {
    const { stores: list } = await api("/admin/stores");
    $("stores-body").innerHTML = list.map((s) => {
      const status = !s.enabled ? '<span class="badge off">Disabled</span>'
        : s.activated ? '<span class="badge on">Active</span>'
        : '<span class="badge pend">Pending</span>';
      const actions = [];
      if (!s.activated) actions.push(`<button class="btn-mini go" data-act="activate" data-id="${esc(s.id)}">Activate</button>`);
      actions.push(s.enabled
        ? `<button class="btn-mini danger" data-act="disable" data-id="${esc(s.id)}">Disable</button>`
        : `<button class="btn-mini go" data-act="enable" data-id="${esc(s.id)}">Enable</button>`);
      return `<tr><td>${esc(s.name)}</td><td>${esc(s.phone || "")}</td><td>${status}</td>`
        + `<td>${s.owner_pending ? '<span class="muted">awaiting activation</span>' : 'active'}</td>`
        + `<td>${actions.join("")}</td></tr>`;
    }).join("") || `<tr><td colspan="5" class="muted">No stores yet.</td></tr>`;
    Array.from($("stores-body").querySelectorAll("button")).forEach((b) => b.onclick = async () => {
      await api("/admin/stores", { method: "POST", body: JSON.stringify({ id: b.dataset.id, action: b.dataset.act }) });
      loadStores();
    });
  } catch (e) { $("stores-body").innerHTML = `<tr><td colspan="5" class="error">${esc(e.message)}</td></tr>`; }
}

/* ---------- dashboard ---------- */
async function loadDashboard() {
  try {
    const q = profile.role === "ADMIN" && curStore() ? `?store=${encodeURIComponent(curStore())}` : "";
    const s = await api("/summary" + q);
    $("kpi-sales").textContent = money(s.sales);
    $("kpi-invoices").textContent = s.invoices ?? "-";
    $("kpi-products").textContent = s.products ?? "-";
    $("kpi-lowstock").textContent = s.lowStock ?? "-";
    $("invoices-body").innerHTML = (s.recentInvoices || []).map((r) =>
      `<tr><td>${esc(r.number)}</td><td>${esc(r.date || "")}</td><td>${esc(r.cashier || "")}</td><td class="num">${money(r.total)}</td></tr>`
    ).join("") || `<tr><td colspan="4" class="muted">No invoices.</td></tr>`;
    $("lowstock-body").innerHTML = (s.lowStockItems || []).map((r) =>
      `<tr><td>${esc(r.sku)}</td><td>${esc(r.name)}</td><td>${esc(r.category || "")}</td><td class="num">${r.quantity}</td></tr>`
    ).join("") || `<tr><td colspan="4" class="muted">Nothing low.</td></tr>`;
  } catch (e) { console.warn(e.message); }
}

/* ---------- accounts ---------- */
async function loadAccounts() {
  try {
    const { accounts } = await api("/accounts");
    $("accounts-body").innerHTML = accounts.map((a) =>
      `<tr><td>${esc(a.username)}</td><td>${esc(a.full_name || "")}</td><td>${esc(a.role)}</td>`
      + `<td>${a.active ? '<span class="badge on">Active</span>' : '<span class="badge off">Disabled</span>'}</td>`
      + `<td>${a.role === "ADMIN" ? "" :
        (a.active ? `<button class="btn-mini danger" data-act="disable" data-id="${esc(a.id)}">Disable</button>`
                  : `<button class="btn-mini go" data-act="enable" data-id="${esc(a.id)}">Enable</button>`)
        + `<button class="btn-mini" data-act="reset" data-id="${esc(a.id)}">Reset password</button>`}</td></tr>`
    ).join("") || `<tr><td colspan="5" class="muted">No accounts.</td></tr>`;
    Array.from($("accounts-body").querySelectorAll("button")).forEach((b) => b.onclick = () => accountAction(b.dataset.act, b.dataset.id));
  } catch (e) { $("accounts-body").innerHTML = `<tr><td colspan="5" class="error">${esc(e.message)}</td></tr>`; }
}
async function accountAction(action, id) {
  if (action === "reset") {
    const pw = prompt("Enter a new password for this account:");
    if (!pw) return;
    await api("/accounts", { method: "POST", body: JSON.stringify({ action: "reset", id, password: pw }) });
  } else {
    await api("/accounts", { method: "POST", body: JSON.stringify({ action, id }) });
  }
  loadAccounts();
}
$("add-account-btn").onclick = () => {
  const roleOptions = profile.role === "ADMIN"
    ? `<option value="OWNER">Owner</option><option value="MANAGER">Manager</option><option value="CASHIER">Cashier</option>`
    : `<option value="MANAGER">Manager</option><option value="CASHIER">Cashier</option>`;
  openModal("New account", `
    <label>Username<input id="m-user"/></label>
    <label>Full name<input id="m-name"/></label>
    <label>Password<input id="m-pass" type="password"/></label>
    <label>Role<select id="m-role">${roleOptions}</select></label>`,
    async () => {
      const body = { action: "create", username: $("m-user").value.trim(),
        fullName: $("m-name").value.trim(), password: $("m-pass").value, role: $("m-role").value };
      if (profile.role === "ADMIN") body.storeId = curStore();
      await api("/accounts", { method: "POST", body: JSON.stringify(body) });
      closeModal(); loadAccounts();
    });
};

/* ---------- settings ---------- */
const SETTING_FIELDS = [
  ["business_name", "Business name"], ["phone", "Phone"], ["address", "Address"],
  ["invoice_prefix", "Invoice prefix"], ["currency_symbol", "Currency symbol"],
  ["default_tax_percent", "Default tax %"], ["low_stock_threshold", "Low stock threshold"],
  ["return_policy_days", "Return policy days"], ["loyalty_points_per_100", "Loyalty pts / 100"],
  ["footer_message", "Receipt footer"],
];
async function loadSettings() {
  try {
    const q = profile.role === "ADMIN" && curStore() ? `?store=${encodeURIComponent(curStore())}` : "";
    const { settings } = await api("/settings" + q);
    const s = settings || {};
    $("settings-form").innerHTML = SETTING_FIELDS.map(([k, l]) =>
      `<label>${l}<input id="set-${k}" value="${esc(s[k] == null ? "" : s[k])}"/></label>`).join("");
    $("settings-msg").textContent = "";
  } catch (e) { $("settings-form").innerHTML = `<p class="error">${esc(e.message)}</p>`; }
}
$("save-settings-btn").onclick = async () => {
  const body = {};
  if (profile.role === "ADMIN") body.store = curStore();
  SETTING_FIELDS.forEach(([k]) => { body[k] = $("set-" + k) ? $("set-" + k).value : undefined; });
  try { await api("/settings" + (profile.role === "ADMIN" && curStore() ? `?store=${encodeURIComponent(curStore())}` : ""),
    { method: "POST", body: JSON.stringify(body) });
    $("settings-msg").textContent = "Saved.";
  } catch (e) { $("settings-msg").textContent = e.message; }
};

/* ---------- modal ---------- */
let modalOk = null;
function openModal(title, html, onOk) {
  $("modal-title").textContent = title; $("modal-body").innerHTML = html;
  $("modal-error").textContent = ""; modalOk = onOk; $("modal").classList.remove("hidden");
}
function closeModal() { $("modal").classList.add("hidden"); modalOk = null; }
$("modal-cancel").onclick = closeModal;
$("modal-ok").onclick = async () => { try { if (modalOk) await modalOk(); } catch (e) { $("modal-error").textContent = e.message; } };

/* ---------- wire up ---------- */
$("login-form").addEventListener("submit", async (e) => {
  e.preventDefault(); $("login-error").textContent = "";
  try { await login($("username").value.trim(), $("password").value, $("remember").checked); }
  catch (err) { $("login-error").textContent = err.message; }
});
$("logout-btn").onclick = logout;
$("refresh-btn").onclick = renderTab;

// Restore a remembered session.
if (token) {
  api("/me").then((data) => { profile = data; stores = data.stores || []; startApp(); })
    .catch(() => logout());
}
