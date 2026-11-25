package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.service.PartyService;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.Detail;
import com.smartbillingpro.ui.EmptyState;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PillGroup;
import com.smartbillingpro.ui.RowList;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.AdjustBalanceDialog;
import com.smartbillingpro.ui.dialogs.PaymentDialog;
import com.smartbillingpro.ui.dialogs.PurchaseDialog;
import com.smartbillingpro.ui.dialogs.PurchaseViewDialog;
import com.smartbillingpro.ui.dialogs.SupplierDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.stream.Stream;

/** The people you buy stock from, what you owe them, and every purchase. */
public final class SuppliersPage extends Page {

    private enum Show {
        ALL, OWED
    }

    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField search = Ui.search("Search suppliers");
    private final PillGroup<Show> show = new PillGroup<>();
    private final RowList<Supplier> list = new RowList<>(SuppliersPage::row, 60);
    private final Detail detail = new Detail(new EmptyState("suppliers", "Choose a supplier",
            "What you owe them and what you bought shows up here.", true));
    private final CardLayout supplierCards = new CardLayout();
    private final JPanel supplierHolder = new JPanel(supplierCards);
    private List<Supplier> all = List.of();
    private Supplier current;

    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.THIS_MONTH);
    private final JTextField purchaseSearch = Ui.search("Purchase no., supplier or bill no.");
    private final RowList<Purchase> purchases = new RowList<>(SuppliersPage::purchaseRow, 60);

    public SuppliersPage(MainFrame frame) {
        super(frame, "Suppliers", "Who you buy stock from, and what you owe them");
        JButton addSupplier = Ui.secondary("New supplier", "plus");
        addSupplier.addActionListener(e -> addSupplier());
        JButton newPurchase = Ui.primary("New purchase", "plus");
        newPurchase.setToolTipText("Record stock you bought – it is added to your stock");
        newPurchase.addActionListener(e -> newPurchase(tabs.getSelectedIndex() == 0 && current != null ? current.id() : null));
        addAction(addSupplier);
        addAction(newPurchase);
        tabs.addTab("Suppliers", buildSuppliers());
        tabs.addTab("Purchases", buildPurchases());
        tabs.addChangeListener(e -> refresh());
        setBody(tabs);
    }

    // ================================================================ suppliers

    private JComponent buildSuppliers() {
        show.setItems(List.of(Show.values()), s -> s == Show.ALL ? "All" : "You owe");
        show.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        show.onChange(s -> apply());
        Ui.onChange(search, () -> {
            list.filter(search.getText());
            if (list.selected() == null) {
                list.selectFirst();
            }
        });
        list.onSelect(this::show);
        list.onOpen(this::edit, false);
        list.setEmptyState(new EmptyState("check", "You don't owe anyone", "Suppliers you owe money to show up here.", true));
        search.setColumns(20);
        JPanel bar = Ui.toolbar(new java.awt.Component[] {search, show});
        Card listCard = new Card(new BorderLayout());
        listCard.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        listCard.add(list, BorderLayout.CENTER);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        content.add(bar, BorderLayout.NORTH);
        content.add(CustomersPage.split(listCard, detail), BorderLayout.CENTER);
        EmptyState none = new EmptyState("suppliers", "No suppliers yet",
                "Add the people or companies you buy stock from, to keep track of what you owe them. "
                        + "You can record a purchase without one, too.")
                .action("Add a supplier", "plus", this::addSupplier)
                .secondary("Record a purchase", () -> newPurchase(null));
        supplierHolder.setOpaque(false);
        supplierHolder.add(content, "content");
        supplierHolder.add(none, "empty");
        return supplierHolder;
    }

    private static RowList.Row row(Supplier s) {
        String sub = Text.blank(s.phone()) ? Text.blank(s.businessInfo()) ? "No phone number" : s.businessInfo() : s.phone();
        RowList.Row r;
        if (s.balance() > 0) {
            r = RowList.Row.of(s.name(), sub, Money.format(s.balance())).withStatus("you owe", Ui.Tone.WARN);
        } else if (s.balance() < 0) {
            r = RowList.Row.of(s.name(), sub, Money.format(-s.balance())).withStatus("paid ahead", Ui.Tone.GOOD);
        } else {
            r = RowList.Row.of(s.name(), sub, null).withStatus("Nothing owed", Ui.Tone.NEUTRAL);
        }
        return r.dimmed(!s.active());
    }

    private void apply() {
        Show s = show.selected() == null ? Show.ALL : show.selected();
        list.setItems(s == Show.OWED ? all.stream().filter(x -> x.balance() > 0).toList() : all);
        list.filter(search.getText());
        if (list.selected() == null) {
            list.selectFirst();
        }
        show(list.selected());
    }

    private void show(Supplier s) {
        current = s;
        if (s == null) {
            detail.showNothing();
            return;
        }
        PartyService.SupplierStats st = ctx.parties().supplierStats(s.id());
        detail.begin();
        JButton pay = Ui.primary("Pay", "money");
        pay.setToolTipText("Record money you paid this supplier");
        pay.addActionListener(e -> pay());
        JButton edit = Ui.secondary("Edit", "edit");
        edit.addActionListener(e -> edit(current));
        JButton more = CustomersPage.moreButton(menu -> {
            JMenuItem buy = new JMenuItem("Record a purchase from " + current.name());
            buy.addActionListener(x -> newPurchase(current.id()));
            JMenuItem history = new JMenuItem("See full account history");
            history.addActionListener(x -> openLedger());
            JMenuItem adjust = new JMenuItem("Correct the balance…");
            adjust.addActionListener(x -> adjustBalance());
            JMenuItem hide = new JMenuItem(current.active() ? "Hide this supplier" : "Show this supplier again");
            hide.addActionListener(x -> toggleActive());
            menu.add(buy);
            menu.add(history);
            menu.add(adjust);
            menu.addSeparator();
            menu.add(hide);
        });
        String contact = String.join("  •  ", Stream.of(s.phone(), s.address(), s.businessInfo())
                .filter(x -> !Text.blank(x)).toList());
        detail.add(Detail.header(s.name() + (s.active() ? "" : "  (hidden)"), contact.isBlank() ? "No contact details" : contact,
                Ui.flowLeft(8, pay, edit, more)));
        long bal = st.balance();
        detail.add(Detail.tiles(
                Detail.tile(bal < 0 ? "Paid ahead" : "You owe", Money.format(Math.abs(bal)),
                        bal > 0 ? "Not paid yet" : bal < 0 ? "Will be used on the next bill" : "All settled",
                        bal > 0 ? Ui.Tone.WARN : Ui.Tone.GOOD),
                Detail.tile("Bought in total", Money.format(st.totalPurchases()),
                        st.purchaseCount() == 1 ? "1 purchase" : st.purchaseCount() + " purchases", Ui.Tone.NEUTRAL),
                Detail.tile("Last purchase", st.lastPurchase() == null ? "—" : Dates.formatDate(st.lastPurchase()),
                        st.lastPurchase() == null ? "Nothing bought yet" : " ", Ui.Tone.NEUTRAL)));
        if (!Text.blank(s.notes())) {
            JLabel notes = Ui.muted("Note: " + s.notes());
            notes.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
            detail.add(Detail.capped(notes));
        }
        detail.add(CustomersPage.monthlyChart("Bought each month", ctx.dashboard().supplierMonthly(s.id(), 6),
                "No purchases in the last 6 months"));
        List<Purchase> recent = ctx.purchases().list(null, null, s.id(), null);
        JButton allBtn = Ui.ghost("See all", null);
        allBtn.addActionListener(e -> {
            range.setPreset(DateRangeBar.Preset.ALL_TIME);
            purchaseSearch.setText(current.name());
            tabs.setSelectedIndex(1);
        });
        detail.add(Detail.section("Recent purchases", recent.size() > 8 ? allBtn : null));
        if (recent.isEmpty()) {
            detail.add(Detail.capped(Ui.muted("No purchases yet.")));
        }
        for (Purchase p : recent.stream().limit(8).toList()) {
            DataTable.Status status = PageStatus.purchase(p);
            JLabel total = new JLabel(Money.format(p.total()));
            total.setFont(Theme.bold(14));
            JButton view = Ui.ghost("View", null);
            view.addActionListener(e -> viewPurchase(p.id()));
            detail.add(Detail.line(p.purchaseNo() + (Text.blank(p.billRef()) ? "" : "  •  bill " + p.billRef()),
                    Dates.formatDateTime(p.createdAt()), Ui.flowRight(8, Ui.pill(status.text(), status.tone()), total, view)));
        }
        detail.done();
    }

    private void addSupplier() {
        SupplierDialog d = new SupplierDialog(this, ctx, null);
        if (d.open(480)) {
            Toast.ok(this, "Supplier added.");
            tabs.setSelectedIndex(0);
            refresh();
            select(d.savedId());
        }
    }

    private void edit(Supplier s) {
        if (s != null && new SupplierDialog(this, ctx, s).open(480)) {
            Toast.ok(this, "Changes saved.");
            refresh();
            select(s.id());
        }
    }

    private void pay() {
        if (current != null && new PaymentDialog(this, ctx, PartyType.SUPPLIER, current.id(), current.name()).open(480)) {
            Toast.ok(this, "Payment recorded.");
            long id = current.id();
            refresh();
            select(id);
        }
    }

    private void adjustBalance() {
        if (current != null && new AdjustBalanceDialog(this, ctx, PartyType.SUPPLIER, current.id(), current.name()).open(520)) {
            Toast.ok(this, "Balance corrected. The change is kept in the account history.");
            long id = current.id();
            refresh();
            select(id);
        }
    }

    private void toggleActive() {
        Supplier s = current;
        if (s == null) {
            return;
        }
        if (s.active() && !Msg.confirm(this, "Hide " + s.name() + "?", "They will not appear when recording a purchase. "
                + "Their history is kept and you can show them again any time.", "Hide")) {
            return;
        }
        safely(() -> {
            ctx.parties().saveSupplier(s.id(), s.name(), s.phone(), s.address(), s.businessInfo(), s.notes(), 0, !s.active());
            refresh();
            select(s.id());
        });
    }

    private void openLedger() {
        if (current == null) {
            return;
        }
        LedgersPage page = frame.page(MainFrame.LEDGERS);
        frame.show(MainFrame.LEDGERS);
        page.openParty(PartyType.SUPPLIER, current.id());
    }

    private void select(long id) {
        if (show.selected() == Show.OWED && all.stream().noneMatch(s -> s.id() == id && s.balance() > 0)) {
            show.select(Show.ALL);
            apply();
        }
        list.items().stream().filter(s -> s.id() == id).findFirst().ifPresent(list::select);
        show(list.selected());
    }

    // ================================================================ purchases

    private JComponent buildPurchases() {
        purchases.onOpen(p -> viewPurchase(p.id()), true);
        purchases.setEmptyState(new EmptyState("suppliers", "No purchases in this period",
                "When you buy stock, record it here – your stock goes up and what you owe is kept track of.", true)
                .action("Record a purchase", "plus", () -> newPurchase(null)));
        range.onChange(this::loadPurchases);
        Ui.onChange(purchaseSearch, () -> purchases.filter(purchaseSearch.getText()));
        purchaseSearch.setColumns(22);
        JPanel bar = Ui.toolbar(new java.awt.Component[] {purchaseSearch, range});
        Card c = new Card(new BorderLayout());
        c.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        c.add(purchases, BorderLayout.CENTER);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(bar, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static RowList.Row purchaseRow(Purchase p) {
        DataTable.Status st = PageStatus.purchase(p);
        return RowList.Row.of(p.purchaseNo() + "  •  " + (p.supplierName() == null ? "No saved supplier" : p.supplierName()),
                Dates.formatDateTime(p.createdAt()) + (Text.blank(p.billRef()) ? "" : "  •  bill " + p.billRef()),
                Money.format(p.total())).withIcon("suppliers").withStatus(st.text(), st.tone()).dimmed(p.reversed());
    }

    private void loadPurchases() {
        List<Purchase> rows = ctx.purchases().list(Dates.startOf(range.from()), Dates.endExclusive(range.to()), null, null);
        purchases.setItems(rows);
        purchases.filter(purchaseSearch.getText());
        long total = rows.stream().filter(p -> !p.reversed()).mapToLong(Purchase::total).sum();
        setSubtitle(rows.isEmpty() ? "Who you buy stock from, and what you owe them"
                : rows.size() + (rows.size() == 1 ? " purchase" : " purchases") + "  •  " + Money.format(total)
                + "  •  " + range.label().toLowerCase());
    }

    private void newPurchase(Long supplierId) {
        PurchaseDialog d = new PurchaseDialog(this, ctx, supplierId);
        if (d.open(760)) {
            Toast.ok(this, "Purchase " + d.saved().purchaseNo() + " saved. Your stock is updated.");
            refresh();
        }
    }

    private void viewPurchase(long id) {
        PurchaseViewDialog d = new PurchaseViewDialog(this, ctx, id);
        d.open(560);
        if (d.wasReversed()) {
            Toast.ok(this, "Purchase cancelled. Stock and the supplier's balance are updated.");
            refresh();
        }
    }

    @Override
    public void refresh() {
        if (tabs.getSelectedIndex() == 1) {
            loadPurchases();
            return;
        }
        Long keep = current == null ? null : current.id();
        all = ctx.parties().suppliers(true);
        long owing = all.stream().filter(s -> s.balance() > 0).count();
        long owed = all.stream().mapToLong(s -> Math.max(0, s.balance())).sum();
        show.setItems(List.of(Show.values()), s -> s == Show.ALL ? "All" : owing > 0 ? "You owe • " + owing : "You owe");
        setSubtitle(all.isEmpty() ? "Who you buy stock from, and what you owe them"
                : all.size() + (all.size() == 1 ? " supplier" : " suppliers")
                + (owed > 0 ? "  •  you owe " + Money.format(owed) + " in total" : "  •  you don't owe anyone"));
        supplierCards.show(supplierHolder, all.isEmpty() ? "empty" : "content");
        apply();
        if (keep != null) {
            list.items().stream().filter(s -> s.id() == keep).findFirst().ifPresent(list::select);
            show(list.selected());
        }
    }

    @Override
    public void focusDefault() {
        if (tabs.getSelectedIndex() == 0 && !all.isEmpty()) {
            search.requestFocusInWindow();
        }
    }
}
