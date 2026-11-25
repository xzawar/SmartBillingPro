package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.Expense;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.service.DashboardService;
import com.smartbillingpro.ui.BarList;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.EmptyState;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PartyLedgerPanel;
import com.smartbillingpro.ui.PillGroup;
import com.smartbillingpro.ui.RowList;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.ExpenseDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
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

/** Who owes whom (with every entry and a running balance) and the shop's own expenses. */
public final class LedgersPage extends Page {

    /** One account in the list. */
    private record Account(long id, String name, String phone, long balance) {
    }

    private final JTabbedPane tabs = new JTabbedPane();
    private final AccountTab customers;
    private final AccountTab suppliers;
    private final DateRangeBar expenseRange = new DateRangeBar(DateRangeBar.Preset.THIS_MONTH);
    private final RowList<Expense> expenses = new RowList<>(LedgersPage::expenseRow, 58);
    private final BarList byCategory = new BarList("No expenses in this period");
    private final JButton cancelExpense = Ui.ghost("Cancel expense…", "close");
    private final JLabel expenseTotal = new JLabel(" ");

    public LedgersPage(MainFrame frame) {
        super(frame, "Ledgers", "Who owes whom, every payment, and what the shop spends");
        customers = new AccountTab(PartyType.CUSTOMER);
        suppliers = new AccountTab(PartyType.SUPPLIER);
        tabs.addTab("Customer accounts", customers.panel);
        tabs.addTab("Supplier accounts", suppliers.panel);
        tabs.addTab("Shop expenses", buildExpenses());
        tabs.addChangeListener(e -> refresh());
        setBody(tabs);
    }

    private enum Filter {
        ALL, OPEN
    }

    private final class AccountTab {
        final PartyType type;
        final JTextField search = Ui.search("Search");
        final PillGroup<Filter> filter = new PillGroup<>();
        final RowList<Account> list;
        final PartyLedgerPanel ledger;
        final CardLayout cards = new CardLayout();
        final JPanel panel = new JPanel(cards);
        final CardLayout rightCards = new CardLayout();
        final JPanel right = new JPanel(rightCards);
        List<Account> all = List.of();

        AccountTab(PartyType type) {
            this.type = type;
            boolean cust = type == PartyType.CUSTOMER;
            list = new RowList<>(a -> {
                RowList.Row r = RowList.Row.of(a.name(), Text.blank(a.phone()) ? " " : a.phone(),
                        a.balance() == 0 ? null : Money.format(Math.abs(a.balance())));
                return a.balance() > 0 ? r.withStatus(cust ? "owes you" : "you owe", Ui.Tone.WARN)
                        : a.balance() < 0 ? r.withStatus("paid ahead", Ui.Tone.GOOD) : r.withStatus("settled", Ui.Tone.NEUTRAL);
            }, 58);
            ledger = new PartyLedgerPanel(ctx, type);
            ledger.onChange(this::load);
            list.onSelect(a -> {
                ledger.setParty(a == null ? null : a.id(), a == null ? "" : a.name());
                rightCards.show(right, a == null ? "none" : "ledger");
            });
            list.setEmptyState(new EmptyState("check", "All settled",
                    cust ? "No customer owes you anything." : "You don't owe any supplier.", true));
            Ui.onChange(search, () -> list.filter(search.getText()));
            filter.setItems(List.of(Filter.values()), f -> f == Filter.ALL ? "All" : cust ? "Owe you" : "You owe");
            filter.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            filter.onChange(f -> apply());
            JPanel left = new JPanel(new BorderLayout(0, 8));
            left.setOpaque(false);
            left.add(Ui.vbox(8, search, filter), BorderLayout.NORTH);
            Card listCard = new Card(new BorderLayout());
            listCard.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
            listCard.add(list, BorderLayout.CENTER);
            left.add(listCard, BorderLayout.CENTER);
            Card ledgerCard = new Card(new BorderLayout());
            ledgerCard.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            ledgerCard.add(ledger, BorderLayout.CENTER);
            Card nothing = new Card(new BorderLayout());
            nothing.add(new EmptyState("ledgers", cust ? "Choose a customer" : "Choose a supplier",
                    "Every sale, payment and correction on their account shows up here.", true), BorderLayout.CENTER);
            right.setOpaque(false);
            right.add(ledgerCard, "ledger");
            right.add(nothing, "none");
            JPanel split = new JPanel(new GridBagLayout());
            split.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.BOTH;
            c.weighty = 1;
            c.weightx = 0.3;
            left.setPreferredSize(new Dimension(100, 100));
            split.add(left, c);
            c.gridx = 1;
            c.weightx = 0.7;
            c.insets = new Insets(0, 14, 0, 0);
            right.setPreferredSize(new Dimension(100, 100));
            split.add(right, c);
            split.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
            EmptyState none = cust
                    ? new EmptyState("customers", "No customer accounts yet",
                    "When you sell on credit to a customer, or they pay you later, it is all recorded here.")
                    .action("Go to Customers", "customers", () -> frame.show(MainFrame.CUSTOMERS))
                    : new EmptyState("suppliers", "No supplier accounts yet",
                    "Purchases you haven't fully paid for, and the payments you make, are recorded here.")
                    .action("Go to Suppliers", "suppliers", () -> frame.show(MainFrame.SUPPLIERS));
            panel.setOpaque(false);
            panel.add(split, "content");
            panel.add(none, "empty");
        }

