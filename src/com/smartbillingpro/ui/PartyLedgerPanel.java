package com.smartbillingpro.ui;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.LedgerEntry;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.service.ReportTable;
import com.smartbillingpro.ui.dialogs.AdjustBalanceDialog;
import com.smartbillingpro.ui.dialogs.PaymentDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

/**
 * Ledger statement for one customer or supplier: date filter, opening balance,
 * every entry with a running balance, closing balance, payments and exports.
 */
public final class PartyLedgerPanel extends JPanel {

    private final AppContext ctx;
    private final PartyType type;
    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.THIS_YEAR);
    private final DataTable<LedgerEntry> table = new DataTable<>();
    private final LineChart chart = new LineChart("No changes in this period");
    private final JLabel opening = Ui.muted(" ");
    private final JLabel closing = Ui.heading(" ");
    private final JButton pay;
    private final JButton adjust = Ui.ghost("Correct balance", "edit");
    private Long partyId;
    private String partyName = "";
    private Runnable onChange = () -> { };

    public PartyLedgerPanel(AppContext ctx, PartyType type) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        this.type = type;
        setOpaque(false);
        boolean cust = type == PartyType.CUSTOMER;
        pay = Ui.primary(cust ? "Receive payment" : "Pay supplier", "money");
        table.col("Date", DataTable.Kind.DATE, LedgerEntry::createdAt, 105)
                .text("What happened", e -> Text.blank(e.description()) ? e.typeLabel()
                        + (Text.blank(e.reference()) ? "" : " " + e.reference()) : e.description(), 230)
                .money(cust ? "They bought" : "You paid", e -> e.debit() == 0 ? null : e.debit())
                .money(cust ? "They paid" : "You bought", e -> e.credit() == 0 ? null : e.credit())
                .signedMoney(cust ? "They owe" : "You owe", LedgerEntry::balance);
        table.setEmptyText("Nothing happened on this account in this period.");
        chart.setPreferredSize(new java.awt.Dimension(300, 150));
        chart.setMinimumSize(new java.awt.Dimension(200, 120));
        range.onChange(this::reload);
        pay.addActionListener(e -> {
            if (partyId != null && new PaymentDialog(this, ctx, type, partyId, partyName).open(520)) {
                Toast.ok(this, "Payment recorded.");
                reload();
                onChange.run();
            }
        });
        adjust.addActionListener(e -> {
            if (partyId != null && new AdjustBalanceDialog(this, ctx, type, partyId, partyName).open(520)) {
                Toast.ok(this, "Adjustment recorded.");
                reload();
                onChange.run();
            }
        });
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(range, BorderLayout.CENTER);
        top.add(ExportBar.create(ctx, this, this::asReport), BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(Ui.vbox(2, opening, closing), BorderLayout.WEST);
        bottom.add(Ui.flowRight(6, adjust, pay), BorderLayout.EAST);
        JPanel tableCard = Ui.card(table, 4);
        JLabel chartTitle = Ui.muted(cust ? "What they owed you over time" : "What you owed over time");
        JPanel chartBox = new JPanel(new BorderLayout(0, 2));
        chartBox.setOpaque(false);
        chartBox.add(chartTitle, BorderLayout.NORTH);
        chartBox.add(chart, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.setOpaque(false);
        north.add(top, BorderLayout.NORTH);
        north.add(chartBox, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(tableCard, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder());
        setParty(null, "");
    }

    public void onChange(Runnable r) {
        this.onChange = r;
    }

    public void setParty(Long id, String name) {
        this.partyId = id;
        this.partyName = name == null ? "" : name;
        pay.setEnabled(id != null);
        adjust.setEnabled(id != null);
        reload();
    }

    public void reload() {
        if (partyId == null) {
            table.setRows(List.of());
            chart.setData(List.of(), "", null);
            opening.setText(" ");
            closing.setText(type == PartyType.CUSTOMER ? "Choose a customer" : "Choose a supplier");
            return;
        }
        String from = Dates.startOf(range.from());
        List<LedgerEntry> rows = ctx.parties().statement(type, partyId, from, Dates.endExclusive(range.to()));
        table.setRows(rows);
        long open = ctx.parties().balanceBefore(type, partyId, from);
        long close = rows.isEmpty() ? open : rows.get(rows.size() - 1).balance();
        boolean cust = type == PartyType.CUSTOMER;
        opening.setText((cust ? "They owed " : "You owed ") + Money.format(open) + " on " + Dates.formatDate(range.from()));
        closing.setForeground(close > 0 ? Theme.WARN : Theme.GOOD);
        closing.setText(close > 0 ? (cust ? "They owe you " : "You owe ") + Money.format(close)
                : close < 0 ? (cust ? "They paid " + Money.format(-close) + " ahead" : "You paid " + Money.format(-close)
                + " ahead") : "\u2713 All settled \u2013 nothing owed");
        java.util.LinkedHashMap<String, Long> perDay = new java.util.LinkedHashMap<>();
        if (open != 0 || rows.isEmpty()) {
            perDay.put(Dates.formatDate(range.from()), open);
        }
        for (LedgerEntry e : rows) {
            perDay.put(Dates.formatDate(e.createdAt()), e.balance());
        }
        List<LineChart.Point> pts = new java.util.ArrayList<>();
        perDay.forEach((d, v) -> pts.add(new LineChart.Point(d, v, 0)));
        boolean flat = pts.stream().allMatch(x -> x.a() == 0);
        chart.setData(flat ? List.of() : pts, cust ? "They owed" : "You owed", null);
    }

    /** The statement as a report for PDF / CSV / printing. */
    public ReportTable asReport() {
        if (partyId == null) {
            Msg.info(this, "Ledger", "Select an account first.");
            return null;
        }
        boolean cust = type == PartyType.CUSTOMER;
        ReportTable r = new ReportTable((cust ? "Customer" : "Supplier") + " statement – " + partyName)
                .col("Date", ReportTable.Type.DATETIME, 3).col("Type", ReportTable.Type.TEXT, 2)
                .col("Reference", ReportTable.Type.TEXT, 2).col("Description", ReportTable.Type.TEXT, 4)
                .col("Debit", ReportTable.Type.MONEY).col("Credit", ReportTable.Type.MONEY)
                .col("Balance", ReportTable.Type.MONEY);
        long open = ctx.parties().balanceBefore(type, partyId, Dates.startOf(range.from()));
        r.row(Dates.startOf(range.from()), "Opening balance", "", "Balance brought forward", null, null, open);
        long debit = 0;
        long credit = 0;
        long bal = open;
        for (LedgerEntry e : table.rows()) {
            r.row(e.createdAt(), e.typeLabel(), Text.trim(e.reference()), Text.trim(e.description()),
                    e.debit() == 0 ? null : e.debit(), e.credit() == 0 ? null : e.credit(), e.balance());
            debit += e.debit();
            credit += e.credit();
            bal = e.balance();
        }
        r.setTotals("Closing balance", null, null, null, debit, credit, bal);
        r.setSubtitle(ctx.settings().get().storeName() + "  •  " + Dates.formatDate(range.from()) + " to "
                + Dates.formatDate(range.to()));
        r.summary(cust ? "Amount due from customer" : "Amount due to supplier", Money.format(Math.max(0, bal)));
        return r;
    }
}
