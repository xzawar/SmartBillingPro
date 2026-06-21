/* Smart Billing Pro - web dashboard logic.
   Talks to the Vercel API. Styled like the desktop app: a left sidebar of
   sections plus a gradient top bar. Admins manage stores + accounts; owners and
   managers see their dashboard and (when activated) settings. */

const API = "/api";
const POLL_MS = 5000;
const $ = (id) => document.getElementById(id);

// ---- session state ----
let token = localStorage.getItem("sbp_token") || sessionStorage.getItem("sbp_token") || "";
let profile = null;          // { name, role, store_id, pending, stores }
let currentStoreId = null;   // selected store (admin can switch)
let activeSection = null;
let pollTimer = null;

// ---- small helpers ----
function saveToken(t, remember) {
  token = t;
  (remember ? localStorage : sessionStorage).setItem("sbp_token", t);
  (remember ? sessionStorage : localStorage).removeItem("sbp_token");
}
function clearToken() {
  token = "";
  localStorage.removeItem("sbp_token");
  sessionStorage.removeItem("sbp_token");
}
async function api(path, opts = {}) {
  const headers = Object.assign({ "Content-Type": "application/json" }, opts.headers || {});
  if (token) headers.Authorization = "Bearer " + token;
  const res = await fetch(API + path, Object.assign({}, opts, { headers }));
  let data = {};
  try { data = await res.json(); } catch (e) {}
  if (!res.ok) throw new Error(data.error || ("Request failed (" + res.status + ")"));
  return data;
}
function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function money(n) { return (parseFloat(n) || 0).toFixed(2); }

// ---- which sidebar sections each role sees (matches the desktop app) ----
function sectionsForRole(role) {
  if (role === "ADMIN") return [
    { id: "admin", label: "Stores" },
    { id: "accounts", label: "Accounts" },
  ];
  if (role === "OWNER") return [
    { id: "dash", label: "Dashboard" },
    { id: "inventory", label: "Inventory" },
    { id: "accounts", label: "Accounts" },
    { id: "settings", label: "Settings" },
  ];
  if (role === "MANAGER") return [
    { id: "dash", label: "Dashboard" },
    { id: "inventory", label: "Inventory" },
    { id: "settings", label: "Settings" },
  ];
  return []; // CASHIER accounts can't sign in to the web version anymore
}

// =================== LOGIN ===================
$("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("login-error").textContent = "";
  const username = $("username").value.trim();
  const password = $("password").value;
  const remember = $("remember").checked;
  try {
    const data = await api("/login", { method: "POST", body: JSON.stringify({ username, password }) });
    saveToken(data.token, remember);
    profile = data;
    afterLogin();
  } catch (err) {
    $("login-error").textContent = err.message;
  }
});

function logout() {
  if (pollTimer) clearInterval(pollTimer);
  clearToken();
  profile = null;
  $("app-view").classList.add("hidden");
  $("login-view").classList.remove("hidden");
}
$("logout-btn").addEventListener("click", logout);
$("refresh-btn").addEventListener("click", () => refreshActive());

// =================== AFTER LOGIN: build shell ===================
function afterLogin() {
  $("login-view").classList.add("hidden");
  $("app-view").classList.remove("hidden");

  $("who").textContent = profile.name || "";
  $("role-tag").textContent = profile.role || "";

  // Build sidebar.
  const nav = $("sidebar-nav");
  nav.innerHTML = "";
  const sections = sectionsForRole(profile.role);
  sections.forEach((s) => {
    const b = document.createElement("button");
    b.textContent = s.label;
    b.dataset.section = s.id;
    b.addEventListener("click", () => showSection(s.id));
    nav.appendChild(b);
  });

  // Store selector (admin only - can drill into any store's dashboard later).
  const sel = $("store-select");
  if (profile.role === "OWNER" || profile.role === "MANAGER" || profile.role === "CASHIER") {
    currentStoreId = profile.store_id;
    sel.classList.add("hidden");
  } else {
    sel.classList.add("hidden"); // admin uses the Stores tab
  }

  updatePendingBanner();
  showSection(sections[0].id);

  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(() => refreshActive(true), POLL_MS);
}

// Show the "waiting for activation" banner + lock settings if pending.
function updatePendingBanner() {
  const pending = !!(profile && profile.pending);
  $("pending-banner").classList.toggle("hidden", !pending);
}

