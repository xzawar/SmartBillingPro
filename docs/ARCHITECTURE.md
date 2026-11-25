# Architecture & migration notes

## 1. Assessment of the previous version (Phase 1)

| Area | Found | Decision |
|---|---|---|
| Stack | Java 17 Swing desktop app, SQLite via `sqlite-jdbc`, plain `javac` scripts, `jpackage` for the `.exe` | **Kept** (no stack change) |
| Online layer | `SupabaseSync` (sync triggers + extra columns inside the DB file), `CloudAccess`, `CloudClient`, `CloudConfig`, `CloudSync`, `LiveSync`, `EnvSetup` (wrote env vars on every start), `RememberMe`; `cloud/` Vercel API + Supabase schema; `web/` console | **Removed completely** |
| Tenancy | Stores, admin/owner/manager/cashier logins, `StoreSession`, store activation lock, staff screens, recycle bin, three launchers | **Removed** – one shop, one window, optional PIN |
| Data model | Money stored as TEXT (SQL `SUM` became floating point); integer-only quantities (no weight selling); no variations, categories table, ledger, partial payments, stock history, purchase payments, expenses | **Replaced** by a new schema (below) |
| Integrity issues | Purchases could be hard-deleted and edited; barcodes not unique; a sale could be printed/saved twice; receipts were a 32-column `JTextArea` print; no backups | **Fixed** by design (transactions, constraints, triggers, tokens, layout engine, backup service) |
| Committed artefacts | `out/` classes, `build/`, `app-image-admin/` with an `.exe`, a stale duplicate `SmartBillingPro/` tree | **Removed**, now ignored |
| Reused ideas | Layered model → repository → service → UI; BigDecimal discipline; `Database.tx`; receipt text layout; vector icons; tiny JSON helper | Carried over and extended |

## 2. Layers

```
ui (Swing/FlatLaf)  →  service (business rules, transactions)  →  repo (SQL)  →  db (SQLite)
                         ↘ print (layout → Java2D / PDF / ESC-POS)
```

* UI never writes SQL; services own every rule and every transaction.
* All database work runs on the Swing thread through one connection
  (simple, race-free). Backups use their own connection on a background
  thread (`VACUUM INTO` reads a consistent snapshot while sales continue).
* A file lock (`app.lock`) allows only one running copy per data folder.

## 3. Database

`src/com/smartbillingpro/db/Migrations.java` — versioned with
`PRAGMA user_version`; each migration runs once in a transaction; a safety
copy is written before any future upgrade. Pragmas: `foreign_keys=ON`,
`journal_mode=WAL`, `synchronous=FULL` (a committed sale survives a power cut).

* **Money** = INTEGER minor units (paisa). **Quantities** = INTEGER
  thousandths of the product unit (1 pc = 1000, 0.250 kg = 250). All sums are
  exact integer maths; rounding is HALF_UP in one place (`Money`).
* Tables: `settings`, `sequences` (gap-free numbers), `units`,
  `payment_methods`, `categories`, `customers`, `suppliers`, `products`,
  `product_variants` (barcode **UNIQUE**, SKU unique, `stock_qty >= 0`),
  `stock_movements`, `sales`, `sale_items`, `sale_returns`,
  `sale_return_items`, `held_sales`, `purchases`, `purchase_items`,
  `payments`, `ledger_entries`, `expenses`, `audit_log`, `app_meta`.
* CHECK constraints: e.g. `amount_paid + balance_due = grand_total`,
  `balance_due = 0 OR customer_id IS NOT NULL`, line total = net + tax,
  returned qty ≤ sold qty.
* **Append-only financial history** — triggers reject `UPDATE`/`DELETE` on
  ledger entries, payments, stock movements and returns, `DELETE` on sales,
  purchases and expenses, and edits to the amounts of sales, purchases and
  expenses. Corrections are new records: returns, purchase reversals,
  payment reversals, ledger adjustments, voided expenses.

### Key flows (each one transaction)

* **Sale** – re-reads every product, recomputes totals, takes the next
  receipt number, writes sale + lines (with cost snapshot), decrements stock
  (fails atomically if stock ran out), records the payment and the customer
  ledger (sale debit, payment credit). A per-cart UUID (`client_token`,
  UNIQUE) makes a double-submitted sale impossible.
* **Return** – proportional value (incl. discount share and tax), stock back,
  cash refund and/or account credit, sale status updated.
* **Purchase** – stock in, moving-average cost, supplier ledger (bill credit,
  payment debit). Reversal takes stock back out and debits the ledger.
* **Profit** = Σ net line amounts − Σ cost snapshot, minus returns (on the
  return date); tax is excluded from revenue; net profit subtracts expenses.

## 4. Backups

`BackupService`: `VACUUM INTO` → integrity check of the new file → atomic
rename → SHA-256 sidecar. Daily automatic backup (checked every 30 min and
on exit), retention of the newest *N* automatic backups, manual backups to
any folder. Restore = validate (header, `PRAGMA integrity_check`, app id,
schema version, required tables, checksum) → safety copy → close → swap file
→ reopen; roll back automatically if the restored file cannot be opened.
Backup settings and status live in `config.properties` (outside the DB) so a
restore never rewinds them.

## 5. Printing & PDF

A receipt/report is laid out once against `DocCanvas` and drawn by
`Graphics2DCanvas` (screen preview, Windows printer driver) or `PdfCanvas`
(PDFBox, Inter embedded). Roll layout works for any printable width (tested
48/58/72/80 mm); `EscPosReceipt` produces raw text for 32–48 char printers;
`TableLayout` paginates reports A4 portrait/landscape.

