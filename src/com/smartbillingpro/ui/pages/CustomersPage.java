package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.service.PartyService;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PartyLedgerPanel;
import com.smartbillingpro.ui.StatCard;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.CustomerDialog;
import com.smartbillingpro.ui.dialogs.ReceiptDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;

/** Customer list with a profile: totals, receipts, ledger and payments. */
public final class CustomersPage extends Page {

    private final JTextField search = Ui.search("Search name or phone");
    private final JCheckBox dueOnly = new JCheckBox("Only customers who owe");
    private final DataTable<Customer> list = new DataTable<>();
    private final JLabel name = Ui.heading("Select a customer");
    private final JLabel contact = Ui.muted(" ");
    private final StatCard purchases = new StatCard("Total purchases", "sales");
    private final StatCard paid = new StatCard("Total paid", "money");
    private final StatCard balance = new StatCard("Balance", "ledgers");
    private final DataTable<Sale> receipts = new DataTable<>();
    private final PartyLedgerPanel ledger;
    private final JButton edit = Ui.secondary("Edit", "edit");
    private Customer current;

    public CustomersPage(MainFrame frame) {
        super(frame, "Customers", "Customer accounts, purchases and balances");
        JButton add = Ui.primary("Add customer", "plus");
        add.addActionListener(e -> {
            CustomerDialog d = new CustomerDialog(this, ctx, null);
            if (d.open(560)) {
                Toast.ok(this, "Customer added.");
                refresh();
                list.rows().stream().filter(c -> c.id() == d.savedId()).findFirst().ifPresent(list::select);
            }
        });
        addAction(add);
        ledger = new PartyLedgerPanel(ctx, PartyType.CUSTOMER);
        ledger.onChange(this::refresh);

        list.text("Customer", Customer::name, 170).text("Phone", c -> Text.orDash(c.phone()), 110)
                .signedMoney("Balance", Customer::balance).status("Status", c -> c.balance() > 0
                        ? new DataTable.Status("Owes", Ui.Tone.BAD)
                        : c.balance() < 0 ? new DataTable.Status("Advance", Ui.Tone.INFO)
                        : new DataTable.Status("Clear", Ui.Tone.GOOD));
        list.setEmptyText("No customers yet. Walk-in sales need no customer.");
        list.onSelect(this::show);
        Ui.onChange(search, () -> list.filter(search.getText()));
        dueOnly.setOpaque(false);
        dueOnly.addActionListener(e -> refresh());
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.add(Ui.vbox(6, search, dueOnly), BorderLayout.NORTH);
        left.add(Ui.card(list, 4), BorderLayout.CENTER);
        left.setMinimumSize(new Dimension(300, 200));

        receipts.text("Receipt", Sale::receiptNo, 110).col("Date", DataTable.Kind.DATETIME, Sale::createdAt, 150)
                .money("Total", Sale::grandTotal).money("Paid", Sale::amountPaid).money("Due", s -> s.balanceDue())
                .status("Status", PageStatus::sale);
        receipts.setEmptyText("No purchases yet.");
        receipts.onOpen(s -> new ReceiptDialog(this, ctx, ctx.sales().find(s.id())).open(560));

        edit.addActionListener(e -> {
            if (current != null && new CustomerDialog(this, ctx, current).open(560)) {
                Toast.ok(this, "Customer saved.");
                refresh();
            }
        });
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.vbox(2, name, contact), BorderLayout.WEST);
        head.add(Ui.flowRight(6, edit), BorderLayout.EAST);
        JPanel stats = new JPanel(new GridLayout(1, 3, 10, 0));
        stats.setOpaque(false);
        stats.add(purchases);
        stats.add(paid);
        stats.add(balance);
        JTabbedPane tabs = new JTabbedPane();
        JPanel receiptsPanel = new JPanel(new BorderLayout(0, 6));
        receiptsPanel.setOpaque(false);
        receiptsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        receiptsPanel.add(Ui.muted("Double-click a receipt to view or reprint it."), BorderLayout.NORTH);
        receiptsPanel.add(Ui.card(receipts, 4), BorderLayout.CENTER);
        tabs.addTab("Receipts", receiptsPanel);
        JPanel ledgerWrap = new JPanel(new BorderLayout());
        ledgerWrap.setOpaque(false);
        ledgerWrap.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        ledgerWrap.add(ledger, BorderLayout.CENTER);
        tabs.addTab("Ledger & payments", ledgerWrap);
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);
        right.add(Ui.vbox(12, head, stats), BorderLayout.NORTH);
        right.add(tabs, BorderLayout.CENTER);
        Card rightCard = new Card(new BorderLayout());
        rightCard.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        rightCard.add(right, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightCard);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setDividerLocation(400);
        split.setResizeWeight(0.3);
        setBody(split);
        show(null);
    }

    private void show(Customer c) {
        current = c;
        edit.setEnabled(c != null);
        if (c == null) {
            name.setText("Select a customer");
            contact.setText("Pick a customer on the left to see their purchases and account.");
            purchases.set("—", " ");
            paid.set("—", " ");
            balance.set("—", " ");
            balance.status(null, null);
            receipts.setRows(java.util.List.of());
            ledger.setParty(null, "");
            return;
        }
        PartyService.CustomerStats s = ctx.parties().customerStats(c.id());
        name.setText(c.name() + (c.active() ? "" : "  (inactive)"));
        contact.setText(String.join("   •   ", java.util.stream.Stream.of(c.phone(), c.address(), c.notes())
                .filter(x -> !Text.blank(x)).toList()));
        purchases.set(Money.format(s.totalPurchases()), s.salesCount() + " receipt(s)"
                + (s.lastSale() == null ? "" : ", last " + Dates.formatDate(s.lastSale())));
        paid.set(Money.format(s.totalPaid()), "Payments received");
        balance.set(Money.format(Math.abs(s.balance())), s.balance() > 0 ? "Outstanding" : s.balance() < 0 ? "Advance (credit)" : "Nothing due");
        balance.status(s.balance() > 0 ? "Owes" : "Clear", s.balance() > 0 ? Ui.Tone.BAD : Ui.Tone.GOOD);
        receipts.setRows(ctx.sales().list(null, null, null, null, c.id(), null, 2000));
        ledger.setParty(c.id(), c.name());
    }

    @Override
    public void refresh() {
        Long keep = current == null ? null : current.id();
        java.util.List<Customer> all = ctx.parties().customers(true);
        if (dueOnly.isSelected()) {
            all = all.stream().filter(c -> c.balance() > 0).toList();
        }
        list.setRows(all);
        list.filter(search.getText());
        long owed = all.stream().mapToLong(c -> Math.max(0, c.balance())).sum();
        setSubtitle(all.size() + " customer(s)  •  total owed to you " + Money.format(owed));
        if (keep == null && !all.isEmpty() && list.selected() == null) {
            list.selectFirst();
        } else if (keep != null) {
            all.stream().filter(c -> c.id() == keep).findFirst().ifPresentOrElse(c -> {
                list.select(c);
                show(c);
            }, () -> show(null));
        }
    }

    @Override
    public void focusDefault() {
        search.requestFocusInWindow();
    }

    /** Select a customer programmatically (from other screens). */
    public void open(long customerId) {
        refresh();
        list.rows().stream().filter(c -> c.id() == customerId).findFirst().ifPresent(list::select);
    }
}
