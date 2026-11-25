package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.service.DashboardService;
import com.smartbillingpro.service.PartyService;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.Detail;
import com.smartbillingpro.ui.EmptyState;
import com.smartbillingpro.ui.LineChart;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PillGroup;
import com.smartbillingpro.ui.RowList;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.AdjustBalanceDialog;
import com.smartbillingpro.ui.dialogs.CustomerDialog;
import com.smartbillingpro.ui.dialogs.PaymentDialog;
import com.smartbillingpro.ui.dialogs.ReceiptDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.stream.Stream;

/** Customers on the left; the chosen customer's balance, buying history and receipts on the right. */
public final class CustomersPage extends Page {

    private enum Show {
        ALL, OWE
    }

    private final JTextField search = Ui.search("Search name or phone");
    private final PillGroup<Show> show = new PillGroup<>();
    private final RowList<Customer> list = new RowList<>(CustomersPage::row, 60);
    private final Detail detail = new Detail(new EmptyState("customers", "Choose a customer",
            "Their balance, purchases and receipts show up here.", true));
    private final CardLayout cards = new CardLayout();
    private final JPanel holder = new JPanel(cards);
    private List<Customer> all = List.of();
    private Customer current;

    public CustomersPage(MainFrame frame) {
        super(frame, "Customers", "People who buy from you, and what they owe");
        JButton add = Ui.primary("New customer", "plus");
        add.addActionListener(e -> addCustomer());
        addAction(add);

        show.setItems(List.of(Show.values()), s -> s == Show.ALL ? "All" : "Owe you");
        show.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        show.onChange(s -> apply());
        Ui.onChange(search, () -> {
            list.filter(search.getText());
            if (list.selected() == null) {
                list.selectFirst();
            }
        });
        list.onSelect(this::show);
        list.onOpen(c -> edit(c), false);
        list.setEmptyState(new EmptyState("check", "Nobody owes you money", "Customers who buy on credit show up here.", true));

        search.setColumns(20);
        JPanel bar = Ui.toolbar(new java.awt.Component[] {search, show});

        Card listCard = new Card(new BorderLayout());
        listCard.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        listCard.add(list, BorderLayout.CENTER);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(bar, BorderLayout.NORTH);
        content.add(split(listCard, detail), BorderLayout.CENTER);

        EmptyState none = new EmptyState("customers", "No customers yet",
                "You don't need customers for normal cash sales. Add regular customers to sell on credit "
                        + "and keep track of what they owe.")
                .action("Add a customer", "plus", this::addCustomer);
        holder.setOpaque(false);
        holder.add(content, "content");
        holder.add(none, "empty");
        setBody(holder);
    }

    /** List on the left (about 40%), details on the right. */
    static JPanel split(JComponent left, JComponent right) {
        JPanel split = new JPanel(new GridBagLayout());
        split.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 0.42;
        left.setPreferredSize(new Dimension(100, 100));
        split.add(left, c);
        c.gridx = 1;
        c.weightx = 0.58;
        c.insets = new Insets(0, 14, 0, 0);
        right.setPreferredSize(new Dimension(100, 100));
        split.add(right, c);
        return split;
    }

    private static RowList.Row row(Customer c) {
        String sub = Text.blank(c.phone()) ? "No phone number" : c.phone();
        RowList.Row r;
        if (c.balance() > 0) {
            r = RowList.Row.of(c.name(), sub, Money.format(c.balance())).withStatus("owes you", Ui.Tone.WARN);
        } else if (c.balance() < 0) {
            r = RowList.Row.of(c.name(), sub, Money.format(-c.balance())).withStatus("paid ahead", Ui.Tone.GOOD);
        } else {
            r = RowList.Row.of(c.name(), sub, null).withStatus("Nothing owed", Ui.Tone.NEUTRAL);
        }
        return r.dimmed(!c.active());
    }

    private void apply() {
        Show s = show.selected() == null ? Show.ALL : show.selected();
        list.setItems(s == Show.OWE ? all.stream().filter(c -> c.balance() > 0).toList() : all);
        list.filter(search.getText());
        if (list.selected() == null) {
            list.selectFirst();
        }
        show(list.selected());
    }

