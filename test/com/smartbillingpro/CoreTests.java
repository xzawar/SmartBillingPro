package com.smartbillingpro;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleItem;
import com.smartbillingpro.model.SaleStatus;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.service.Cart;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.service.PurchaseService;
import com.smartbillingpro.service.ReportService;
import com.smartbillingpro.service.SaleService;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.smartbillingpro.TestSupport.check;
import static com.smartbillingpro.TestSupport.eq;
import static com.smartbillingpro.TestSupport.fails;
import static com.smartbillingpro.TestSupport.money;
import static com.smartbillingpro.TestSupport.qty;

/** Business-logic tests against a real (temporary) SQLite database. */
final class CoreTests {

    private CoreTests() {
    }

    // ------------------------------------------------------------ pure maths

    static void testMoneyAndQty() {
        Money.setSymbol("Rs");
        eq(125050L, Money.parse("1,250.50"), "parse grouped");
        eq(30000L, Money.parse("Rs 300"), "parse with symbol");
        eq("Rs 1,250.50", Money.format(125050), "format");
        eq("-Rs 5.00", Money.format(-500), "format negative");
        fails(() -> Money.parse("12#4"), "not a valid amount");
        fails(() -> Money.parse("1.234"), "decimal places");
        eq(250L, Qty.parse("0.25"), "qty parse");
        eq("0.25", Qty.format(250), "qty format");
        eq("2", Qty.format(2000), "qty format whole");
        // weight-based pricing: 500 PKR/kg x 250 g = 125 PKR
        eq(12500L, Money.mulQty(50000, 250), "500/kg x 0.25kg");
        eq(33L, Money.mulQty(100, 333), "rounding half-up 0.333 x 1.00");
        long[] parts = Money.allocate(1000, new long[] {1, 1, 1});
        eq(1000L, parts[0] + parts[1] + parts[2], "allocation sums exactly");
    }

