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
    private final JLabel opening = Ui.muted(" ");
    private final JLabel closing = Ui.heading(" ");
    private final JButton pay;
    private final JButton adjust = Ui.ghost("Adjust balance", "edit");
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
        table.col("Date", DataTable.Kind.DATETIME, LedgerEntry::createdAt, 140)
                .text("Type", LedgerEntry::typeLabel, 120)
                .text("Reference", e -> Text.orDash(e.reference()), 100)
                .text("Description", e -> Text.orDash(e.description()), 220)
                .money(cust ? "Debit (charged)" : "Debit (paid)", e -> e.debit() == 0 ? null : e.debit())
                .money(cust ? "Credit (received)" : "Credit (billed)", e -> e.credit() == 0 ? null : e.credit())
                .signedMoney("Balance", LedgerEntry::balance);
        table.setEmptyText("No transactions in this period.");
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
        top.add(range, BorderLayout.WEST);
        top.add(ExportBar.create(ctx, this, this::asReport), BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(Ui.vbox(2, opening, closing), BorderLayout.WEST);
        bottom.add(Ui.flowRight(6, adjust, pay), BorderLayout.EAST);
        JPanel tableCard = Ui.card(table, 4);
        add(top, BorderLayout.NORTH);
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
            opening.setText(" ");
            closing.setText(type == PartyType.CUSTOMER ? "Select a customer" : "Select a supplier");
            return;
        }
        String from = Dates.startOf(range.from());
        List<LedgerEntry> rows = ctx.parties().statement(type, partyId, from, Dates.endExclusive(range.to()));
        table.setRows(rows);
        long open = ctx.parties().balanceBefore(type, partyId, from);
        long close = rows.isEmpty() ? open : rows.get(rows.size() - 1).balance();
        opening.setText("Opening balance on " + Dates.formatDate(range.from()) + ": " + Money.format(open));
        boolean cust = type == PartyType.CUSTOMER;
        closing.setForeground(close > 0 ? Theme.BAD : Theme.GOOD);
        closing.setText(close > 0 ? (cust ? "Customer owes: " : "You owe: ") + Money.format(close)
                : close < 0 ? "Advance: " + Money.format(-close) + (cust ? " (customer credit)" : " (paid in advance)")
                : "✓ Settled – nothing outstanding");
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