    private void show(Customer c) {
        current = c;
        if (c == null) {
            detail.showNothing();
            return;
        }
        PartyService.CustomerStats s = ctx.parties().customerStats(c.id());
        detail.begin();
        JButton receive = Ui.primary("Receive payment", "money");
        receive.setToolTipText("Record money this customer paid you");
        receive.addActionListener(e -> receivePayment());
        JButton edit = Ui.secondary("Edit", "edit");
        edit.addActionListener(e -> edit(current));
        JButton more = moreButton(menu -> {
            JMenuItem history = new JMenuItem("See full account history");
            history.addActionListener(x -> openLedger());
            JMenuItem adjust = new JMenuItem("Correct the balance…");
            adjust.addActionListener(x -> adjustBalance());
            JMenuItem hide = new JMenuItem(current.active() ? "Hide this customer" : "Show this customer again");
            hide.addActionListener(x -> toggleActive());
            menu.add(history);
            menu.add(adjust);
            menu.addSeparator();
            menu.add(hide);
        });
        String contact = String.join("  •  ", Stream.of(c.phone(), c.address()).filter(x -> !Text.blank(x)).toList());
        detail.add(Detail.header(c.name() + (c.active() ? "" : "  (hidden)"), contact.isBlank() ? "No contact details" : contact,
                Ui.flowLeft(8, receive, edit, more)));
        long bal = s.balance();
        detail.add(Detail.tiles(
                Detail.tile(bal < 0 ? "Paid ahead" : "Owes you", Money.format(Math.abs(bal)),
                        bal > 0 ? "Not paid yet" : bal < 0 ? "Will be used on the next bill" : "All settled",
                        bal > 0 ? Ui.Tone.WARN : Ui.Tone.GOOD),
                Detail.tile("Bought in total", Money.format(s.totalPurchases()),
                        s.salesCount() == 1 ? "1 receipt" : s.salesCount() + " receipts", Ui.Tone.NEUTRAL),
                Detail.tile("Last visit", s.lastSale() == null ? "—" : Dates.formatDate(s.lastSale()),
                        s.lastSale() == null ? "Hasn't bought yet" : " ", Ui.Tone.NEUTRAL)));
        if (!Text.blank(c.notes())) {
            JLabel notes = Ui.muted("Note: " + c.notes());
            notes.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
            detail.add(Detail.capped(notes));
        }
        detail.add(monthlyChart("Bought each month", ctx.dashboard().customerMonthly(c.id(), 6),
                "No purchases in the last 6 months"));
        List<Sale> recent = ctx.sales().list(null, null, null, null, c.id(), null, 8);
        JButton all = Ui.ghost("See all", null);
        all.addActionListener(e -> {
            ReceiptsPage page = frame.page(MainFrame.RECEIPTS);
            frame.show(MainFrame.RECEIPTS);
            page.searchFor(current.name());
        });
        detail.add(Detail.section("Recent receipts", recent.size() >= 8 ? all : null));
        if (recent.isEmpty()) {
            detail.add(Detail.capped(Ui.muted("No receipts yet.")));
        }
        for (Sale sale : recent) {
            DataTable.Status st = PageStatus.sale(sale);
            JLabel total = new JLabel(Money.format(sale.grandTotal()));
            total.setFont(Theme.bold(14));
            JLabel pill = Ui.pill(st.text(), st.tone());
            JButton open = Ui.ghost("View", null);
            open.addActionListener(e -> new ReceiptDialog(this, ctx, ctx.sales().find(sale.id())).open(560));
            detail.add(Detail.line(sale.receiptNo(), Dates.formatDateTime(sale.createdAt()), Ui.flowRight(8, pill, total, open)));
        }
        detail.done();
    }