// =================== SECTION SWITCHING ===================
const SECTION_EL = {
  admin: "admin-section", dash: "dash-section",
  accounts: "accounts-section", settings: "settings-section",
  inventory: "inventory-section",
};
const SECTION_TITLE = { admin: "Stores", dash: "Dashboard", accounts: "Accounts", settings: "Settings", inventory: "Inventory" };

function showSection(id) {
  activeSection = id;
  Object.values(SECTION_EL).forEach((el) => $(el).classList.add("hidden"));
  $(SECTION_EL[id]).classList.remove("hidden");
  $("top-title").textContent = SECTION_TITLE[id] || "";
  document.querySelectorAll("#sidebar-nav button").forEach((b) =>
    b.classList.toggle("active", b.dataset.section === id));
  refreshActive();
}

function refreshActive(silent) {
  if (!activeSection) return;
  const fn = {
    admin: loadStores, dash: loadDashboard,
    accounts: loadAccounts, settings: loadSettings,
    inventory: loadInventory,
  }[activeSection];
  if (fn) fn(silent).catch((e) => { if (!silent) alert(e.message); });
}

// =================== ADMIN: STORES ===================
async function loadStores() {
  const data = await api("/admin/stores");
  const body = $("stores-body");
  const rows = data.stores || [];
  if (!rows.length) { body.innerHTML = '<tr><td colspan="5" class="muted">No stores yet. Click "New store" to add one.</td></tr>'; return; }
  body.innerHTML = rows.map((s) => {
    let status, actions;
    if (!s.enabled) {
      status = '<span class="badge off">Disabled</span>';
      actions = btn("enable-store", s.id, "Enable", "go");
    } else if (!s.activated) {
      status = '<span class="badge pend">Waiting activation</span>';
      actions = btn("activate-store", s.id, "Activate", "go") + btn("disable-store", s.id, "Disable", "danger");
    } else {
      status = '<span class="badge on">Active</span>';
      actions = btn("disable-store", s.id, "Disable", "danger");
    }
    const owner = s.owner_pending ? '<span class="muted">pending owner</span>' : "";
    return "<tr><td>" + esc(s.name) + "</td><td>" + esc(s.phone || "-") + "</td><td>" +
      status + "</td><td>" + owner + "</td><td>" + actions + "</td></tr>";
  }).join("");
}
function btn(action, id, label, cls) {
  return '<button class="btn-mini ' + (cls || "") + '" data-action="' + action + '" data-id="' + esc(id) + '">' + label + "</button>";
}

// Delegated clicks for store action buttons.
$("stores-body").addEventListener("click", async (e) => {
  const b = e.target.closest("button[data-action]");
  if (!b) return;
  const map = { "enable-store": "enable", "disable-store": "disable", "activate-store": "activate" };
  const action = map[b.dataset.action];
  if (!action) return;
  if (action === "disable" && !confirm("Disable this store? All its accounts will be blocked from signing in.")) return;
  try {
    await api("/admin/stores", { method: "POST", body: JSON.stringify({ action, id: b.dataset.id }) });
    loadStores();
  } catch (err) { alert(err.message); }
});

// New store (admin) modal.
$("add-store-btn").addEventListener("click", () => {
  openModal("New store", `
    <label>Store name<input id="f-store-name" type="text" /></label>
    <label>Phone<input id="f-store-phone" type="text" /></label>
    <label>Address<input id="f-store-address" type="text" /></label>
  `, async () => {
    const name = $("f-store-name").value.trim();
    if (!name) throw new Error("Store name is required.");
    await api("/admin/stores", { method: "POST", body: JSON.stringify({
      action: "create", name, phone: $("f-store-phone").value.trim(), address: $("f-store-address").value.trim(),
    }) });
    loadStores();
  });
});

// =================== DASHBOARD ===================
async function loadDashboard() {
  const storeId = currentStoreId || profile.store_id;
  const q = profile.role === "ADMIN" && storeId ? ("?store=" + encodeURIComponent(storeId)) : "";
  const d = await api("/summary" + q);
  $("kpi-sales").textContent = money(d.sales);
  $("kpi-invoices").textContent = d.invoices;
  $("kpi-products").textContent = d.products;
  $("kpi-lowstock").textContent = d.lowStock;
  $("invoices-body").innerHTML = (d.recentInvoices || []).length
    ? d.recentInvoices.map((r) => "<tr><td>" + esc(r.number) + "</td><td>" +
        esc((r.date || "").slice(0, 16).replace("T", " ")) + "</td><td>" + esc(r.cashier || "-") +
        '</td><td class="num">' + money(r.total) + "</td></tr>").join("")
    : '<tr><td colspan="4" class="muted">No invoices yet.</td></tr>';
  $("lowstock-body").innerHTML = (d.lowStockItems || []).length
    ? d.lowStockItems.map((p) => "<tr><td>" + esc(p.sku) + "</td><td>" + esc(p.name) + "</td><td>" +
        esc(p.category || "-") + '</td><td class="num">' + (p.quantity || 0) + "</td></tr>").join("")
    : '<tr><td colspan="4" class="muted">Nothing low on stock.</td></tr>';
}

