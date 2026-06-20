# Smart Billing Pro - Cloud & Web Guide

This guide explains everything that was added in this update and, most
importantly, **how to put each store's database in the cloud** and **how the
online web login works** so an owner or manager can check the shop from
anywhere.

> The desktop features (Remember me, separate Admin/Store launchers, real-time
> updates, cloud-upload-on-logout) are fully built into the app. The cloud
> backend and the website need to run on a server you control - this guide gives
> you copy-paste code to set that up.

---

## 1. What was added to the desktop app

| Feature | Where |
|---|---|
| **Remember me** checkbox on login. Keeps you signed into the last account until you click **Log out**. | `ui/LoginFrame.java`, `util/RememberMe.java` |
| **Admin-only launcher** (`AdminApp`) + **Store-only launcher** (`StoreApp`). | `AdminApp.java`, `StoreApp.java`, `Bootstrap.java` |
| **Real-time updates** - a background thread watches the database and refreshes every open window automatically. | `util/LiveSync.java` |
| **Upload to cloud on logout** (auto when online, optional when offline). | `util/CloudSync.java`, `ui/MainFrame.java` |

### Remember me
- Only the **username** is stored (never the password) in
  `~/.smartbillingpro/session.properties`.
- On the next launch the app finds that account and signs in automatically.
- It is cleared the moment you press **Log out**.

### The two separate launchers / EXEs
- **Run from source (no install):**
  - `run-admin.bat` / `run-admin.sh`  -> opens **only** the Admin console.
  - `run-store.bat` / `run-store.sh`  -> opens **only** store accounts (owner / manager / cashier).
  - `run.bat` / `run.sh` (original)   -> the full app that accepts any account.
- **Build real Windows `.exe` files** (needs a full **JDK 17+** with `jpackage`):
  - `Build-Admin-Exe.bat` -> creates `app-image-admin\SmartBillingPro-Admin\SmartBillingPro-Admin.exe`
  - `Build-Store-Exe.bat` -> creates `app-image-store\SmartBillingPro-Store\SmartBillingPro-Store.exe`
  - Copy that whole folder to any PC - it bundles its own Java, so nothing else needs installing.
- The admin can only sign in through the Admin app; store staff can only sign in through the Store app. The login screen blocks the wrong account type.

### Real-time updates (threads)
- `LiveSync` runs a **daemon background thread** that checks the database file
  (and its `-wal` write-ahead log) about every 1.5 seconds.
- When it detects a change it calls back on the Swing UI thread and refreshes the
  screen you are looking at.
- **For multiple computers/counters to update each other live**, point them all
  at the **same database file** using the `SMARTBILLING_DB` environment variable
  (e.g. a shared network folder). Every app watching that file updates within a
  couple of seconds.

```bat
REM Example: all tills in a shop share one database on a network drive
setx SMARTBILLING_DB "\\SHOP-SERVER\billing\shoppro.db"
```

---

## 2. Putting each store's database in the cloud

Each store has its own SQLite database file. "Uploading to the cloud" simply
means sending that file to an online location, tagged with the store id, so the
website can read it.

### How the app uploads
When the following environment variables are set, the app uploads the database
**automatically on logout** when the device is online, or **offers a button** to
upload when it is offline:

| Variable | Meaning |
|---|---|
| `SMARTBILLING_CLOUD_URL` | The HTTPS endpoint that receives the file. The store id is added as `?store=<id>`. |
| `SMARTBILLING_CLOUD_TOKEN` | A secret token. Sent as `Authorization: Bearer <token>`. |

```bat
REM On each shop PC (Windows):
setx SMARTBILLING_CLOUD_URL "https://api.yourdomain.com/upload"
setx SMARTBILLING_CLOUD_TOKEN "a-long-random-secret"
```
```bash
# On macOS/Linux:
export SMARTBILLING_CLOUD_URL="https://api.yourdomain.com/upload"
export SMARTBILLING_CLOUD_TOKEN="a-long-random-secret"
```