    /** "More ▾" button that opens a small menu. */
    static JButton moreButton(java.util.function.Consumer<JPopupMenu> fill) {
        JButton more = Ui.icon("chevron-down", "More");
        more.setText("More");
        more.setHorizontalTextPosition(SwingConstants.LEFT);
        more.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            fill.accept(menu);
            menu.show(more, 0, more.getHeight() + 4);
        });
        return more;
    }

    /** Six-month bar chart of money amounts. */
    static JComponent monthlyChart(String title, List<DashboardService.Point> points, String emptyText) {
        LineChart chart = new LineChart(emptyText);
        chart.setBars(true);
        chart.setData(points.stream().map(p -> new LineChart.Point(p.label(), p.value(), 0)).toList(), "Amount", null);
        chart.setPreferredSize(new Dimension(300, 180));
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setOpaque(false);
        box.add(Detail.section(title, null), BorderLayout.NORTH);
        box.add(chart, BorderLayout.CENTER);
        box.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));
        return box;
    }

    private void addCustomer() {
        CustomerDialog d = new CustomerDialog(this, ctx, null);
        if (d.open(480)) {
            Toast.ok(this, "Customer added.");
            refresh();
            select(d.savedId());
        }
    }

    private void edit(Customer c) {
        if (c != null && new CustomerDialog(this, ctx, c).open(480)) {
            Toast.ok(this, "Changes saved.");
            refresh();
            select(c.id());
        }
    }

    private void receivePayment() {
        if (current != null && new PaymentDialog(this, ctx, PartyType.CUSTOMER, current.id(), current.name()).open(480)) {
            Toast.ok(this, "Payment recorded.");
            long id = current.id();
            refresh();
            select(id);
        }
    }

    private void adjustBalance() {
        if (current != null && new AdjustBalanceDialog(this, ctx, PartyType.CUSTOMER, current.id(), current.name()).open(520)) {
            Toast.ok(this, "Balance corrected. The change is kept in the account history.");
            long id = current.id();
            refresh();
            select(id);
        }
    }

    private void toggleActive() {
        Customer c = current;
        if (c == null) {
            return;
        }
        if (c.active() && !Msg.confirm(this, "Hide " + c.name() + "?", "They will not appear when choosing a customer "
                + "on the Sales screen. Their history is kept and you can show them again any time.", "Hide")) {
            return;
        }
        safely(() -> {
            ctx.parties().saveCustomer(c.id(), c.name(), c.phone(), c.address(), c.notes(), 0, !c.active());
            refresh();
            select(c.id());
        });
    }

    private void openLedger() {
        if (current == null) {
            return;
        }
        LedgersPage page = frame.page(MainFrame.LEDGERS);
        frame.show(MainFrame.LEDGERS);
        page.openParty(PartyType.CUSTOMER, current.id());
    }

    private void select(long id) {
        if (show.selected() == Show.OWE && all.stream().noneMatch(c -> c.id() == id && c.balance() > 0)) {
            show.select(Show.ALL);
            apply();
        }
        list.items().stream().filter(c -> c.id() == id).findFirst().ifPresent(list::select);
        show(list.selected());
    }

    @Override
    public void refresh() {
        Long keep = current == null ? null : current.id();
        all = ctx.parties().customers(true);
        long owing = all.stream().filter(c -> c.balance() > 0).count();
        long owed = all.stream().mapToLong(c -> Math.max(0, c.balance())).sum();
        show.setItems(List.of(Show.values()), s -> s == Show.ALL ? "All" : owing > 0 ? "Owe you • " + owing : "Owe you");
        setSubtitle(all.isEmpty() ? "People who buy from you, and what they owe"
                : all.size() + (all.size() == 1 ? " customer" : " customers")
                + (owed > 0 ? "  •  they owe you " + Money.format(owed) + " in total" : "  •  nobody owes you anything"));
        cards.show(holder, all.isEmpty() ? "empty" : "content");
        apply();
        if (keep != null) {
            list.items().stream().filter(c -> c.id() == keep).findFirst().ifPresent(list::select);
            show(list.selected());
        }
    }

    @Override
    public void focusDefault() {
        if (!all.isEmpty()) {
            search.requestFocusInWindow();
        }
    }

    /** Select a customer programmatically (from other screens). */
    public void open(long customerId) {
        refresh();
        select(customerId);
    }
}