        void load() {
            Account keep = list.selected();
            all = type == PartyType.CUSTOMER
                    ? ctx.parties().customers(true).stream().map(c -> new Account(c.id(), c.name(), c.phone(), c.balance())).toList()
                    : ctx.parties().suppliers(true).stream().map(s -> new Account(s.id(), s.name(), s.phone(), s.balance())).toList();
            cards.show(panel, all.isEmpty() ? "empty" : "content");
            apply();
            if (keep != null) {
                list.items().stream().filter(a -> a.id() == keep.id()).findFirst().ifPresent(list::select);
            }
            if (list.selected() == null) {
                list.selectFirst();
            }
            ledger.reload();
            long total = all.stream().mapToLong(a -> Math.max(0, a.balance())).sum();
            setSubtitle(total == 0 ? (type == PartyType.CUSTOMER ? "No customer owes you anything"
                    : "You don't owe any supplier")
                    : (type == PartyType.CUSTOMER ? "Customers owe you " : "You owe suppliers ") + Money.format(total));
        }

        void apply() {
            boolean open = filter.selected() == Filter.OPEN;
            list.setItems(open ? all.stream().filter(a -> a.balance() != 0).toList() : all);
            list.filter(search.getText());
            if (list.selected() == null) {
                list.selectFirst();
            }
            Account a = list.selected();
            ledger.setParty(a == null ? null : a.id(), a == null ? "" : a.name());
            rightCards.show(right, a == null ? "none" : "ledger");
        }

        void open(long id) {
            filter.select(Filter.ALL);
            search.setText("");
            load();
            list.items().stream().filter(a -> a.id() == id).findFirst().ifPresent(list::select);
        }
    }

    /** Show one customer's or supplier's account (used from other screens). */
    public void openParty(PartyType type, long id) {
        tabs.setSelectedIndex(type == PartyType.CUSTOMER ? 0 : 1);
        (type == PartyType.CUSTOMER ? customers : suppliers).open(id);
    }

    // ------------------------------------------------------------ expenses

    private static RowList.Row expenseRow(Expense e) {
        String sub = Dates.formatDate(e.expenseDate()) + (Text.blank(e.methodName()) ? "" : "  •  " + e.methodName())
                + (Text.blank(e.note()) ? "" : "  •  " + e.note());
        RowList.Row r = RowList.Row.of(e.category(), sub, Money.format(e.amount())).withIcon("money");
        return e.voided() ? r.withStatus("Cancelled", Ui.Tone.NEUTRAL).dimmed(true) : r;
    }

    private JComponent buildExpenses() {
        expenses.setEmptyState(new EmptyState("money", "No expenses in this period",
                "Record money the shop spends, like rent, electricity or salaries. Reports take it off your profit.", true)
                .action("Add an expense", "plus", this::addExpense));
        expenses.onSelect(e -> cancelExpense.setEnabled(e != null && !e.voided()));
        expenseRange.onChange(this::loadExpenses);
        JButton add = Ui.primary("Add expense", "plus");
        add.addActionListener(e -> addExpense());
        cancelExpense.setEnabled(false);
        cancelExpense.setToolTipText("Cancel the chosen expense. It stays in the list, marked as cancelled.");
        cancelExpense.addActionListener(e -> {
            Expense x = expenses.selected();
            if (x == null || x.voided()) {
                return;
            }
            String reason = Msg.ask(this, "Cancel expense", "Why is the " + x.category() + " expense of "
                    + Money.format(x.amount()) + " being cancelled? It stays in the list, marked as cancelled.", "");
            if (reason != null) {
                safely(() -> {
                    ctx.expenses().voidExpense(x.id(), reason);
                    Toast.ok(this, "Expense cancelled.");
                    loadExpenses();
                });
            }
        });
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(expenseRange, BorderLayout.WEST);
        bar.add(Ui.flowRight(6, cancelExpense, add), BorderLayout.EAST);

        Card listCard = new Card(new BorderLayout());
        listCard.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        listCard.add(expenses, BorderLayout.CENTER);
        byCategory.setFormat(Money::format, v -> Money.symbol() + com.smartbillingpro.ui.LineChart.compact(v));
        JLabel title = new JLabel("Where the money went");
        title.setFont(Theme.bold(15));
        title.setForeground(Theme.TEXT);
        expenseTotal.setFont(Theme.bold(22));
        expenseTotal.setForeground(Theme.TEXT);
        JPanel summary = new JPanel(new BorderLayout(0, 12));
        summary.setOpaque(false);
        summary.add(Ui.vbox(4, title, expenseTotal), BorderLayout.NORTH);
        summary.add(byCategory, BorderLayout.CENTER);
        Card chartCard = new Card(new BorderLayout());
        chartCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        chartCard.add(summary, BorderLayout.CENTER);

        JPanel split = CustomersPage.split(listCard, chartCard);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(bar, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    private void addExpense() {
        if (new ExpenseDialog(this, ctx).open(520)) {
            Toast.ok(this, "Expense recorded.");
            loadExpenses();
        }
    }

    private void loadExpenses() {
        List<Expense> rows = ctx.expenses().list(expenseRange.from(), expenseRange.to());
        expenses.setItems(rows);
        cancelExpense.setEnabled(expenses.selected() != null && !expenses.selected().voided());
        long total = rows.stream().filter(e -> !e.voided()).mapToLong(Expense::amount).sum();
        expenseTotal.setText(Money.format(total));
        List<DashboardService.Point> cats = ctx.dashboard().expensesByCategory(expenseRange.from(), expenseRange.to());
        byCategory.setItems(cats.stream().map(c -> new BarList.Item(c.label(), c.value(), Money.format(c.value()))).toList());
        setSubtitle("Spent " + Money.format(total) + "  •  " + expenseRange.label().toLowerCase());
    }

    @Override
    public void refresh() {
        switch (tabs.getSelectedIndex()) {
            case 0 -> customers.load();
            case 1 -> suppliers.load();
            default -> loadExpenses();
        }
    }
}