// =================== ACCOUNTS ===================
let storesCache = [];   // for the account modal's store dropdown (admin)
let accountsCache = []; // cached account rows so a row click can open its popup
let storeNameById = {}; // store id -> name, for the Store column / popup
async function loadAccounts() {
  const d = await api("/accounts");
  const rows = d.accounts || [];
  accountsCache = rows;
  // Build a quick id->name lookup for the Store column (admin view).
  storeNameById = {};
  if (profile.role === "ADMIN") {
    try { const sd = await api("/admin/stores"); storesCache = sd.stores || []; storesCache.forEach((s) => (storeNameById[s.id] = s.name)); } catch (e) {}
  }
  $("accounts-body").innerHTML = rows.length
    ? rows.map((a) => {
        const status = a.active ? '<span class="badge on">Active</span>' : '<span class="badge off">Disabled</span>';
        return '<tr class="clickable" data-id="' + esc(a.id) + '"><td>' + esc(a.username) + "</td><td>" +
          esc(a.full_name || "-") + "</td><td>" + esc(a.role) + "</td><td>" + esc(accountStoreName(a)) + "</td><td>" +
          status + '</td><td class="row-go">Manage \u203A</td></tr>';
      }).join("")
    : '<tr><td colspan="6" class="muted">No accounts yet.</td></tr>';
}

function accountStoreName(a) {
  if (a.role === "ADMIN") return "-";
  return storeNameById[a.store_id] || (profile.role === "OWNER" ? "(your store)" : "-");
}

// Click a row to open its account popup (a replica of the desktop app's dialog).
$("accounts-body").addEventListener("click", (e) => {
  const tr = e.target.closest("tr[data-id]");
  if (!tr) return;
  const a = accountsCache.find((x) => String(x.id) === String(tr.dataset.id));
  if (a) openAccountModal(a);
});

// Account detail popup: view the account and change its status / password.
function openAccountModal(a) {
  const isAdmin = a.role === "ADMIN";
  const body = `
    <label>Username<input type="text" value="${esc(a.username)}" disabled /></label>
    <label>Full name<input type="text" value="${esc(a.full_name || "")}" disabled /></label>
    <label>Role<input type="text" value="${esc(a.role)}" disabled /></label>
    <label>Store<input type="text" value="${esc(accountStoreName(a))}" disabled /></label>
    <label class="check"><input id="f-acc-active" type="checkbox" ${a.active ? "checked" : ""} ${isAdmin ? "disabled" : ""} /> Account active</label>
    <label>Reset password<input id="f-acc-newpass" type="password" placeholder="${isAdmin ? "Not available for admin" : "Leave blank to keep current"}" ${isAdmin ? "disabled" : ""} /></label>
  `;
  openModal("Account \u2022 " + a.username, body, async () => {
    if (isAdmin) return; // the administrator account cannot be edited here
    const active = $("f-acc-active").checked;
    if (active !== !!a.active) {
      await api("/accounts", { method: "POST", body: JSON.stringify({ action: active ? "enable" : "disable", id: a.id }) });
    }
    const pw = $("f-acc-newpass").value;
    if (pw) {
      await api("/accounts", { method: "POST", body: JSON.stringify({ action: "reset", id: a.id, password: pw }) });
    }
    loadAccounts();
  });
}

