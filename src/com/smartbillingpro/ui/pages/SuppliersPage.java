package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.PurchaseItem;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.service.PartyService;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PartyLedgerPanel;
import com.smartbillingpro.ui.StatCard;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.PurchaseDialog;
import com.smartbillingpro.ui.dialogs.SupplierDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;

/** Suppliers, their accounts, and stock purchases. */
public final class SuppliersPage extends Page {

    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField search = Ui.search("Search suppliers");
    private final DataTable<Supplier> list = new DataTable<>();
    private final JLabel name = Ui.heading("Select a supplier");
    private final JLabel contact = Ui.muted(" ");
    private final StatCard purchasesCard = new StatCard("Total purchases", "suppliers");
    private final StatCard paidCard = new StatCard("Total paid", "money");
    private final StatCard balanceCard = new StatCard("Balance", "ledgers");
    private final DataTable<Purchase> supplierPurchases = new DataTable<>();
    private final PartyLedgerPanel ledger;
    private final JButton edit = Ui.secondary("Edit", "edit");
    private Supplier current;

    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.THIS_MONTH);
    private final JTextField purchaseSearch = Ui.search("Search purchase no., supplier, bill no.");
    private final DataTable<Purchase> purchases = new DataTable<>();

    public SuppliersPage(MainFrame frame) {
        super(frame, "Suppliers", "Suppliers, purchases and what you owe");
        JButton newPurchase = Ui.primary("New purchase", "plus");
        newPurchase.addActionListener(e -> newPurchase(current == null ? null : current.id()));
        JButton addSupplier = Ui.secondary("Add supplier", "plus");
        addSupplier.addActionListener(e -> {
            SupplierDialog d = new SupplierDialog(this, ctx, null);
            if (d.open(560)) {
                Toast.ok(this, "Supplier added.");
                tabs.setSelectedIndex(0);
                refresh();
                list.rows().stream().filter(s -> s.id() == d.savedId()).findFirst().ifPresent(list::select);
            }
        });
        addAction(addSupplier);
        addAction(newPurchase);
        ledger = new PartyLedgerPanel(ctx, PartyType.SUPPLIER);
        ledger.onChange(this::refresh);
        tabs.addTab("Suppliers", buildSuppliers());
        tabs.addTab("Purchases", buildPurchases());
        tabs.addChangeListener(e -> refresh());
        setBody(tabs);
    }

    private JComponent buildSuppliers() {
        list.text("Supplier", Supplier::name, 170).text("Phone", s -> Text.orDash(s.phone()), 110)
                .signedMoney("You owe", Supplier::balance)
                .status("Status", s -> s.balance() > 0 ? new DataTable.Status("Due", Ui.Tone.WARN)
                        : s.balance() < 0 ? new DataTable.Status("Advance", Ui.Tone.INFO)
                        : new DataTable.Status("Clear", Ui.Tone.GOOD));
        list.setEmptyText("No suppliers yet. Click \"Add supplier\".");
        list.onSelect(this::show);
        Ui.onChange(search, () -> list.filter(search.getText()));
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.add(search, BorderLayout.NORTH);
        left.add(Ui.card(list, 4), BorderLayout.CENTER);
        left.setMinimumSize(new Dimension(300, 200));

        supplierPurchases.text("Purchase", Purchase::purchaseNo, 110).col("Date", DataTable.Kind.DATETIME,
                Purchase::createdAt, 150).text("Bill no.", p -> Text.orDash(p.billRef()), 90).money("Total", Purchase::total)
                .money("Paid", Purchase::amountPaid).status("Status", PageStatus::purchase);
        supplierPurchases.setEmptyText("No purchases from this supplier yet.");
        supplierPurchases.onOpen(p -> viewPurchase(p.id()));
        edit.addActionListener(e -> {
            if (current != null && new SupplierDialog(this, ctx, current).open(560)) {
                Toast.ok(this, "Supplier saved.");
                refresh();
            }
        });
        JButton buy = Ui.secondary("New purchase", "plus");
        buy.addActionListener(e -> newPurchase(current == null ? null : current.id()));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.vbox(2, name, contact), BorderLayout.WEST);
        head.add(Ui.flowRight(6, edit, buy), BorderLayout.EAST);
        JPanel stats = new JPanel(new GridLayout(1, 3, 10, 0));
        stats.setOpaque(false);
        stats.add(purchasesCard);
        stats.add(paidCard);
        stats.add(balanceCard);
        JTabbedPane detailTabs = new JTabbedPane();
        JPanel pp = new JPanel(new BorderLayout());
        pp.setOpaque(false);
        pp.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        pp.add(Ui.card(supplierPurchases, 4), BorderLayout.CENTER);
        detailTabs.addTab("Purchases", pp);
        JPanel lw = new JPanel(new BorderLayout());
        lw.setOpaque(false);
        lw.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        lw.add(ledger, BorderLayout.CENTER);
        detailTabs.addTab("Ledger & payments", lw);
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);
        right.add(Ui.vbox(12, head, stats), BorderLayout.NORTH);
        right.add(detailTabs, BorderLayout.CENTER);
        Card rightCard = new Card(new BorderLayout());
        rightCard.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        rightCard.add(right, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightCard);
        split.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        split.setDividerLocation(400);
        split.setResizeWeight(0.3);
        show(null);
        return split;
    }

    private void show(Supplier s) {
        current = s;
        edit.setEnabled(s != null);
        if (s == null) {
            name.setText("Select a supplier");
            contact.setText("Pick a supplier to see purchases, payments and balance.");
            purchasesCard.set("—", " ");
            paidCard.set("—", " ");
            balanceCard.set("—", " ");
            balanceCard.status(null, null);
            supplierPurchases.setRows(List.of());
            ledger.setParty(null, "");
            return;
        }
        PartyService.SupplierStats st = ctx.parties().supplierStats(s.id());
        name.setText(s.name() + (s.active() ? "" : "  (inactive)"));
        contact.setText(String.join("   •   ", java.util.stream.Stream.of(s.phone(), s.address(), s.businessInfo(),
                s.notes()).filter(x -> !Text.blank(x)).toList()));
        purchasesCard.set(Money.format(st.totalPurchases()), st.purchaseCount() + " purchase(s)"
                + (st.lastPurchase() == null ? "" : ", last " + Dates.formatDate(st.lastPurchase())));
        paidCard.set(Money.format(st.totalPaid()), "Payments made");
        balanceCard.set(Money.format(Math.abs(st.balance())), st.balance() > 0 ? "You owe" : st.balance() < 0
                ? "Advance with supplier" : "Nothing due");
        balanceCard.status(st.balance() > 0 ? "Due" : "Clear", st.balance() > 0 ? Ui.Tone.WARN : Ui.Tone.GOOD);
        supplierPurchases.setRows(ctx.purchases().list(null, null, s.id(), null));
        ledger.setParty(s.id(), s.name());
    }

    private JComponent buildPurchases() {
        purchases.text("Purchase", Purchase::purchaseNo, 110).col("Date", DataTable.Kind.DATETIME, Purchase::createdAt, 150)
                .text("Supplier", p -> p.supplierName() == null ? "No supplier" : p.supplierName(), 180)
                .text("Bill no.", p -> Text.orDash(p.billRef()), 90).money("Total", Purchase::total)
                .money("Paid", Purchase::amountPaid).money("Due", Purchase::balanceDue)
                .status("Status", PageStatus::purchase);
        purchases.setEmptyText("No purchases in this period.");
        purchases.onOpen(p -> viewPurchase(p.id()));
        range.onChange(this::loadPurchases);
        Ui.onChange(purchaseSearch, () -> purchases.filter(purchaseSearch.getText()));
        purchaseSearch.setColumns(22);
        JButton view = Ui.secondary("View", "file");
        view.addActionListener(e -> {
            Purchase p = purchases.selected();
            if (p != null) {
                viewPurchase(p.id());
            }
        });
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(range, BorderLayout.WEST);
        bar.add(Ui.flowRight(6, purchaseSearch, view), BorderLayout.EAST);
        return InventoryPage.panel(bar, purchases);
    }

    private void loadPurchases() {
        List<Purchase> rows = ctx.purchases().list(Dates.startOf(range.from()), Dates.endExclusive(range.to()), null, null);
        purchases.setRows(rows);
        purchases.filter(purchaseSearch.getText());
        long total = rows.stream().filter(p -> !p.reversed()).mapToLong(Purchase::total).sum();
        setSubtitle(rows.size() + " purchase(s) • " + Money.format(total) + " in " + range.label().toLowerCase());
    }

    private void newPurchase(Long supplierId) {
        PurchaseDialog d = new PurchaseDialog(this, ctx, supplierId);
        if (d.open(780)) {
            Toast.ok(this, "Purchase " + d.saved().purchaseNo() + " saved. Stock updated.");
            refresh();
        }
    }

    private void viewPurchase(long id) {
        Purchase p = ctx.purchases().find(id);
        StringBuilder sb = new StringBuilder();
        sb.append(p.purchaseNo()).append("   •   ").append(Dates.formatDateTime(p.createdAt())).append('\n');
        sb.append("Supplier: ").append(p.supplierName() == null ? "No supplier" : p.supplierName());
        if (!Text.blank(p.billRef())) {
            sb.append("   •   Bill ").append(p.billRef());
        }
        sb.append("\n\n");
        for (PurchaseItem i : p.items()) {
            sb.append(i.displayName()).append(":  ").append(Qty.format(i.quantity(), i.unitCode())).append(" × ")
                    .append(Money.format(i.unitCost())).append(" = ").append(Money.format(i.lineTotal())).append('\n');
        }
        sb.append("\nSubtotal ").append(Money.format(p.subtotal()));
        if (p.discount() > 0) {
            sb.append("   •   Discount ").append(Money.format(p.discount()));
        }
        sb.append("\nTotal ").append(Money.format(p.total())).append("   •   Paid ").append(Money.format(p.amountPaid()))
                .append(p.paymentMethodName() == null ? "" : " (" + p.paymentMethodName() + ")")
                .append("   •   Due ").append(Money.format(p.balanceDue()));
        if (!Text.blank(p.note())) {
            sb.append("\nNote: ").append(p.note());
        }
        if (p.reversed()) {
            sb.append("\n\nREVERSED: ").append(Text.trim(p.reversalReason()));
            Msg.info(this, "Purchase " + p.purchaseNo(), sb.toString());
            return;
        }
        Object[] options = {"Close", "Reverse this purchase…"};
        int r = javax.swing.JOptionPane.showOptionDialog(this, sb.toString(), "Purchase " + p.purchaseNo(),
                javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (r == 1) {
            String reason = Msg.ask(this, "Reverse purchase", "Reversing removes the stock it added and cancels the bill on "
                    + "the supplier's account. The purchase stays in the history.\n\nReason:", "");
            if (reason != null) {
                safely(() -> {
                    ctx.purchases().reverse(p.id(), reason);
                    Toast.ok(this, "Purchase reversed.");
                    refresh();
                });
            }
        }
    }

    @Override
    public void refresh() {
        if (tabs.getSelectedIndex() == 1) {
            loadPurchases();
            return;
        }
        Long keep = current == null ? null : current.id();
        List<Supplier> all = ctx.parties().suppliers(true);
        list.setRows(all);
        list.filter(search.getText());
        setSubtitle(all.size() + " supplier(s)  •  you owe " + Money.format(all.stream()
                .mapToLong(s -> Math.max(0, s.balance())).sum()));
        if (keep == null && !all.isEmpty() && list.selected() == null) {
            list.selectFirst();
        } else if (keep != null) {
            all.stream().filter(s -> s.id() == keep).findFirst().ifPresentOrElse(s -> {
                list.select(s);
                show(s);
            }, () -> show(null));
        }
    }
}
