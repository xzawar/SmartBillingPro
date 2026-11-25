# Smart Billing Pro — User guide

Everything works without the internet. There is one window with nine tabs on
the left; `Ctrl+1` … `Ctrl+9` jump between them.

## 0. Installing

Run **`SmartBillingPro-Setup-4.0.0.exe`** on the shop's laptop. Accept the
terms of use, then click through the wizard; everything the app needs is
inside the installer. The installer registers the laptop: the installed app
only runs on that laptop, and if its files are copied to another computer it
refuses to start there. After a repair or a Windows reinstall on the same
laptop, simply run the installer again — your data is kept. The terms can be
read again any time under *Tools & Settings › Security & data › Terms of use*.

## 1. First start

The first time the app opens it asks a few short questions, one at a time:
your shop's name, phone number and address (all printed on receipts) and
whether you add sales tax. Anything you skip can be changed later in
**Tools & Settings**. Currency is **Rs / PKR** by default.

The **Dashboard** then shows a short **Get your shop ready** list (add a
product, make a first sale, choose the receipt printer, make a first backup)
with one button for each step. Hide it whenever you like.

If you used the previous online version on this computer, the app offers to
**import** products, categories, suppliers, customers and shop details on
first start (also available under *Tools & Settings › Security & data*).

## 2. Adding products

**Inventory › Add product** (or `Ctrl+N`), or simply scan a barcode on the
Sales screen that the app doesn't know yet. Adding a product asks **one
question per screen**, and `Enter` always moves on:

1. What is it called?
2. How do you sell it? — *by the piece*, *by weight* (price per kg),
   *by volume* (per litre) or *by length* (per metre). Other units such as
   dozen or gram are under *Use a different unit*.
3. Does it come in different sizes or types? If yes, tap suggestions such as
   Small / Medium / Large (or type your own); each size gets its own price,
   barcode and stock.
4. Scan the barcode — the app tells you straight away if it is already used.
   No barcode? Press *It has no barcode*.
5. What do you sell it for?
6. What did you pay for it? (optional — it is only used to show your profit)
7. How many do you have right now?
8. Which category is it in? (optional)

Finally a summary shows every answer with a **Change** link. *Show more
details* there holds the rarely needed extras: low-stock warning, item code
(SKU), description, supplier, picture and pack size. **Save product**, or
**Save and add another**.

Click a product in **Inventory** to see its stock, price, profit, a chart of
what sold in the last 30 days and its latest stock changes.

* **Change stock** — for anything that isn't a sale or a purchase: new stock
  that arrived without a bill, damaged or expired items, lost items, items
  used in the shop, items sent back, or *I counted it* to set the exact
  number. Every change is kept in **Stock history** with its reason.
* **Edit** opens the same summary; change only what you need.
* Products with history cannot be deleted; **More › Hide from the Sales
  screen** hides them but keeps reports correct. Show them again any time
  from the *Hidden* filter.

## 3. Selling (Sales tab)

* **Scan** a barcode at any time — you do not need to click the search box
  first. Scanning the same item again adds one more.
* Or type part of a name, barcode or category, then tap a product in the list
  (or `↓` then `Enter`). The category buttons above the list narrow it down.
* Products with sizes ask which one; products sold by weight ask for the
  weight. Type it in **g** (or kg) — the price is calculated automatically
  (250 g at Rs 500/kg = Rs 125) — or type the money amount and the weight is
  worked out for you.
* Unknown barcode? The app offers to **add it as a new product** (the
  barcode is already filled in) and then puts it on the bill.
* Each line on the bill has its own **−** / **+** and **×** buttons;
  double-click a line (or `F2`) to change its quantity, price or discount.
* **Add discount** gives money off the whole bill, as an amount or a %.
* **Customer** (`F4`): leave it as *Walk-in customer* for normal cash sales,
  or choose / add a customer. If a customer pays less than the total, the
  rest is added to their account (walk-in customers must pay in full).
* **Payment**: tap how the customer pays (Cash, Card, …), then type the cash
  received (`F10`) or tap one of the round amounts — or leave it empty for
  the exact amount. *Give back Rs …* shows the change.
* **Complete sale** — `F12` (or `Enter` in the amount box). Stock, payment,
  customer balance and receipt are saved together in one step; a sale can
  never be saved twice.
* **Hold bill** (`F8`) parks the bill to serve someone else; **Held bills**
  (`F9`) brings it back. **Shortcuts** at the top lists every key.

## 4. Receipts, reprints and returns

**Receipts** lists every sale; search by receipt number, customer or phone
and choose the period. Select one to see it exactly as it prints, then
**Print**, **Save as PDF** or **Take items back**.

Returns put the stock back and record the refund: cash back, or for account
customers optionally taken off what they owe. The original sale is never
changed — it is marked *Partly returned* / *Returned* and the return has its
own number (`RET-…`).

## 5. Customers, suppliers and purchases

**Customers** and **Suppliers** list everyone with what they owe (or what you
owe them). Click a name to see the balance, totals, a chart of what was
bought each month and the latest receipts or purchases. **Receive payment**
/ **Pay** records money; *More* has the full account history, *Correct the
balance* and *Hide*. A new customer or supplier only needs a name — phone,
address and an old balance are under *Add more details*.

**Suppliers › New purchase**: choose who you bought from (or *Somewhere
else*), scan or search the products and enter quantity and cost, then tap
**Paid in full**, **Paid part of it** or **Not paid yet**. Stock goes up, the
product's cost becomes the moving average, and any unpaid part is added to
the supplier's balance. Bill number, discount and a note are under *Add bill
number, discount or note*. A purchase entered by mistake can be
**cancelled** from its details (stock and balance are corrected, the purchase
stays in history).

## 6. Ledgers and expenses

* **Ledgers › Customer / Supplier accounts**: every sale, purchase, payment,
  return and correction with a running balance and a chart of the balance
  over time, for any period. Export as PDF/Excel or print.
* Payments recorded by mistake are **reversed**, never deleted.
* **Ledgers › Shop expenses**: tap what the money was for (rent,
  electricity, salaries…) and type the amount — today's date and your usual
  way of paying are filled in. A chart shows where the money went. Expenses
  reduce net profit in reports. A wrong expense is **cancelled** (it stays
  visible, marked as cancelled).

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
   printer*) and choose **80 mm** or **58 mm** paper. Everything below is
   under *Show advanced printer settings* and rarely needs changing:
   *Custom* paper widths (such as 48 mm paper) and *Printable width*
   (80 mm paper → 72 mm, 58 mm → 48 mm).
3. *How to print* (advanced):
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
| `F1` | Go to the search box |
| `↓` then `Enter` | Add the highlighted product |
| `F2` | Change quantity, price or discount of the item |
| `F4` | Choose the customer |
| `F8` / `F9` | Hold this bill / bring back a held bill |
| `F10` | Type the cash received |
| `F12` | Complete sale |
| `+` / `−` (number pad) | One more / one less of the last item |
| `Ctrl+Delete` | Clear the bill |
| `Esc` | Clear the search |
| `Ctrl+1 … Ctrl+9` | Switch tabs |
| `Ctrl+N` (Inventory) | Add a product |