// New account modal: admin must PICK a store + role; owners add staff to own store.
$("add-account-btn").addEventListener("click", async () => {
  // Make sure we have the store list for admins.
  let storeOptions = "";
  if (profile.role === "ADMIN") {
    try { const sd = await api("/admin/stores"); storesCache = (sd.stores || []).filter((s) => s.enabled); } catch (e) {}
    if (!storesCache.length) { alert("Create a store first, then add accounts to it."); return; }
    storeOptions = storesCache.map((s) => '<option value="' + esc(s.id) + '">' + esc(s.name) + "</option>").join("");
  }
  const roleOptions = profile.role === "ADMIN"
    ? '<option value="OWNER">Owner</option><option value="MANAGER">Manager</option><option value="CASHIER">Cashier</option>'
    : '<option value="MANAGER">Manager</option><option value="CASHIER">Cashier</option>';
  const storeField = profile.role === "ADMIN"
    ? '<label>Store<select id="f-acc-store">' + storeOptions + "</select></label>"
    : "";
  openModal("New account", `
    <label>Username<input id="f-acc-user" type="text" /></label>
    <label>Full name<input id="f-acc-name" type="text" /></label>
    <label>Password<input id="f-acc-pass" type="password" /></label>
    <label>Role<select id="f-acc-role">` + roleOptions + `</select></label>
    ` + storeField + `
  `, async () => {
    const username = $("f-acc-user").value.trim();
    const password = $("f-acc-pass").value;
    if (!username || !password) throw new Error("Username and password are required.");
    const payload = {
      action: "create", username, password,
      fullName: $("f-acc-name").value.trim(), role: $("f-acc-role").value,
    };
    if (profile.role === "ADMIN") payload.storeId = $("f-acc-store").value;
    await api("/accounts", { method: "POST", body: JSON.stringify(payload) });
    loadAccounts();
  });
});

// =================== SETTINGS ===================
const SETTING_FIELDS = [
  ["business_name", "Business name"], ["address", "Address"], ["phone", "Phone"],
  ["invoice_prefix", "Invoice prefix"], ["default_tax_percent", "Tax %"],
  ["currency_symbol", "Currency symbol"], ["footer_message", "Receipt footer"],
  ["low_stock_threshold", "Low-stock threshold"], ["return_policy_days", "Return policy (days)"],
  ["loyalty_points_per_100", "Loyalty points / 100"],
];
async function loadSettings() {
  const storeId = currentStoreId || profile.store_id;
  const q = profile.role === "ADMIN" && storeId ? ("?store=" + encodeURIComponent(storeId)) : "";
  const d = await api("/settings" + q);
  const s = d.settings || {};
  const locked = !!(profile && profile.pending);   // store waiting for activation
  const form = $("settings-form");
  form.innerHTML = SETTING_FIELDS.map(([k, label]) =>
    "<label>" + esc(label) + '<input data-key="' + k + '" value="' + esc(s[k] == null ? "" : s[k]) + '" ' +
    (locked ? "disabled" : "") + " /></label>").join("");
  $("save-settings-btn").disabled = locked;
  $("settings-msg").textContent = locked
    ? "Settings are locked until an administrator activates your store."
    : "";
}
$("save-settings-btn").addEventListener("click", async () => {
  if (profile && profile.pending) { alert("Settings are locked until an administrator activates your store."); return; }
  const payload = {};
  document.querySelectorAll("#settings-form input[data-key]").forEach((i) => (payload[i.dataset.key] = i.value));
  if (profile.role === "ADMIN" && currentStoreId) payload.store = currentStoreId;
  try {
    await api("/settings", { method: "POST", body: JSON.stringify(payload) });
    $("settings-msg").textContent = "Saved.";
    setTimeout(() => ($("settings-msg").textContent = ""), 2000);
  } catch (err) { alert(err.message); }
});

// =================== INVENTORY (owner / manager view + edit) ===================
let inventoryCache = []; // cached product rows so a row click can open its editor

// Admin works on the selected store; owners/managers on their own store.
function inventoryStorePayload() {
  const sid = currentStoreId || profile.store_id;
  return profile.role === "ADMIN" && sid ? { store: sid } : {};
}

async function loadInventory() {
  const storeId = currentStoreId || profile.store_id;
  const q = profile.role === "ADMIN" && storeId ? ("?store=" + encodeURIComponent(storeId)) : "";
  const d = await api("/inventory" + q);
  const rows = d.products || [];
  inventoryCache = rows;
  const locked = !!(profile && profile.pending);
  $("add-product-btn").disabled = locked;
  $("inventory-msg").textContent = locked
    ? "Inventory is locked until an administrator activates your store."
    : "";
  $("inventory-body").innerHTML = rows.length
    ? rows.map((p) =>
        '<tr class="clickable" data-id="' + esc(p.id) + '"><td>' + esc(p.sku) + "</td><td>" +
        esc(p.name) + "</td><td>" + esc(p.category || "-") + '</td><td class="num">' +
        money(p.cost_price) + '</td><td class="num">' + money(p.sale_price) + '</td><td class="num">' +
        (p.quantity || 0) + '</td><td class="row-go">Edit \u203A</td></tr>').join("")
    : '<tr><td colspan="7" class="muted">No products yet.</td></tr>';
}

