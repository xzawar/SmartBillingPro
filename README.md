# Smart Billing Pro — Offline POS

A fast, fully **offline** point-of-sale and shop management app for Windows.
One shop, one owner, one window. All data stays on the computer in a local
SQLite database, with automatic daily backups. No internet connection, no
online account and no server are needed.

| | |
|---|---|
| **Sales (POS)** | Barcode scanning (USB scanners work anywhere in the window), search, category filters, product grid, variations (sizes…), sale by weight/volume (e.g. 250 g of a per-kg product), line & bill discounts, tax, hold/recall, cash/bank/other payment methods, change and part-payment on a customer's account, keyboard shortcuts |
| **Inventory** | Categories, products with variations (own barcode, SKU, prices, stock each), units (pcs, kg, g, mg, L, ml, m, cm + custom), duplicate-barcode protection, pictures, low/out-of-stock alerts, stock adjustments with reasons, full stock movement history, archive instead of delete |
| **Suppliers** | Supplier accounts, purchases (full / partial / credit), moving-average cost, purchase reversal, payments, statements |
| **Ledgers** | Customer and supplier ledgers with running balances, payments, adjustments, reversals, PDF/Excel/print statements, expenses |
| **Customers** | Accounts, purchase history, receipts, total paid, outstanding balance, receive payments; walk-in sales need no customer |
| **Receipts** | Every sale stored; search by number/customer/date/method/status; preview, reprint, **PDF** (A4 or roll), **58 / 80 mm thermal** (driver or ESC/POS), returns & refunds |
| **Reports** | 25 reports: daily/weekly/monthly/product/category/payment-method sales, returns, P&L, daily/monthly/product profit, stock, low/out of stock, valuation, movements, purchase history, customer/supplier balances, cash received, credit sales, payments made, cash book, expenses — all with date filters, search, PDF, CSV (Excel) and print |
| **Dashboard** | Today's sales & profit, purchases, inventory value, products, low stock, customers, receivables, payables, sales trend, profit vs expenses, top products, category sales, stock overview — all from real data |
| **Tools & Settings** | Store details, currency (PKR by default), date format, tax, receipt texts & numbering, printer setup + test print, payment methods, units, backup & restore, optional startup PIN, import from the previous version |

## Install on Windows (no Java needed)

Download **`SmartBillingPro-4.0.0-windows.zip`** from the latest
*Build Windows app* run in the repository's **Actions** tab (or build it
yourself, below), unzip it anywhere (e.g. `C:\SmartBillingPro`) and run
`SmartBillingPro\SmartBillingPro.exe`. Pin it to the taskbar. The folder
contains its own Java runtime.

Your data is stored in `%LOCALAPPDATA%\SmartBillingPro` and backups in
`Documents\SmartBillingPro Backups` (changeable in *Tools & Settings*).

## Build it yourself

Requires a JDK 17 or newer (e.g. [Temurin 21](https://adoptium.net)). All
libraries are already in `lib/` and `resources/`.

| Task | Windows | macOS / Linux |
|---|---|---|
| Run from source | `run.bat` | `./run.sh` |
| Run the tests | `run-tests.bat` | `./run-tests.sh` |
| Build the Windows app (`dist\SmartBillingPro\SmartBillingPro.exe` + zip; installer too if WiX 3 is installed) | `build-exe.bat` | — |
| Build a portable Java bundle (+ native image for the current OS) | — | `./build.sh` |

The GitHub Actions workflow `.github/workflows/build-windows.yml` runs the
tests and builds the Windows app on every push.

## Documentation

* [User guide](docs/USER-GUIDE.md) — daily use, barcodes, weights,
  variations, returns, **backups & restore**, **printer setup**, shortcuts.
* [Architecture & migration](docs/ARCHITECTURE.md) — what changed from the
  online version, database design, data-integrity rules, design system,
  testing, known limitations.

## Project layout

```
src/com/smartbillingpro/
  app/       paths, local config, single-instance lock, wiring (AppContext)
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
resources/   bundled Inter font (SIL OFL)
packaging/   app icon
lib/         sqlite-jdbc, FlatLaf, PDFBox (Apache 2.0)
```
