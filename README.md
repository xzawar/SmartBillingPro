# Smart Billing Pro — Offline POS

*Developed by KeyKraft.*

A fast, fully **offline** point-of-sale and shop management app for Windows.
One shop, one owner, one window. All data stays on the computer in a local
SQLite database, with automatic daily backups. No internet connection, no
online account and no server are needed.

| | |
|---|---|
| **Sales (POS)** | Barcode scanning (USB scanners work anywhere in the window), search, category filters, a simple product list, sizes/variations, sale by weight/volume (e.g. 250 g of a per-kg product), line & bill discounts, tax, hold/recall, cash/bank/other payment methods, change and part-payment on a customer's account, keyboard shortcuts |
| **Inventory** | Add products one question at a time (name → how it is sold → sizes → barcode → price → cost → stock → category), sizes/variations (own barcode, SKU, prices, stock each), units (pcs, kg, g, mg, L, ml, m, cm + custom), duplicate-barcode protection, pictures, low/out-of-stock alerts, "Change stock" with plain reasons, full stock history, a 30-day sales chart per product, hide instead of delete |
| **Suppliers** | Supplier accounts, purchases (full / partial / credit), moving-average cost, purchase reversal, payments, statements |
| **Ledgers** | Customer and supplier ledgers with running balances, payments, adjustments, reversals, PDF/Excel/print statements, expenses |
| **Customers** | Name + phone is all that is needed; balance, monthly purchases chart, recent receipts, receive payments; walk-in sales need no customer |
| **Receipts** | Every sale stored; search by number/customer/date/method/status; preview, reprint, **PDF** (A4 or roll), **58 / 80 mm thermal** (driver or ESC/POS), returns & refunds |
| **Reports** | 25 reports: daily/weekly/monthly/product/category/payment-method sales, returns, P&L, daily/monthly/product profit, stock, low/out of stock, valuation, movements, purchase history, customer/supplier balances, cash received, credit sales, payments made, cash book, expenses — all with date filters, search, PDF, CSV (Excel) and print |
| **Dashboard** | "Get your shop ready" checklist for new shops; today's sales & profit, running low, receivables, payables, stock value; charts of sales, profit vs expenses, how customers paid, best sellers, category sales and stock — all from real data |
| **Tools & Settings** | Store details, currency (PKR by default), date format, tax, receipt texts & numbering, printer setup + test print, payment methods, units, backup & restore, optional startup PIN, import from the previous version |

## Install on Windows (no Java needed)

Use the installer **`SmartBillingPro-Setup-4.0.0.exe`**. Get it from the
latest *Build Windows installer* run in the repository's **Actions** tab
(artifact *SmartBillingPro-Setup*), or make it yourself with
`make-installer.bat` (below).

Copy the installer to the shop's laptop and run it there:

1. Accept the **terms of use** (the installer will not continue otherwise).
2. Click through the wizard. Everything the app needs, including its own Java
   runtime, is inside the installer: no internet connection and no other
   software is needed.
3. The installer **registers that laptop**. The installed app only runs on
   it: if its files are copied to another computer, the app refuses to start
   there and says why.

Windows may show *"Windows protected your PC"* because the installer is not
code-signed: click **More info** › **Run anyway**.

Your data is stored in `%LOCALAPPDATA%\SmartBillingPro` and backups in
`Documents\SmartBillingPro Backups` (changeable in *Tools & Settings*).
Uninstalling keeps the data. To move to a new laptop, install the app there
and restore your latest backup.

## Build it yourself

Requires a JDK 17 or newer (e.g. [Temurin 21](https://adoptium.net)). All
libraries are already in `lib/` and `resources/`.

| Task | Windows | macOS / Linux |
|---|---|---|
| Run from source | `run.bat` | `./run.sh` |
| Run the tests | `run-tests.bat` | `./run-tests.sh` |
| Make the Windows installer (`dist\SmartBillingPro-Setup-4.0.0.exe`, needs Inno Setup 6) | `make-installer.bat` | — |
| Build a portable Java bundle (+ native image for the current OS) | — | `./build.sh` |

`make-installer.bat` (double-click it) checks for a JDK 17+ and
[Inno Setup 6](https://jrsoftware.org/isdl.php) and offers to install them
with winget if they are missing, runs the tests, packages the app with its
own Java runtime and creates the installer.

The GitHub Actions workflow `.github/workflows/build-windows.yml` runs the
tests and builds the installer on every push, then installs it silently on
the build machine to check the computer lock.

## Documentation

* [User guide](docs/USER-GUIDE.md) — daily use, barcodes, weights,
  variations, returns, **backups & restore**, **printer setup**, shortcuts.
* [Architecture & migration](docs/ARCHITECTURE.md) — what changed from the
  online version, database design, data-integrity rules, design system,
  testing, known limitations.

## Project layout

```
src/com/smartbillingpro/
  app/       paths, local config, single-instance lock, computer lock
             (DeviceLock), wiring (AppContext)
  db/        SQLite connection, versioned migrations, JDBC helper
  model/     records: Product, Variant, Sale, LedgerEntry, ...
  repo/      SQL only (one repository per area)
  service/   business rules: Cart, SaleService, InventoryService, PartyService,
             PurchaseService, ExpenseService, ReportService, DashboardService,
             BackupService, LegacyImportService, SettingsService
  print/     receipt/report layout, Java2D + PDF (PDFBox) canvases, ESC/POS,
             printers, CSV/PDF export
  ui/        FlatLaf Swing UI: MainFrame, pages/ (9 tabs), dialogs/, components
  util/      Money (integer paisa), Qty (thousandths), Dates, Fonts, ...
test/        self-contained test suite (no external test library)
resources/   bundled Inter font (SIL OFL), KeyKraft logo (SVG), terms of use
packaging/   app icon, installer script (Inno Setup) and installer artwork
lib/         sqlite-jdbc, FlatLaf, PDFBox (Apache 2.0)
```