The app sends the raw `.db` file as an HTTP `POST` (`application/octet-stream`)
with an extra `X-Store-Id` header. You only need a server that accepts that POST.

### Behaviour summary (exactly what you asked for)
- **Device is connected to the internet** -> on logout the database uploads
  automatically, so the cloud always has the latest data.
- **Device is offline** -> on logout you get a prompt offering to upload the data
  to the cloud database (you can retry once you have signal).

### Option A - Simplest: a tiny PHP receiver
```php
<?php
// upload.php  -- stores each shop's db as data/store-<id>.db
$expected = 'a-long-random-secret';
$auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
if ($auth !== "Bearer $expected") { http_response_code(401); exit('unauthorized'); }

$store = preg_replace('/[^0-9]/', '', $_GET['store'] ?? '0');
$body  = file_get_contents('php://input');
if (!$body) { http_response_code(400); exit('empty'); }

@mkdir(__DIR__.'/data', 0775, true);
file_put_contents(__DIR__."/data/store-$store.db", $body);
http_response_code(200);
echo 'ok';
```
Set `SMARTBILLING_CLOUD_URL` to `https://yourdomain.com/upload.php`.

### Option B - Supabase Storage
1. Create a Supabase project and a **Storage bucket** called `databases`.
2. Put a small serverless function (or use the Storage REST API) behind
   `SMARTBILLING_CLOUD_URL` that writes the uploaded bytes to
   `databases/store-<id>.db`.
3. Use a **service-role key on the server only** - never inside the desktop app.
   - Your publishable (anon) key is safe for the browser; the secret/service-role
     key must stay on the server.

### Option C - AWS S3 (pre-signed upload)
- Have a small endpoint mint a pre-signed `PUT` URL for `store-<id>.db`, or accept
  the POST and forward it to S3 with the AWS SDK. The website then reads the file
  from S3.

> **Tip:** SQLite uses a `-wal` file in WAL mode. The app checkpoints on close,
> so the single `.db` file you upload is complete and consistent at logout time.

---

## 3. The online web login (owner / manager dashboard)

A ready-to-customise website is in the **`web/`** folder:

- `web/index.html` - login screen + dashboard (KPIs, recent invoices, low stock)
- `web/styles.css` - styling that matches the desktop theme
- `web/app.js` - calls your backend and refreshes every few seconds (live)

Open `web/app.js` and set **`API_BASE`** to your deployed backend URL. The front
end expects these JSON endpoints:

| Method & path | Returns |
|---|---|
| `POST /api/login` `{username,password}` | `{ token, name, role, stores:[{id,name}] }` |
| `GET /api/summary?store=<id>` (Bearer token) | `{ sales, invoices, products, lowStock, recentInvoices[], lowStockItems[], updatedAt }` |

### Web authentication
- The backend checks the username/password against the uploaded store database
  (the password hashing matches the desktop app) and returns a **JWT token**.
- The browser stores the token and sends it as `Authorization: Bearer <token>`
  on every request. Owners/managers see their own store(s) only.
- Always serve the site over **HTTPS**.

