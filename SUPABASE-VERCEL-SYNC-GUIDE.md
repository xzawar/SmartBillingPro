# Smart Billing Pro - Cloud Sync, Supabase & Vercel Guide

This guide explains **exactly** what to do, step by step, to put your desktop app
online so that:

- every store's database syncs to **Supabase** when the computer has internet,
- a **website** (hosted on **Vercel**) shows and controls the same data,
- the **administrator** can enable/disable stores and create accounts from the web,
- those accounts can then **log in to the desktop app**.

You do **not** need to be a programmer to follow this. Take it slowly, one part
at a time.

> **Important honesty note:** The cloud backend (the `cloud/` folder) and the
> desktop sync code are ready to deploy, but I could not compile, host or test
> them inside this tool (no internet, no Java/Node runtime here). Treat the first
> run as a real test: follow the steps, and if something errors, the error text
> will tell you what to fix. Nothing here changes how the app works **offline** -
> if you never set up the cloud, the app behaves exactly as before.

---

## 0. The big picture (read this first)

There are **three** pieces:

| Piece | What it is | Where it runs |
|-------|------------|---------------|
| **Desktop app** | Your Java Swing app (`.exe`) | Each shop's computer |
| **Cloud API + Website** | The `cloud/` folder (Node.js functions + a small website) | **Vercel** |
| **Database** | **Supabase** (a hosted PostgreSQL database) | Supabase cloud |

**They talk like this:**

```
 Desktop app  <-- HTTPS -->  Vercel API (/api/*)  <-- SQL -->  Supabase database
                                   ^
 Website (browser)  <-- HTTPS -----+
```

The key idea: **neither the desktop app nor the website talks to Supabase
directly.** They both call the **Vercel API**, and only the Vercel API holds the
secret Supabase key. This keeps your database safe.

---

## 1. Your GitHub / Vercel question, answered directly

> *"Should I upload the whole project to GitHub to deploy the web version on
> Vercel, or only the html/css/java files? And how does the sync work between
> them?"*

**Short answer:**

- **Do NOT deploy the Java files to Vercel.** Vercel cannot run a Java desktop
  app. Java is your *desktop* program; it is shipped as the `.exe`, not to the web.
- **Only the `cloud/` folder gets deployed to Vercel.** That folder contains:
  - `api/` - the Node.js functions (login, sync, admin, etc.),
  - `public/` - the website (HTML, CSS, JS),
  - `package.json`, `vercel.json` - configuration.
- You **can** push the *whole* project to GitHub (it's fine to have the Java code
  in the same repository). When you connect that repo to Vercel, you just tell
  Vercel: **"the project lives in the `cloud` folder"** (the *Root Directory*
  setting - see Part 4). Vercel then ignores the Java and only builds `cloud/`.

**So:** one GitHub repo for everything is simplest. Vercel only looks at `cloud/`.

**How the sync works between them (plain words):**

1. When a shop's computer is online, the desktop app every few seconds sends its
   new/changed rows up to the Vercel API, which writes them into Supabase.
2. The same app also asks the Vercel API "what changed since last time?" and pulls
   those changes down into its local database.
3. The website reads/writes the **same** Supabase data through the **same** Vercel
   API, so anything the admin/owner does on the web shows up in the app on the
   next sync, and anything done in the app shows up on the web.
4. If two places change the same row, the **most recent change wins** (by time).

---

## 2. Set up Supabase (the database)

1. Go to **https://supabase.com** and create a free account.
2. Click **New project**.
   - **Name:** `smart-billing-pro` (anything you like).
   - **Database password:** choose a strong one and save it somewhere safe.
   - **Region:** pick the one closest to you (e.g. *South Asia (Mumbai)*).
3. Wait ~2 minutes for the project to finish setting up.
4. In the left menu open **SQL Editor** -> **New query**.
5. Open the file `cloud/supabase-schema.sql` from this project, copy **all** of it,
   paste it into the SQL editor, and click **Run**.
   - This creates every table the app needs and a default **admin** account.
6. Now collect two values you'll need later. Open **Project Settings** (gear icon)
   -> **API**:
   - **Project URL** - looks like `https://abcdxyz.supabase.co`. This is your
     `SUPABASE_URL`.
   - **service_role key** (under *Project API keys*) - a long secret string. This
     is your `SUPABASE_SERVICE_ROLE`.

> **NEVER** put the `service_role` key in the website, the desktop app, chat, or
> GitHub. It goes **only** into Vercel's environment variables (Part 4). It is a
> master key to your database.

**Default admin login (created by the schema):**

- Username: `admin`
- Password: `admin123`  *(change this after your first web login)*

---

## 3. Put the code on GitHub

1. Create a free account at **https://github.com** if you don't have one.
2. Create a **new repository**, e.g. `smart-billing-pro` (Private is fine).
3. Upload the project. Easiest non-technical way:
   - On the new repo page click **"uploading an existing file"**.
   - Drag in the project folder contents (the whole `SmartBillingPro` folder is
     fine, including `cloud/`).
   - Click **Commit changes**.
   *(If you know Git, just `git init`, `git add .`, `git commit`, `git push`.)*

That's all GitHub is for: a place Vercel can read your `cloud/` folder from.

---

## 4. Deploy the API + website on Vercel

1. Go to **https://vercel.com** and sign up **with your GitHub account**.
2. Click **Add New... -> Project**, and **Import** your `smart-billing-pro` repo.
3. **VERY IMPORTANT - set the Root Directory:**
   - On the configure screen find **Root Directory** -> **Edit** -> choose the
     **`cloud`** folder.
   - This tells Vercel to build only the `cloud/` folder (the API + website) and
     ignore the Java desktop code.
