# Smart Billing Pro — User guide

Everything works without the internet. There is one window with nine tabs on
the left; `Ctrl+1` … `Ctrl+9` jump between them.

## 1. First start

1. Open **Tools & Settings › Store** and enter your shop name, address and
   phone (they appear on receipts). Currency is **Rs / PKR** by default.
2. **Tools & Settings › Sales & receipts**: turn tax on if you charge it,
   set the receipt prefix (e.g. `INV` → `INV-000001`), footer text and what
   happens after a sale (print automatically / show the receipt / nothing).
3. **Tools & Settings › Printer**: choose your receipt printer and paper
   width, then **Print test receipt** (see §8).
4. **Tools & Settings › Backup & restore**: choose a backup folder, ideally
   on a USB drive or a second disk (see §7).
5. If you used the previous online version on this computer, the app offers
   to **import** products, categories, suppliers, customers and shop details
   on first start (also available under *Security & data*).

## 2. Adding products

**Inventory › Add product** (or `Ctrl+N` on the Inventory tab).

* **Barcode** — click the barcode box and scan; the app checks at once that
  the barcode is not used by another product. A scanner's Enter just moves to
  the next box, so it never saves the form by accident.
* **Sold by (unit)** — `Pieces` for packed goods; `kg`, `g`, `L`, `ml`, `m`
  … for goods sold by weight or measure. The selling price is per that unit
  (e.g. Rs 500 per kg).
* **Opening stock** is recorded as a stock movement. After that, stock only
  changes through sales, returns, purchases and **Adjust stock** — never by
  editing the product — so every change has a date and a reason.
* **Variations** — tick *This product comes in variations* to give one product
  several sizes/colours/packs, each with its own barcode, SKU, cost, price and
  stock. *Add S / M / L / XL* fills the usual sizes.
* Products with history cannot be deleted; **Archive** hides them from the
  till but keeps reports correct. Archived products can be restored.

## 3. Selling (Sales tab)

* **Scan** a barcode at any time — you do not need to click the search box
  first. Scanning the same item again adds one more.
* Or type part of a name/barcode, then click a product (or `↓` then `Enter`).
* Products with variations ask which one; weighed products ask for the
  weight. Type it in **g** (or kg) — the price is calculated automatically
  (250 g at Rs 500/kg = Rs 125) — or type the money amount and the weight is
  worked out for you.
* Unknown barcode? The app offers to **register a new product** with it and
  then adds it to the bill.
* Change a line: select it and use `+` / `−`, or `F2` to edit quantity,
  price or a line discount; `Delete` removes it.
* **Bill discount**: amount, or press `%` for a percentage.
* **Customer** (`F4`): leave as *Walk-in Customer* for normal cash sales, or
  pick/create a customer. If the customer pays less than the total, the rest
  is added to their account (walk-in customers must pay in full).
* **Payment**: choose the method, type the amount received (`F10`) — or leave
  it empty for the exact amount — and the change is shown.
* **Complete Sale** — `F12` (or `Enter` in the amount box). Stock, payment,
  customer balance and receipt are saved together in one step; a sale can
  never be saved twice.
* **Hold** (`F8`) parks the bill to serve another customer; **Held** (`F9`)
  brings it back. `Ctrl+Delete` clears the bill, `Esc` clears the search.

## 4. Receipts, reprints and returns

**Receipts** lists every sale with filters for number, customer, date,
payment method and status. Select one to preview it, then **Print**,
**Save PDF** or **Return items**.

Returns put the stock back and record the refund: cash back, or for account
customers optionally taken off what they owe. The original sale is never
changed — it is marked *Partly returned* / *Returned* and the return has its
own number (`RET-…`).

## 5. Suppliers and purchases

**Suppliers › New purchase**: choose the supplier (or *No supplier* for a
cash purchase), scan/search products, enter quantity and cost, an optional
bill discount and how much you paid now. Stock goes up, the product's cost
price becomes the moving average, and any unpaid part is added to the
supplier's balance. A purchase entered by mistake can be **reversed** from
its details (stock and balance are corrected, the purchase stays in history).

## 6. Ledgers, payments and expenses

* **Ledgers › Customer / Supplier ledger**: every charge, payment, return and
  adjustment with a running balance, for any period, with opening and closing
  balance. **Receive payment / Pay supplier** records money; **Adjust
  balance** corrects a balance with a reason. Export as PDF/Excel or print.
* Payments recorded by mistake are **reversed**, never deleted.
* **Ledgers › Expenses**: rent, electricity, salaries… These feed net profit.
  A wrong expense is **voided** (it stays visible).

## 7. Backups and restore

* A complete, verified backup is made **automatically once a day** (shortly
  after the app opens and again when it closes, if none was made that day).
  The newest 30 daily backups are kept (changeable).
* **Back up now** makes an extra backup at any time; **Back up to USB / other
  folder…** saves one somewhere else once; **Change backup folder…** moves
  all future backups (e.g. to a USB drive or second disk).
* File names show the date and time, e.g.
  `SmartBillingPro_2026-11-24_21-30-15_auto.db`, each with a `.sha256`
  checksum file. Every backup is integrity-checked right after it is written.
* The top bar shows the backup status: green = backed up today, amber = not
  today / never, red = the last backup failed (click it for details).

**Restore:** *Tools & Settings › Backup & restore* → select a backup →
**Restore selected…** (or **Restore from file…** for a backup on a USB
drive). The app checks the file (integrity, that it is a Smart Billing Pro
backup, checksum, version), shows what it contains, **saves a safety copy of
your current data first**, then swaps the data in. If anything goes wrong the
previous data is put back automatically. Safety copies appear in the list as
*Safety copy* and can be restored the same way.

Moving to a new computer: install the app, open it once, then restore your
latest backup file.

## 8. Printers

**Thermal receipt printers (58 mm / 80 mm):**

1. Install the printer's Windows driver and make sure Windows can print a
   test page.
2. *Tools & Settings › Printer*: pick the printer (or keep *Windows default
   printer*), choose **80 mm** or **58 mm** (or *Custom* for other widths such
   as 48 mm paper), and check *Printable width* (80 mm paper → 72 mm,
   58 mm → 48 mm).
3. *How to print*:
   * **Windows printer driver (recommended)** — works with almost every
     printer; adjust *Text size* if text is too small/large.
   * **Raw ESC/POS text** — for printers that accept raw ESC/POS. Set
     *Characters per line* (32 for 58 mm, 48 for 80 mm). Supports automatic
     paper cut and opening the cash drawer.
4. **Print test receipt.**

**PDF:** receipts can be saved as an A4 invoice-style PDF or as a narrow
PDF identical to the thermal receipt (*Sales & receipts › PDF size*). Turn on
*Also save a PDF copy of every receipt* to keep a PDF of each sale
automatically. Reports and ledger statements print on any A4 printer through
the normal Windows print dialog.

## 9. Security

*Tools & Settings › Security & data* can set an optional 4–8 digit PIN
that is asked when the app opens and when you click the lock icon. There are
no user accounts.

## Keyboard shortcuts (Sales)

| Key | Action |
|---|---|
| `F1` | Focus the scan/search box |
| `F2` | Edit the selected line |
| `F4` | Choose customer |
| `F8` / `F9` | Hold sale / Held sales |
| `F10` | Amount received |
| `F12` | Complete sale |
| `+` / `−` (numpad) | Change quantity of the selected line |
| `Delete` | Remove selected line |
| `Ctrl+Delete` | Clear the bill |
| `Ctrl+1 … Ctrl+9` | Switch tabs |