## 6. Design system

Applied from the supplied design reference (Wise "Neptune" design DNA + the
two dashboard references). Tokens live only in `ui/Theme.java`:

* One bright accent (lime `#C8EB52`) for the single primary action and the
  active navigation item, with deep same-hue ink `#2B3A06` for text on it
  (Wise's own green pair is a brand asset and was deliberately not copied).
* Warm near-black ink `#0E0F0C`, greys `#454745 / #6A6C6A`, white surfaces on
  a warm tint, 12 % borders; separation by tint, no shadows.
* Semantic status colours with labels: positive `#054D28/#E2F6D5`, negative
  `#CB272F/#FBEAEA`, warning `#4A3B1C/#FFF7D7`, info `#054D4D/#E0F7F7`.
* Inter (bundled, SIL OFL) in Regular + SemiBold; numbers as heroes.
* 16 px radius cards, pill buttons and chips, 2 px geometric icons on a 24 px
  grid, pastel tinted summary cards with solid icon tiles, smooth line charts
  (validated violet/aqua pair, legend + hover tooltips).

### Interaction patterns

* **One question at a time** — `ui/Wizard` shows a single question per
  screen with inline validation, a progress line, *Skip* where an answer is
  optional and a closing summary whose *Change* links jump to one question
  and come back. Used by `ui/flows/ProductFlow` (add / edit product),
  `StockFlow` (change stock with a reason) and `ShopSetupFlow` (first start).
  Rare details are only offered from the summary.
* **Short forms** — `FormDialog` shows problems inline above its buttons;
  customer, supplier, purchase and expense forms ask only the essentials and
  keep the rest under a `Collapsible` "more details".
* **Lists, not tables or cards, for things you pick** — `RowList` (avatar,
  two lines, value and status) with a friendly `EmptyState` and a "no
  matches" state; master–detail screens use `ui/Detail` (header, tiles,
  sections). The till's bill is a `CartList` with − / + / × on every line.
* **Choices as taps** — `PillGroup` (payment method, filters) and
  `OptionList` (big one-tap answers inside flows).
* **Charts from real data only** — `LineChart` (line or bar mode) and
  `BarList`, fed by `DashboardService` queries (daily product sales, monthly
  customer / supplier totals, payment split, expenses by category) that a
  test checks against the recorded transactions.

### Installer and computer lock

`make-installer.bat` runs `packaging/make-installer.ps1`: tests → compile →
jar → `jpackage` app image with its own Java runtime → Inno Setup
(`packaging/installer/SmartBillingPro.iss`) → `dist/SmartBillingPro-Setup-<version>.exe`.
The installer shows `resources/legal/terms.txt` (must be accepted), installs
for all users, adds shortcuts and an uninstaller, and then runs
`SmartBillingPro.exe --bind-device <token>`.

`app/DeviceLock` writes `device.lic` into the installation folder: SHA-256
hashes of the Windows machine GUID and hardware UUID plus an HMAC. Every
build has its own random token; only its SHA-256 is inside the app, so the
installed files alone cannot register another computer. At start-up the app
reads the IDs again and refuses to run when the files were copied to another
computer, the registration was edited, or it is missing (`--check-device`
reports the state). The hardware ID is only compared when it could be read
both times, so a limited Windows account never locks out the real laptop.
This stops copying the installed app; like any purely offline lock it
cannot stop someone who runs the installer again on another computer or
who reverse-engineers the program — the terms of use cover that.

## 7. Testing

`test/` holds a dependency-free runner (`run-tests.bat` / `./run-tests.sh`)
with 24 tests on real temporary databases: money/quantity maths, weight
conversion, cart discounts/tax allocation, products/variations/barcode
uniqueness, stock adjustments and immutability triggers, cash/credit/partial
sales, rollback when stock runs out, duplicate-sale protection, returns,
held sales, purchases & supplier ledger, report and dashboard figures,
persistence across restarts, backup/validate/restore, corrupt-backup
rejection, daily backup + retention, backups during concurrent sales,
legacy import, receipt fit at 48/58/80 mm, ESC/POS line widths, A4/roll PDFs
(parsed back with PDFBox), multi-page receipts, PDF + CSV for all 25 reports,
and the chart figures. The UI was exercised under a virtual display with a
simulated USB scanner (fast keystrokes + Enter), the weight dialog, the
keyboard-only add-product flow, the unknown-barcode flow and F12 checkout,
and every screen was checked at 1024×640, 1366×768 and 1920×1080; the packaged app was launched inside a network namespace with
no network at all.

## 8. Known limitations

* The Windows installer must be built on Windows (`make-installer.bat`, or
  the GitHub Actions workflow) — `jpackage` only targets the OS it runs on —
  and needs Inno Setup 6. It is not code-signed, so Windows SmartScreen asks
  for confirmation on first run.
* Physical thermal printers and scanners could not be tested here; the
  printing paths use standard Java printing / raw ESC/POS and were verified
  through rendering and byte output. Use *Print test receipt* on site.
* Sales history from the old online version is not imported (master data and
  stock are); the old database file is left untouched.
* Complex scripts (e.g. Urdu) are not shaped in PDFs/receipts; Latin text is
  fully supported.
* Quantities have 3 decimals in the product's unit (selling mg of a per-kg
  product needs the product to be set up per g).
* Single computer by design (no multi-till network sharing).
