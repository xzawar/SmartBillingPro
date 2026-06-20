# Smart Billing Pro

A point-of-sale and inventory system for a real shop, built with Java Swing and
an object-oriented design. It supports **multiple checkout counters sharing one
database**, a barcode scanner + thermal receipt workflow, suppliers and purchase
orders, cost/profit reporting, customer loyalty, returns/refunds, and staff
logins with roles.

---

## 1. Requirements

- **Java JDK 17 or newer** - https://adoptium.net (the `java -version` command should work)
- **SQLite JDBC driver** - one small `.jar` placed in the `lib/` folder (see below)

## 2. One-time setup: add the database driver

1. Open https://github.com/xerial/sqlite-jdbc/releases
2. Download the latest `sqlite-jdbc-x.x.x.x.jar`
3. Copy it into the **`lib/`** folder of this project

That's it - the run scripts pick up everything in `lib/` automatically.

## 3. Run

- **Windows:** double-click `run.bat`
- **macOS / Linux:** run `./run.sh` in a terminal (you may need `chmod +x run.sh` first)

The script compiles the source into `out/` and launches the app.

## 4. First login

| Username | Password   | Role          |
|----------|------------|---------------|
| `admin`  | `admin123` | Administrator |

Change this password (and add staff) from **Staff** once you're in.

---

## Multiple counters sharing data

All data lives in a single SQLite database file. By default it is created at:

```
<your home folder>/.smartbillingpro/shop.db
```

To let several counters share the same data, put that `shop.db` file on a shared
network drive that every till can reach, and point each computer at it by
setting the `SMARTBILLING_DB` environment variable to the shared path before
launching. SQLite is configured with WAL mode and a busy-timeout so concurrent
tills can read and write safely.

> Tip: for very high concurrency (many tills hammering the same file at once),
> a client/server database is the long-term upgrade, but a shared SQLite file is
> perfect for a single shop or a small mall unit.

## Roles and permissions

| Capability                | Admin | Manager | Cashier |
|---------------------------|:-----:|:-------:|:-------:|
| Make sales (POS)          |  Yes  |   Yes   |   Yes   |
| Manage inventory          |  Yes  |   Yes   |    -    |
| Suppliers & purchases     |  Yes  |   Yes   |    -    |
| Reports (sales/profit)    |  Yes  |   Yes   |    -    |
| Void / refund sales       |  Yes  |   Yes   |    -    |
| Manage staff              |  Yes  |    -    |    -    |
| Settings                  |  Yes  |    -    |    -    |

## Hardware: scanner & receipt printer

- **Barcode scanner:** USB scanners act as a keyboard. On the **New Sale**
  screen, scanning types the code into the product search box and filters the
  catalog instantly - just press Add to Cart.
- **Thermal receipt printer:** receipts are formatted to a narrow 32-column
  width. From the receipt preview window use **Print** (your OS print dialog),
  and pick the thermal printer. Sales History can reprint any past receipt.

## Returns & refunds

Open **Sales History**, select a completed sale, then choose **Void**
(cancel a mistake, full reversal) or **Refund** (customer return). Both restore
stock automatically and ask for a reason that is saved in the audit log.

> Partial returns (refunding only some lines) are not automated yet - process
> them as a full refund followed by a fresh sale of the kept items.

## What's inside (architecture)

```
src/com/smartbillingpro/
  model/        plain data objects (Product, Invoice, User, ...)
  repository/   database access (DAOs) over a single SQLite connection
  service/      business logic (billing math, sales, reports, auth)
  ui/           Swing screens, dialogs, reusable components, theme
  SmartBillingApp.java   entry point
lib/            put the SQLite JDBC driver jar here
run.sh / run.bat        compile + launch helpers
```

The layers are kept separate on purpose: UI talks to services, services talk to
repositories, repositories talk to the database. Money is handled with
`BigDecimal` throughout to avoid rounding errors.