4. Open **Environment Variables** and add these (from Part 2):

   | Name | Value |
   |------|-------|
   | `SUPABASE_URL` | your Project URL, e.g. `https://abcdxyz.supabase.co` |
   | `SUPABASE_SERVICE_ROLE` | your service_role secret key |
   | `JWT_SECRET` | any long random text you invent (e.g. 40+ characters) |

5. Click **Deploy** and wait ~1 minute.
6. When it finishes, Vercel gives you a URL like
   **`https://smart-billing-pro.vercel.app`**. That is your live system.
   - Open it in a browser - you should see the **Smart Billing Pro** login page.
   - Log in with `admin` / `admin123` to reach the **web admin console**.

> If you change environment variables later, click **Redeploy** for them to take
> effect.

---

## 5. Point the desktop app at your cloud

On each shop computer, set **one** environment variable so the app knows where
your cloud lives:

- **Name:** `SMARTBILLING_CLOUD_API`
- **Value:** your Vercel URL **without** a trailing slash, e.g.
  `https://smart-billing-pro.vercel.app`

**On Windows (simplest):**

1. Press Start, type *"environment variables"*, open
   **"Edit the system environment variables"**.
2. Click **Environment Variables...** -> under *User variables* click **New**.
3. Name = `SMARTBILLING_CLOUD_API`, Value = your Vercel URL. Click OK.
4. **Restart** the app.

When this is set, the login screen shows two extra buttons:
**"Sign in online (new device)"** and **"Create a store"**. If it is not set, the
app simply runs offline as before.

---

## 6. The day-to-day flow

### A brand-new shop
1. On the shop computer, open the app -> **Create a store**.
2. Fill in store details + the owner's username/password -> submit.
   - The store and owner are created in the cloud but **disabled**, waiting for
     the administrator.
3. The **administrator** opens the website, goes to **Stores**, finds the new
   store and clicks **Activate**. This enables the store and its owner account.
4. Back in the app the owner clicks **Sign in online (new device)**. The app
   downloads that store's data and opens. From now on it also works offline.

### Another device for the same shop (e.g. a cashier's PC)
1. The owner/admin creates the cashier or manager account (in the app once
   online, or on the website).
2. On the new computer, set `SMARTBILLING_CLOUD_API`, open the app, type the
   account's username/password, and click **Sign in online (new device)**.
3. The app downloads **only that store's** data locally. After that, any account
   of the same store can sign in on that computer **without** needing the internet
   each time, because the store's accounts and data are stored locally.

### Everyday syncing
- Whenever a computer is online, the app pushes new sales/products/etc. up and
  pulls down anything changed elsewhere - **every few seconds**.
- Data covered: products, invoices, customers, suppliers, users, settings,
  stores, purchase orders and the audit log - **everything**.

### What the website can do
- **Administrator:** see all stores, **enable/disable** a store (disabling blocks
  all of that store's accounts), **activate** new stores, and create/disable/reset
  accounts.
- **Owner / Manager (on the web):** view dashboards and records, generate/check
  reports, and update settings their role is allowed to change.
- By design, the **web does not create new sales or change stock** - selling and
  inventory changes happen in the desktop app; the web is for oversight and
  control. The app then syncs those records up for the web to display.

---

## 7. Conflict rule (important to understand)

If the same record is edited in two places before they sync, the app keeps the
**most recently edited version** (compared by timestamp). This is called
*last-write-wins*. It keeps things simple; just be aware that an older edit can be
overwritten by a newer one.

---

## 8. Quick troubleshooting

| Symptom | Likely cause / fix |
|--------|--------------------|
| App shows no cloud buttons | `SMARTBILLING_CLOUD_API` not set, or app not restarted. |
| "Network error" on sign-in | Wrong Vercel URL, or no internet. Check the URL has no trailing slash. |
| Website 500 error on login | Env vars missing/incorrect in Vercel, or schema SQL not run. Redeploy after fixing. |
| Owner can't sign in | Store/owner not **Activated** by the admin yet. |
| Changes not appearing | Give it a few seconds; confirm the computer is online. |

---

## 9. Files reference (where things live)

```
cloud/
  supabase-schema.sql      <- run this once in Supabase SQL editor
  package.json             <- Node dependencies for the API
  vercel.json              <- Vercel config (CORS, routing)
  api/                     <- the server functions
    _lib.js                <- shared helpers (DB client, auth, tokens)
    login.js               <- POST /api/login
    register-store.js       <- POST /api/register-store
    me.js                  <- GET  /api/me (restore session)
    sync/pull.js           <- GET  /api/sync/pull   (download changes)
    sync/push.js           <- POST /api/sync/push   (upload changes)
    admin/stores.js        <- list / enable / disable / activate stores
    accounts.js            <- create / disable / reset accounts
    summary.js             <- dashboard numbers
    settings.js            <- read / update settings
  public/                  <- the website (index.html, styles.css, app.js)

src/com/smartbillingpro/
  util/Json.java           <- tiny JSON reader/writer (no libraries)
  util/CloudConfig.java    <- stores the API URL + saved session
  util/CloudClient.java    <- makes HTTPS calls to the API
  service/SupabaseSync.java <- the two-way sync engine (push/pull loop)
  service/CloudAccess.java  <- online sign-in + create-store helpers
```

---

## 10. Security checklist

- [ ] `service_role` key is **only** in Vercel env vars (never in app/website/GitHub).
- [ ] You changed the default `admin` password after first login.
- [ ] `JWT_SECRET` is a long random value you keep private.
- [ ] Your GitHub repo can be private if you prefer.

That's everything. Set up Supabase (Part 2), push to GitHub (Part 3), deploy to
Vercel with the `cloud` root directory and env vars (Part 4), point the app at it
(Part 5), and you're online.
