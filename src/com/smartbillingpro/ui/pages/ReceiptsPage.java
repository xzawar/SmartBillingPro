package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleReturn;
import com.smartbillingpro.model.SaleStatus;
import com.smartbillingpro.print.ReceiptData;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.ReceiptDialog;
import com.smartbillingpro.ui.dialogs.ReturnDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

/** Every receipt, searchable, with preview, reprint, PDF and returns. */
public final class ReceiptsPage extends Page {

    private final JTextField search = Ui.search("Receipt no., customer or phone");
    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.LAST_7);
    private final JComboBox<Object> method = new JComboBox<>();
    private final JComboBox<Object> status = new JComboBox<>(new Object[] {"Any status", SaleStatus.COMPLETED,
        SaleStatus.PARTIALLY_RETURNED, SaleStatus.RETURNED});
    private final DataTable<Sale> table = new DataTable<>();
    private final JPanel previewHolder = new JPanel(new BorderLayout());
    private final JLabel previewInfo = Ui.muted(" ");
    private final JButton print = Ui.primary("Print", "print");
    private final JButton pdf = Ui.secondary("Save PDF", "pdf");
    private final JButton returns = Ui.secondary("Return items", "return");
    private Sale current;
    private boolean loading;

    public ReceiptsPage(MainFrame frame) {
        super(frame, "Receipts", "Find, reprint or return any past sale");
        table.text("Receipt", Sale::receiptNo, 110).col("Date", DataTable.Kind.DATETIME, Sale::createdAt, 150)
                .text("Customer", Sale::customerName, 150).text("Method", s -> s.paymentMethodName(), 100)
                .money("Total", Sale::grandTotal).status("Status", PageStatus::sale);
        table.setEmptyText("No receipts match these filters.");
        table.onSelect(this::preview);
        table.onOpen(s -> new ReceiptDialog(this, ctx, ctx.sales().find(s.id())).open(560));
        range.onChange(this::refresh);
        method.addActionListener(e -> refresh());
        status.addActionListener(e -> refresh());
        Ui.onChange(search, this::refresh);
        search.setColumns(20);

        print.addActionListener(e -> {
            if (current != null) {
                safely(() -> {
                    ReceiptDialog.print(ctx, current);
                    Toast.ok(this, "Receipt sent to the printer.");
                });
            }
        });
        pdf.addActionListener(e -> {
            if (current != null) {
                new ReceiptDialog(this, ctx, current).open(560);
            }
        });
        returns.addActionListener(e -> {
            if (current == null) {
                return;
            }
            if (current.status() == SaleStatus.RETURNED) {
                Msg.info(this, "Return", "Everything on this receipt has already been returned.");
                return;
            }
            if (new ReturnDialog(this, ctx, current).open(700)) {
                Toast.ok(this, "Return saved. Stock and balances updated.");
                refresh();
            }
        });

        JPanel filters = Ui.vbox(8, Ui.flowLeft(8, search, method, status), range);

        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.add(filters, BorderLayout.NORTH);
        left.add(Ui.card(table, 4), BorderLayout.CENTER);
        left.setMinimumSize(new Dimension(420, 200));

        previewHolder.setBackground(Theme.BG);
        JScrollPane ps = new JScrollPane(previewHolder);
        ps.setBorder(BorderFactory.createEmptyBorder());
        ps.getVerticalScrollBar().setUnitIncrement(20);
        Card right = new Card(new BorderLayout(0, 10));
        right.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        right.add(previewInfo, BorderLayout.NORTH);
        right.add(ps, BorderLayout.CENTER);
        right.add(Ui.flowRight(6, returns, pdf, print), BorderLayout.SOUTH);
        right.setPreferredSize(new Dimension(400, 200));
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.add(left, BorderLayout.CENTER);
        body.add(right, BorderLayout.EAST);
        setBody(body);
        preview(null);
    }

    private void preview(Sale header) {
        current = header == null ? null : ctx.sales().find(header.id());
        previewHolder.removeAll();
        boolean has = current != null;
        print.setEnabled(has);
        pdf.setEnabled(has);
        returns.setEnabled(has && current.status() != SaleStatus.RETURNED);
        if (!has) {
            previewInfo.setText("Select a receipt to preview it.");
        } else {
            previewHolder.add(new ReceiptDialog.Preview(ReceiptData.of(current, ctx.settings().get()), ctx.settings().get()),
                    BorderLayout.NORTH);
            List<SaleReturn> rets = ctx.sales().returnsFor(current.id());
            StringBuilder sb = new StringBuilder("<html>" + current.receiptNo() + " • " + Dates.formatDateTime(current.createdAt()));
            for (SaleReturn r : rets) {
                sb.append("<br>Return ").append(r.returnNo()).append(" on ").append(Dates.formatDate(r.createdAt()))
                        .append(": ").append(Money.format(r.totalAmount())).append(" (").append(r.reason()).append(")");
            }
            previewInfo.setText(sb + "</html>");
        }
        previewHolder.revalidate();
        previewHolder.repaint();
    }

    @Override
    public void refresh() {
        if (loading) {
            return;
        }
        loading = true;
        try {
            Object sel = method.getSelectedItem();
            method.removeAllItems();
            method.addItem("Any payment method");
            for (PaymentMethod m : ctx.settings().paymentMethods(false)) {
                method.addItem(m);
                if (sel instanceof PaymentMethod pm && pm.id() == m.id()) {
                    sel = m;
                }
            }
            method.setSelectedItem(sel instanceof PaymentMethod ? sel : "Any payment method");
            Object st = status.getSelectedItem();
            List<Sale> rows = ctx.sales().list(Dates.startOf(range.from()), Dates.endExclusive(range.to()),
                    search.getText(), sel instanceof PaymentMethod pm ? pm.id() : null, null,
                    st instanceof SaleStatus ss ? ss : null, 5000);
            table.setRows(rows);
            long total = rows.stream().mapToLong(s -> s.grandTotal() - s.returnedTotal()).sum();
            setSubtitle(rows.size() + " receipt(s)  •  net " + Money.format(total) + "  •  " + range.label());
            if (current == null && !rows.isEmpty()) {
                table.selectFirst();
            } else if (current != null) {
                long id = current.id();
                rows.stream().filter(s -> s.id() == id).findFirst().ifPresentOrElse(s -> {
                    table.select(s);
                    preview(s);
                }, () -> preview(null));
            }
        } finally {
            loading = false;
        }
    }

    @Override
    public void focusDefault() {
        search.requestFocusInWindow();
    }
}