    static void testWeightConversion() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            Unit g = TestSupport.unit(ctx, "g");
            Unit kg = TestSupport.unit(ctx, "kg");
            eq(250L, InventoryService.convert(qty("250"), g, kg.factor()), "250 g = 0.25 kg");
            eq(qty("1500"), InventoryService.convert(qty("1.5"), kg, g.factor()), "1.5 kg = 1500 g");
            Unit mg = TestSupport.unit(ctx, "mg");
            eq(qty("0.5"), InventoryService.convert(qty("500"), mg, g.factor()), "500 mg = 0.5 g");
        }
    }

    static void testCartTotals() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long a = TestSupport.simpleProduct(ctx, "Soap", "111", "60", "100", "50", "pcs");
            long b = TestSupport.simpleProduct(ctx, "Rice", "222", "300", "500", "20", "kg");
            Cart cart = new Cart();
            cart.add(ctx.inventory().sellable(a), Qty.of(3));
            cart.add(ctx.inventory().sellable(a), Qty.of(1)); // merges
            eq(1, cart.lines().size(), "same product merges into one line");
            cart.add(ctx.inventory().sellable(b), qty("0.25"));
            Cart.Totals t = cart.totals();
            eq(money("525"), t.subtotal(), "400 + 125");
            cart.setLineDiscount(cart.lines().get(0), money("25"));
            cart.setDiscountPercent(new BigDecimal("10"));
            cart.setTaxPercent(new BigDecimal("17"));
            t = cart.totals();
            eq(money("25"), t.itemDiscount(), "line discount");
            eq(money("50"), t.cartDiscount(), "10% of 500");
            eq(money("450"), t.taxable(), "taxable");
            eq(money("76.50"), t.tax(), "17% tax");
            eq(money("526.50"), t.grandTotal(), "grand total");
            long sumLines = t.lines().stream().mapToLong(Cart.Allocation::total).sum();
            eq(t.grandTotal(), sumLines, "line totals add up to grand total");
            fails(() -> cart.add(ctx.inventory().sellable(a), qty("0.5")), "whole");
            fails(() -> cart.add(ctx.inventory().sellable(a), Qty.of(47)), "only 50");
            fails(() -> cart.setLineDiscount(cart.lines().get(0), money("9999")), "cannot be more");
        }
    }

    // ------------------------------------------------------------ products

    static void testProductsAndBarcodes() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long v = TestSupport.simpleProduct(ctx, "Cola 1.5L", "8901234", "150", "200", "24", "pcs");
            eq("Cola 1.5L", ctx.inventory().findByCode("8901234").productName(), "barcode lookup");
            eq(qty("24"), ctx.inventory().sellable(v).stockQty(), "opening stock");
            fails(() -> TestSupport.simpleProduct(ctx, "Other", "8901234", "1", "2", "0", "pcs"), "already used by Cola");
            eq(null, ctx.inventory().findByCode("0000"), "unknown barcode");

            // product with variations
            long pcs = TestSupport.unit(ctx, "pcs").id();
            long cat = ctx.inventory().addCategory("Clothing");
            fails(() -> ctx.inventory().addCategory("clothing"), "already exists");
            List<InventoryService.VariantForm> vars = List.of(
                    new InventoryService.VariantForm(null, "Small", "S-1", "TS-S", money("500"), money("900"), Qty.of(5), 0, true),
                    new InventoryService.VariantForm(null, "Medium", "M-1", "TS-M", money("520"), money("950"), Qty.of(6), 0, true),
                    new InventoryService.VariantForm(null, "Large", "L-1", "TS-L", money("540"), money("1000"), Qty.of(7), 0, true));
            long pid = ctx.inventory().saveProduct(new InventoryService.ProductForm(null, "T-Shirt", cat, "Cotton", pcs,
                    null, null, null, true, vars));
            Product p = ctx.inventory().product(pid);
            eq(3, p.variants().size(), "3 variations");
            eq(Qty.of(18), p.totalStock(), "total stock across variations");
            SellableItem m = ctx.inventory().findByCode("M-1");
            eq("Medium", m.variantName(), "scan variant barcode");
            eq(money("950"), m.salePrice(), "variant price");
            fails(() -> ctx.inventory().saveProduct(new InventoryService.ProductForm(null, "Dup", cat, null, pcs, null, null,
                    null, true, List.of(
                        new InventoryService.VariantForm(null, "A", "X1", null, 0, 0, 0, 0, true),
                        new InventoryService.VariantForm(null, "a", "X2", null, 0, 0, 0, 0, true)))), "must be different");

            // edit: rename, remove one variation that has history (archived, not deleted)
            Product cur = ctx.inventory().product(pid);
            List<InventoryService.VariantForm> edit = List.of(
                    new InventoryService.VariantForm(cur.variants().get(0).id(), "S", "S-1", "TS-S", money("500"), money("950"), 0, 0, true),
                    new InventoryService.VariantForm(cur.variants().get(1).id(), "M", "M-1", "TS-M", money("520"), money("950"), 0, 0, true));
            ctx.inventory().saveProduct(new InventoryService.ProductForm(pid, "T-Shirt", cat, null, pcs, null, null, null, true, edit));
            cur = ctx.inventory().product(pid);
            eq(3, cur.variants().size(), "removed variation with stock is archived, not deleted");
            eq(2L, cur.variants().stream().filter(x -> x.active()).count(), "two active");
            eq(Qty.of(5), cur.variants().get(0).stockQty(), "editing never changes stock");
            fails(() -> ctx.inventory().deleteCategory(cat), "still has");
            check(!ctx.inventory().canDelete(pid), "product with stock cannot be deleted");
            fails(() -> ctx.inventory().saveProduct(new InventoryService.ProductForm(pid, "T-Shirt", cat, null,
                    TestSupport.unit(ctx, "kg").id(), null, null, null, true, edit)), "unit cannot be changed");
        }
    }

    static void testStockAdjustment() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long v = TestSupport.simpleProduct(ctx, "Sugar", "S1", "120", "150", "10", "kg");
            ctx.inventory().adjustStock(v, InventoryService.AdjustMode.REMOVE, qty("0.5"), "Damaged", "wet bag");
            eq(qty("9.5"), ctx.inventory().sellable(v).stockQty(), "removed 0.5kg");
            ctx.inventory().adjustStock(v, InventoryService.AdjustMode.SET, qty("12"), "Stock count correction", null);
            eq(qty("12"), ctx.inventory().sellable(v).stockQty(), "set to counted");
            fails(() -> ctx.inventory().adjustStock(v, InventoryService.AdjustMode.REMOVE, qty("100"), "Damaged", null),
                    "not enough stock");
            fails(() -> ctx.inventory().adjustStock(v, InventoryService.AdjustMode.ADD, qty("1"), "", null), "reason");
            eq(qty("12"), ctx.inventory().sellable(v).stockQty(), "failed adjustment changed nothing");
            eq(3, ctx.inventory().movements(null, null, null, "Sugar").size(), "opening + 2 adjustments recorded");
            fails(() -> ctx.database().db().update("UPDATE stock_movements SET qty_change = 1"), "cannot be changed");
            fails(() -> ctx.database().db().update("DELETE FROM stock_movements"), "cannot be deleted");
        }
    }

    // ------------------------------------------------------------ sales

    static void testCompleteCashSale() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long soap = TestSupport.simpleProduct(ctx, "Soap", "111", "60", "100", "10", "pcs");
            long rice = TestSupport.simpleProduct(ctx, "Rice", "222", "300", "500", "20", "kg");
            Cart cart = new Cart();
            cart.add(ctx.inventory().sellable(soap), Qty.of(2));
            cart.add(ctx.inventory().sellable(rice), qty("0.25"));
            long cash = TestSupport.cashMethod(ctx);
            Sale s = ctx.sales().complete(cart, new SaleService.PaymentInput(cash, money("500"), null));
            eq("INV-000001", s.receiptNo(), "first receipt number");
            eq(money("325"), s.grandTotal(), "200 + 125");
            eq(money("175"), s.changeGiven(), "change");
            eq(0L, s.balanceDue(), "fully paid");
            eq(Qty.of(8), ctx.inventory().sellable(soap).stockQty(), "soap stock deducted");
            eq(qty("19.75"), ctx.inventory().sellable(rice).stockQty(), "rice stock deducted");
            eq(money("75"), s.items().get(1).costAmount(), "cost of 0.25kg at 300/kg");
            fails(() -> ctx.sales().complete(cart, new SaleService.PaymentInput(cash, money("500"), null)), "already been saved");
            cart.clear();
            cart.add(ctx.inventory().sellable(soap), Qty.of(1));
            Sale s2 = ctx.sales().complete(cart, new SaleService.PaymentInput(cash, money("100"), null));
            eq("INV-000002", s2.receiptNo(), "sequential receipt numbers");
            // walk-in cannot take credit
            Cart c3 = new Cart();
            c3.add(ctx.inventory().sellable(soap), Qty.of(1));
            fails(() -> ctx.sales().complete(c3, new SaleService.PaymentInput(cash, money("50"), null)), "select a customer");
            eq(Qty.of(7), ctx.inventory().sellable(soap).stockQty(), "rejected sale changed nothing");
            fails(() -> ctx.database().db().update("UPDATE sales SET grand_total = 1"), "cannot be edited");
            fails(() -> ctx.database().db().update("DELETE FROM sales"), "cannot be deleted");
        }
    }

    static void testSaleRollsBackWhenStockRunsOut() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long a = TestSupport.simpleProduct(ctx, "A", "A", "1", "10", "5", "pcs");
            long b = TestSupport.simpleProduct(ctx, "B", "B", "1", "10", "5", "pcs");
            Cart cart = new Cart();
            cart.add(ctx.inventory().sellable(a), Qty.of(2));
            cart.add(ctx.inventory().sellable(b), Qty.of(5));
            // stock of B changes behind the till's back (another adjustment)
            ctx.inventory().adjustStock(b, InventoryService.AdjustMode.REMOVE, Qty.of(3), "Damaged", null);
            fails(() -> ctx.sales().complete(cart, new SaleService.PaymentInput(TestSupport.cashMethod(ctx), money("100"), null)),
                    "not enough stock of b");
            eq(Qty.of(5), ctx.inventory().sellable(a).stockQty(), "A untouched after rollback");
            eq(0L, ctx.database().db().scalar("SELECT COUNT(*) FROM sales"), "no sale saved");
            eq(0L, ctx.database().db().scalar("SELECT COUNT(*) FROM payments"), "no payment saved");
            eq("INV-000001", ctx.sales().previewNextReceiptNo(), "receipt number not consumed");
        }
    }

    static void testCreditSaleAndCustomerLedger() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long v = TestSupport.simpleProduct(ctx, "Oil 5L", "OIL", "2000", "2500", "10", "pcs");
            long cid = ctx.parties().saveCustomer(null, "Ali Khan", "0300-1234567", "Lahore", null, money("1000"), true);
            fails(() -> ctx.parties().saveCustomer(null, "Other", "0300-1234567", null, null, 0, true), "already has the phone");
            Customer c = ctx.parties().customer(cid);
            eq(money("1000"), c.balance(), "opening balance");
            Cart cart = new Cart();
            cart.setCustomer(c);
            cart.add(ctx.inventory().sellable(v), Qty.of(2));
            Sale s = ctx.sales().complete(cart, new SaleService.PaymentInput(TestSupport.cashMethod(ctx), money("3000"), null));
            eq(money("2000"), s.balanceDue(), "partial payment leaves 2000 due");
            eq("Partial", s.paymentStatus(), "status");
            eq(money("3000"), ctx.parties().balance(PartyType.CUSTOMER, cid), "1000 opening + 2000 due");
            ctx.parties().receiveFromCustomer(cid, money("2500"), TestSupport.cashMethod(ctx), "cash");
            eq(money("500"), ctx.parties().balance(PartyType.CUSTOMER, cid), "after payment");
            var stmt = ctx.parties().statement(PartyType.CUSTOMER, cid, null, null);
            eq(4, stmt.size(), "opening, sale, paid at sale, payment");
            eq(money("500"), stmt.get(stmt.size() - 1).balance(), "running balance");
            var pay = ctx.parties().payments(null, null, "CUSTOMER", cid, "IN").get(0);
            ctx.parties().reversePayment(pay.id(), "entered twice");
            eq(money("3000"), ctx.parties().balance(PartyType.CUSTOMER, cid), "reversal restores balance");
            fails(() -> ctx.parties().reversePayment(pay.id(), "again"), "already been reversed");
            ctx.parties().adjustBalance(PartyType.CUSTOMER, cid, -money("100"), "Goodwill discount");
            eq(money("2900"), ctx.parties().balance(PartyType.CUSTOMER, cid), "adjustment");
            eq(money("2900"), ctx.parties().totalReceivable(), "total receivable");
        }
    }

    static void testReturns() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long v = TestSupport.simpleProduct(ctx, "Shoes", "SH", "1000", "1500", "10", "pcs");
            long cash = TestSupport.cashMethod(ctx);
            Cart cart = new Cart();
            cart.add(ctx.inventory().sellable(v), Qty.of(3));
            cart.setDiscountAmount(money("300"));
            Sale s = ctx.sales().complete(cart, new SaleService.PaymentInput(cash, money("4200"), null));
            eq(money("4200"), s.grandTotal(), "4500 - 300");
            SaleItem line = s.items().get(0);
            long[] val = SaleService.returnValue(line, Qty.of(1));
            eq(money("1400"), val[2], "one third of the discounted line");
            fails(() -> ctx.sales().processReturn(s.id(), List.of(new SaleService.ReturnLine(line.id(), Qty.of(1))),
                    money("100"), cash, "Size"), "full value");
            ctx.sales().processReturn(s.id(), List.of(new SaleService.ReturnLine(line.id(), Qty.of(1))), money("1400"), cash,
                    "Wrong size");
            eq(Qty.of(8), ctx.inventory().sellable(v).stockQty(), "stock back");
            Sale after = ctx.sales().find(s.id());
            eq(SaleStatus.PARTIALLY_RETURNED, after.status(), "partly returned");
            fails(() -> ctx.sales().processReturn(s.id(), List.of(new SaleService.ReturnLine(line.id(), Qty.of(3))),
                    0, cash, "x"), "at most 2");
            ctx.sales().processReturn(s.id(), List.of(new SaleService.ReturnLine(line.id(), Qty.of(2))), money("2800"), cash, "Faulty");
            after = ctx.sales().find(s.id());
            eq(SaleStatus.RETURNED, after.status(), "fully returned");
            eq(after.grandTotal(), after.returnedTotal(), "returned total equals sale");
            ReportService.ProfitFigures p = ctx.reports().profit(LocalDate.now(), LocalDate.now());
            eq(0L, p.revenue(), "revenue nets to zero after full return");
            eq(0L, p.cost(), "cost nets to zero");
        }
    }

    static void testCustomerReturnCreditsAccount() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long v = TestSupport.simpleProduct(ctx, "Bag", "BG", "500", "1000", "5", "pcs");
            long cash = TestSupport.cashMethod(ctx);
            long cid = ctx.parties().saveCustomer(null, "Sara", null, null, null, 0, true);
            Cart cart = new Cart();
            cart.setCustomer(ctx.parties().customer(cid));
            cart.add(ctx.inventory().sellable(v), Qty.of(2));
            Sale s = ctx.sales().complete(cart, new SaleService.PaymentInput(cash, money("500"), null));
            eq(money("1500"), ctx.parties().balance(PartyType.CUSTOMER, cid), "owes 1500");
            eq(money("500"), ctx.sales().maxCashRefund(s, money("1000")), "can only refund what was paid");
            ctx.sales().processReturn(s.id(), List.of(new SaleService.ReturnLine(s.items().get(0).id(), Qty.of(1))), 0, null,
                    "Changed mind");
            eq(money("500"), ctx.parties().balance(PartyType.CUSTOMER, cid), "return reduces what is owed");
        }
    }

    static void testHeldSales() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long v = TestSupport.simpleProduct(ctx, "Milk", "MK", "100", "120", "10", "pcs");
            Cart cart = new Cart();
            cart.add(ctx.inventory().sellable(v), Qty.of(3));
            cart.setLineDiscount(cart.lines().get(0), money("10"));
            ctx.sales().hold(cart, "Table 4");
            check(cart.isEmpty(), "cart cleared after hold");
            eq(1L, ctx.sales().heldCount(), "one held");
            var held = ctx.sales().heldSales().get(0);
            eq("Table 4", held.label(), "label");
            eq(money("350"), held.total(), "held total");
            List<String> warnings = ctx.sales().recall(held, cart);
            check(warnings.isEmpty(), "no warnings");
            eq(Qty.of(3), cart.lines().get(0).qty(), "qty restored");
            eq(money("10"), cart.lines().get(0).lineDiscount(), "discount restored");
            eq(0L, ctx.sales().heldCount(), "removed from held list");
        }
    }

    // ------------------------------------------------------------ purchases & suppliers

    static void testPurchasesAndSupplierLedger() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long v = TestSupport.simpleProduct(ctx, "Flour", "FL", "100", "130", "10", "kg");
            long sid = ctx.parties().saveSupplier(null, "Mill Co", "042-111", null, "Wholesale", null, money("5000"), true);
            eq(money("5000"), ctx.parties().supplier(sid).balance(), "supplier opening balance");
            long bank = ctx.database().db().scalar("SELECT id FROM payment_methods WHERE name = 'Bank Transfer'");
            Purchase p = ctx.purchases().receive(new PurchaseService.PurchaseForm(sid, "B-77",
                    List.of(new PurchaseService.Line(v, qty("10"), money("120"))), money("200"), money("600"), bank, null));
            eq(money("1000"), p.total(), "1200 - 200 discount");
            eq(money("400"), p.balanceDue(), "due");
            eq(qty("20"), ctx.inventory().sellable(v).stockQty(), "stock increased");
            // moving average: (10kg x 100 + 1000) / 20kg = 100
            eq(money("100"), ctx.inventory().sellable(v).costPrice(), "average cost");
            eq(money("5400"), ctx.parties().balance(PartyType.SUPPLIER, sid), "5000 + 400");
            ctx.parties().payToSupplier(sid, money("400"), bank, null);
            eq(money("5000"), ctx.parties().balance(PartyType.SUPPLIER, sid), "after payment");
            ctx.purchases().reverse(p.id(), "Wrong supplier");
            eq(qty("10"), ctx.inventory().sellable(v).stockQty(), "stock reversed");
            eq(money("4000"), ctx.parties().balance(PartyType.SUPPLIER, sid), "bill reversed (1000)");
            fails(() -> ctx.purchases().reverse(p.id(), "again"), "already been reversed");
            fails(() -> ctx.purchases().receive(new PurchaseService.PurchaseForm(null, null,
                    List.of(new PurchaseService.Line(v, qty("1"), money("10"))), 0, 0, null, null)), "select a supplier");
            fails(() -> ctx.database().db().update("DELETE FROM purchases"), "cannot be deleted");
        }
    }

    // ------------------------------------------------------------ reports

    static void testReportsMatchTransactions() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            long a = TestSupport.simpleProduct(ctx, "Pen", "P", "20", "50", "100", "pcs");
            long b = TestSupport.simpleProduct(ctx, "Book", "B", "200", "300", "100", "pcs");
            long cash = TestSupport.cashMethod(ctx);
            for (int i = 0; i < 3; i++) {
                Cart c = new Cart();
                c.add(ctx.inventory().sellable(a), Qty.of(2));
                c.add(ctx.inventory().sellable(b), Qty.of(1));
                ctx.sales().complete(c, new SaleService.PaymentInput(cash, money("400"), null));
            }
            ctx.expenses().add(LocalDate.now(), "Electricity", money("150"), cash, null);
            LocalDate today = LocalDate.now();
            ReportService.ProfitFigures p = ctx.reports().profit(today, today);
            eq(money("1200"), p.revenue(), "3 x 400");
            eq(money("720"), p.cost(), "3 x (40 + 200)");
            eq(money("480"), p.grossProfit(), "gross profit");
            eq(money("330"), p.netProfit(), "net of expenses");
            for (var d : ReportService.CATALOG) {
                var t = ctx.reports().run(d.id(), today.minusDays(7), today);
                check(t.columns().size() > 0, "report " + d.id() + " has columns");
            }
            var daily = ctx.reports().run("sales.daily", today, today);
            eq(money("1200"), daily.totals()[6], "daily net sales total");
            var products = ctx.reports().run("sales.products", today, today);
            eq(2, products.rows().size(), "two products sold");
            var stock = ctx.reports().run("stock.current", today, today);
            eq(money("21280"), stock.totals()[8], "stock value at cost: 94 x 20 + 97 x 200");
            var dash = ctx.dashboard().overview(today.minusDays(6), today);
            eq(money("1200"), dash.todaySales(), "dashboard today sales");
            eq(money("480"), dash.todayProfit(), "dashboard today profit");
            eq(2L, dash.productCount(), "products");
            var trend = ctx.dashboard().salesTrend(today.minusDays(6), today, ReportService.Grouping.DAY);
            eq(7, trend.size(), "7 days including empty ones");
            eq(money("1200"), trend.get(6).value(), "today's bar");
        }
    }

    static void testPersistenceAfterRestart() throws Exception {
        Path dir = TestSupport.tempDir();
        long v;
        try (AppContext ctx = TestSupport.newContext(dir)) {
            v = TestSupport.simpleProduct(ctx, "Tea", "T", "300", "400", "10", "pcs");
            Cart c = new Cart();
            c.add(ctx.inventory().sellable(v), Qty.of(1));
            ctx.sales().complete(c, new SaleService.PaymentInput(TestSupport.cashMethod(ctx), money("400"), null));
            ctx.settings().save(java.util.Map.of("store.name", "Ahmad General Store"));
        }
        try (AppContext ctx = TestSupport.newContext(dir)) {
            eq(Qty.of(9), ctx.inventory().sellable(v).stockQty(), "stock persisted");
            eq(1, ctx.sales().list(null, null, null, null, null, null, 10).size(), "sale persisted");
            eq("Ahmad General Store", ctx.settings().get().storeName(), "settings persisted");
            eq("INV-000002", ctx.sales().previewNextReceiptNo(), "numbering continues");
        }
    }

    static void testLegacyImport() throws Exception {
        Path dir = TestSupport.tempDir();
        Path old = dir.resolve("shoppro.db");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + old);
             java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE TABLE stores (id INTEGER PRIMARY KEY, name TEXT)");
            st.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, store_id INTEGER, sku TEXT, name TEXT, category TEXT, "
                    + "cost_price TEXT, sale_price TEXT, quantity INTEGER, supplier_id INTEGER)");
            st.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, store_id INTEGER, name TEXT, phone TEXT, address TEXT)");
            st.execute("CREATE TABLE suppliers (id INTEGER PRIMARY KEY, store_id INTEGER, name TEXT, phone TEXT, address TEXT, notes TEXT)");
            st.execute("CREATE TABLE settings (store_id INTEGER, business_name TEXT, address TEXT, phone TEXT, invoice_prefix TEXT, "
                    + "currency_symbol TEXT, footer_message TEXT)");
            st.execute("INSERT INTO stores VALUES (1, 'Old Shop'), (2, 'Other Shop')");
            st.execute("INSERT INTO suppliers VALUES (7, 1, 'Old Supplier', '123', null, null)");
            st.execute("INSERT INTO products VALUES (1, 1, '111', 'Pepsi', 'Drinks', '90', '120.50', 24, 7), "
                    + "(2, 1, '111', 'Dup Code', 'Drinks', '1', '2', 3, null), (3, 1, '', 'Broken', '', 'x', '5', -2, null), "
                    + "(4, 2, '999', 'Elsewhere', '', '1', '1', 1, null)");
            st.execute("INSERT INTO customers VALUES (1, 1, 'Ali', '0300', 'Town')");
            st.execute("INSERT INTO settings VALUES (1, 'Old Shop Ltd', 'Main Rd', '042', 'BILL', 'PKR', 'Thanks')");
        }
        try (AppContext ctx = TestSupport.newContext(dir.resolve("new"))) {
            var stores = ctx.legacyImport().scan(old.toFile());
            eq(2, stores.size(), "two stores found");
            var shop = stores.stream().filter(s -> s.id() == 1).findFirst().orElseThrow();
            var r = ctx.legacyImport().importStore(old.toFile(), shop);
            eq(3, r.products(), "products of store 1 only");
            eq(1, r.customers(), "customers");
            eq(1, r.suppliers(), "suppliers");
            SellableItem pepsi = ctx.inventory().findByCode("111");
            eq("Pepsi", pepsi.productName(), "barcode carried over");
            eq(money("120.50"), pepsi.salePrice(), "price converted to minor units");
            eq(Qty.of(24), pepsi.stockQty(), "stock as opening movement");
            eq("Old Shop Ltd", ctx.settings().get().storeName(), "shop name");
            eq("BILL", ctx.settings().get().receiptPrefix(), "receipt prefix");
            check(r.notes().stream().anyMatch(n -> n.contains("Duplicate code 111")), "duplicate code reported");
            fails(() -> ctx.legacyImport().importStore(old.toFile(), shop), "empty shop");
        }
    }
}