function productFormHtml(p) {
  p = p || {};
  return (
    '<label>SKU<input id="f-prod-sku" type="text" value="' + esc(p.sku || "") + '" /></label>' +
    '<label>Name<input id="f-prod-name" type="text" value="' + esc(p.name || "") + '" /></label>' +
    '<label>Category<input id="f-prod-cat" type="text" value="' + esc(p.category || "") + '" /></label>' +
    '<label>Cost price<input id="f-prod-cost" type="number" step="0.01" value="' + esc(p.cost_price == null ? "" : p.cost_price) + '" /></label>' +
    '<label>Sale price<input id="f-prod-sale" type="number" step="0.01" value="' + esc(p.sale_price == null ? "" : p.sale_price) + '" /></label>' +
    '<label>Quantity<input id="f-prod-qty" type="number" step="1" value="' + esc(p.quantity == null ? "" : p.quantity) + '" /></label>'
  );
}

function readProductForm() {
  return {
    sku: $("f-prod-sku").value.trim(),
    name: $("f-prod-name").value.trim(),
    category: $("f-prod-cat").value.trim(),
    cost_price: $("f-prod-cost").value.trim() || "0",
    sale_price: $("f-prod-sale").value.trim() || "0",
    quantity: $("f-prod-qty").value.trim() || "0",
  };
}

// Click a product row to edit (or delete) it.
$("inventory-body").addEventListener("click", (e) => {
  const tr = e.target.closest("tr[data-id]");
  if (!tr) return;
  if (profile && profile.pending) { alert("Inventory is locked until an administrator activates your store."); return; }
  const p = inventoryCache.find((x) => String(x.id) === String(tr.dataset.id));
  if (p) openProductModal(p);
});

function openProductModal(p) {
  const store = inventoryStorePayload();
  openModal("Product \u2022 " + (p.name || p.sku),
    productFormHtml(p) + '<button id="f-prod-delete" class="btn-mini danger" style="margin-top:12px">Delete product</button>',
    async () => {
      const fields = readProductForm();
      if (!fields.sku || !fields.name) throw new Error("SKU and name are required.");
      await api("/inventory", { method: "POST", body: JSON.stringify(Object.assign({ action: "update", id: p.id }, fields, store)) });
      loadInventory();
    });
  const del = $("f-prod-delete");
  if (del) del.addEventListener("click", async () => {
    if (!confirm("Delete this product?")) return;
    try {
      await api("/inventory", { method: "POST", body: JSON.stringify(Object.assign({ action: "delete", id: p.id }, store)) });
      closeModal();
      loadInventory();
    } catch (err) { $("modal-error").textContent = err.message; }
  });
}

// Add a brand-new product from the web.
$("add-product-btn").addEventListener("click", () => {
  if (profile && profile.pending) { alert("Inventory is locked until an administrator activates your store."); return; }
  const store = inventoryStorePayload();
  openModal("New product", productFormHtml({}), async () => {
    const fields = readProductForm();
    if (!fields.sku || !fields.name) throw new Error("SKU and name are required.");
    await api("/inventory", { method: "POST", body: JSON.stringify(Object.assign({ action: "create" }, fields, store)) });
    loadInventory();
  });
});

// =================== MODAL ===================
let modalOk = null;
function openModal(title, bodyHtml, onOk) {
  $("modal-title").textContent = title;
  $("modal-body").innerHTML = bodyHtml;
  $("modal-error").textContent = "";
  modalOk = onOk;
  $("modal").classList.remove("hidden");
}
function closeModal() { $("modal").classList.add("hidden"); modalOk = null; }
$("modal-cancel").addEventListener("click", closeModal);
$("modal-ok").addEventListener("click", async () => {
  if (!modalOk) return closeModal();
  try { await modalOk(); closeModal(); }
  catch (err) { $("modal-error").textContent = err.message; }
});

// =================== STARTUP: restore remembered session ===================
(async function init() {
  if (!token) return;
  try {
    const me = await api("/me");
    profile = me;
    profile.token = token;
    afterLogin();
  } catch (e) {
    clearToken();
  }
})();
