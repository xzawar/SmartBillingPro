package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.Expense;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PartyLedgerPanel;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Customer and supplier ledgers in one place, plus the shop's expenses. */
public final class LedgersPage extends Page {

    /** A party row in the account list. */
    private record Account(long id, String name, String phone, long balance) {
    }

    private final JTabbedPane tabs = new JTabbedPane();
    private final AccountTab customers;
    private final AccountTab suppliers;
    private final DateRangeBar expenseRange = new DateRangeBar(DateRangeBar.Preset.THIS_MONTH);
    private final DataTable<Expense> expenses = new DataTable<>();

    public LedgersPage(MainFrame frame) {
        super(frame, "Ledgers", "Every amount owed, paid and spent – with running balances");
        customers = new AccountTab(PartyType.CUSTOMER);
        suppliers = new AccountTab(PartyType.SUPPLIER);
        tabs.addTab("Customer ledger", customers.panel);
        tabs.addTab("Supplier ledger", suppliers.panel);
        tabs.addTab("Expenses", buildExpenses());
        tabs.addChangeListener(e -> refresh());
        setBody(tabs);
    }

    private final class AccountTab {
        final PartyType type;
        final JTextField search = Ui.search("Search");
        final JCheckBox outstanding = new JCheckBox("Only with a balance");
        final DataTable<Account> list = new DataTable<>();
        final PartyLedgerPanel ledger;
        final JComponent panel;

        AccountTab(PartyType type) {
            this.type = type;
            boolean cust = type == PartyType.CUSTOMER;
            ledger = new PartyLedgerPanel(ctx, type);
            ledger.onChange(this::load);
            list.text(cust ? "Customer" : "Supplier", Account::name, 170).text("Phone", a -> Text.orDash(a.phone()), 100)
                    .signedMoney("Balance", Account::balance);
            list.setEmptyText(cust ? "No customers yet." : "No suppliers yet.");
            list.onSelect(a -> ledger.setParty(a == null ? null : a.id(), a == null ? "" : a.name()));
            Ui.onChange(search, () -> list.filter(search.getText()));
            outstanding.setOpaque(false);
            outstanding.addActionListener(e -> load());
            JPanel left = new JPanel(new BorderLayout(0, 8));
            left.setOpaque(false);
            left.add(Ui.vbox(6, search, outstanding), BorderLayout.NORTH);
            left.add(Ui.card(list, 4), BorderLayout.CENTER);
            left.setMinimumSize(new Dimension(260, 200));
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, ledger);
            split.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
            split.setDividerLocation(340);
            split.setResizeWeight(0.25);
            panel = split;
        }

        void load() {
            Account keep = list.selected();
            List<Account> rows = type == PartyType.CUSTOMER
                    ? ctx.parties().customers(true).stream().map(c -> new Account(c.id(), c.name(), c.phone(), c.balance())).toList()
                    : ctx.parties().suppliers(true).stream().map(s -> new Account(s.id(), s.name(), s.phone(), s.balance())).toList();
            if (outstanding.isSelected()) {
                rows = rows.stream().filter(a -> a.balance() != 0).toList();
            }
            list.setRows(rows);
            list.filter(search.getText());
            if (keep != null) {
                rows.stream().filter(a -> a.id() == keep.id()).findFirst().ifPresent(list::select);
            }
            if (list.selected() == null) {
                list.selectFirst();
            }
            ledger.reload();
            long total = rows.stream().mapToLong(a -> Math.max(0, a.balance())).sum();
            setSubtitle((type == PartyType.CUSTOMER ? "Customers owe you " : "You owe suppliers ") + Money.format(total));
        }
    }

    // ------------------------------------------------------------ expenses

    private JComponent buildExpenses() {
        expenses.text("No.", Expense::expenseNo, 100).col("Date", DataTable.Kind.DATE, Expense::expenseDate, 100)
                .text("Category", Expense::category, 140).text("Paid by", e -> Text.orDash(e.methodName()), 110)
                .text("Note", e -> Text.orDash(e.note()), 200).money("Amount", Expense::amount)
                .status("Status", e -> e.voided() ? new DataTable.Status("Voided", Ui.Tone.NEUTRAL)
                        : new DataTable.Status("Recorded", Ui.Tone.GOOD));
        expenses.setEmptyText("No expenses recorded in this period.");
        expenseRange.onChange(this::loadExpenses);
        JButton add = Ui.primary("Add expense", "plus");
        add.addActionListener(e -> {
            if (new ExpenseDialog().open(520)) {
                Toast.ok(this, "Expense recorded.");
                loadExpenses();
            }
        });
        JButton voidBtn = Ui.ghost("Void", "close");
        voidBtn.addActionListener(e -> {
            Expense x = expenses.selected();
            if (x == null || x.voided()) {
                return;
            }
            String reason = Msg.ask(this, "Void expense", "Why is " + x.expenseNo() + " (" + Money.format(x.amount())
                    + ") being voided? It stays in the list marked as voided.", "");
            if (reason != null) {
                safely(() -> {
                    ctx.expenses().voidExpense(x.id(), reason);
                    loadExpenses();
                });
            }
        });
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(expenseRange, BorderLayout.WEST);
        bar.add(Ui.flowRight(6, voidBtn, add), BorderLayout.EAST);
        return InventoryPage.panel(bar, expenses);
    }

    private void loadExpenses() {
        List<Expense> rows = ctx.expenses().list(expenseRange.from(), expenseRange.to());
        expenses.setRows(rows);
        long total = rows.stream().filter(e -> !e.voided()).mapToLong(Expense::amount).sum();
        setSubtitle("Expenses " + expenseRange.label().toLowerCase() + ": " + Money.format(total));
    }

    private final class ExpenseDialog extends FormDialog {
        private final JSpinner date = new JSpinner(new SpinnerDateModel(new Date(), null, new Date(), Calendar.DAY_OF_MONTH));
        private final JComboBox<String> category = new JComboBox<>();
        private final JTextField amount = Ui.money("0.00");
        private final JComboBox<PaymentMethod> method = new JComboBox<>();
        private final JTextField note = Ui.field("optional");

        ExpenseDialog() {
            super(LedgersPage.this, "Add expense", "Save expense");
            date.setEditor(new JSpinner.DateEditor(date, Dates.displayPattern()));
            category.setEditable(true);
            for (String c : ctx.expenses().categories()) {
                category.addItem(c);
            }
            for (PaymentMethod m : ctx.settings().paymentMethods(true)) {
                method.addItem(m);
            }
            setBody(Ui.vbox(12, Ui.grid(2, 12, 0, Ui.row("Date", date), Ui.row("Category *", category)),
                    Ui.grid(2, 12, 0, Ui.row("Amount *", amount), Ui.row("Paid by", method)), Ui.row("Note", note)));
        }

        @Override
        protected boolean onSave() {
            long a = Ui.moneyValue(amount, "the amount", true);
            LocalDate d = ((Date) date.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            Object c = category.getEditor().getItem();
            PaymentMethod m = (PaymentMethod) method.getSelectedItem();
            ctx.expenses().add(d, c == null ? "" : c.toString(), a, m == null ? -1 : m.id(), note.getText());
            return true;
        }
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