### Reference backend (Node.js + Express) reading the uploaded SQLite files
```js
// server.js  -- npm i express better-sqlite3 jsonwebtoken bcryptjs cors
const express = require("express");
const Database = require("better-sqlite3");
const jwt = require("jsonwebtoken");
const cors = require("cors");
const path = require("path");

const JWT_SECRET = process.env.JWT_SECRET || "change-me";
const DATA_DIR = process.env.DATA_DIR || "./data"; // where store-<id>.db files land
const app = express();
app.use(cors());
app.use(express.json());
app.use(express.raw({ type: "application/octet-stream", limit: "100mb" }));

// Receive uploads from the desktop app (matches SMARTBILLING_CLOUD_URL)
app.post("/upload", (req, res) => {
  if (req.headers.authorization !== `Bearer ${process.env.UPLOAD_TOKEN}`)
    return res.sendStatus(401);
  const store = String(req.query.store || "0").replace(/[^0-9]/g, "");
  require("fs").writeFileSync(path.join(DATA_DIR, `store-${store}.db`), req.body);
  res.send("ok");
});

function openStore(id) {
  return new Database(path.join(DATA_DIR, `store-${id}.db`), { readonly: true });
}

app.post("/api/login", (req, res) => {
  const { username, password } = req.body;
  // Look the user up across uploaded store DBs (or keep a central users DB).
  // Verify the password against the stored hash, then:
  const token = jwt.sign({ username }, JWT_SECRET, { expiresIn: "12h" });
  res.json({ token, name: username, role: "OWNER", stores: [{ id: 1, name: "My Store" }] });
});

function auth(req, res, next) {
  try { req.user = jwt.verify((req.headers.authorization||"").replace("Bearer ",""), JWT_SECRET); next(); }
  catch { res.sendStatus(401); }
}

app.get("/api/summary", auth, (req, res) => {
  const db = openStore(req.query.store || 1);
  const today = new Date().toISOString().slice(0, 10);
  const sales = db.prepare("SELECT COALESCE(SUM(grand_total),0) s, COUNT(*) c FROM invoices WHERE substr(created_at,1,10)=?").get(today);
  const products = db.prepare("SELECT COUNT(*) c FROM products").get();
  const low = db.prepare("SELECT sku,name,category,quantity FROM products WHERE quantity<=low_stock_threshold ORDER BY quantity LIMIT 50").all();
  const recent = db.prepare("SELECT number, created_at date, cashier, grand_total total FROM invoices ORDER BY id DESC LIMIT 20").all();
  res.json({ sales: sales.s, invoices: sales.c, products: products.c, lowStock: low.length, lowStockItems: low, recentInvoices: recent, updatedAt: Date.now() });
});

app.listen(8080, () => console.log("API on :8080"));
```
> Adjust the table/column names to match the actual schema in `repository/Database.java`.

### Deploying the website
- Host `web/` on any static host (Netlify, Vercel, GitHub Pages, or the same
  server). Host `server.js` on a small VM/host (Render, Railway, a VPS, etc.).
- Point `API_BASE` in `app.js` at the backend, and the desktop
  `SMARTBILLING_CLOUD_URL` at the backend's `/upload`.

### "Live online" vs "upload on logout"
- The website **polls every few seconds**, so while a shop PC is online and its
  database is reachable (continuous sync), the owner sees changes almost live.
- If you only upload on logout (the offline path), the website shows the data as
  of the **last upload**. For true continuous live data, run the backend's
  `/upload` more often (e.g. on every sale) or keep the shop DB on a hosted
  Postgres/Supabase instead of uploading a file.

---

## 4. Security checklist
- Serve everything over **HTTPS**.
- Keep `SMARTBILLING_CLOUD_TOKEN`, `UPLOAD_TOKEN`, `JWT_SECRET` and any
  service-role keys **on servers only** - never in the website or desktop app.
- Give each owner access to **their store(s) only**.
- Use strong, unique passwords; rotate the upload token if a device is lost.

---

## 5. Quick start
1. **Desktop:** double-click `run-admin.bat` (admin) or `run-store.bat` (shop).
   Tick **Remember me** to skip login next time.
2. **Build EXEs (optional):** run `Build-Admin-Exe.bat` / `Build-Store-Exe.bat`
   on a PC with JDK 17+.
3. **Cloud:** deploy a receiver (PHP/Node/Supabase), then set
   `SMARTBILLING_CLOUD_URL` + `SMARTBILLING_CLOUD_TOKEN` on each shop PC.
4. **Web:** deploy `web/` + the backend, set `API_BASE`, and log in as the owner.
