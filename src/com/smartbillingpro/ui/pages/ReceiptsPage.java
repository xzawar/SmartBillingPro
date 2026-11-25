package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleReturn;
import com.smartbillingpro.model.SaleStatus;
import com.smartbillingpro.print.ReceiptData;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.EmptyState;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.RowList;
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
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/** Every past sale: find it, see the receipt, print it again or take items back. */
public final class ReceiptsPage extends Page {

    private final JTextField search = Ui.search("Receipt no., customer or phone");
    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.LAST_7);
    private final JComboBox<Object> method = new JComboBox<>();
    private final JComboBox<Object> status = new JComboBox<>(new Object[] {"Any status", "Paid", "Not fully paid",
        "Returned"});
    private final RowList<Sale> list = new RowList<>(ReceiptsPage::row, 60);
    private final JPanel previewHolder = new JPanel(new BorderLayout());
    private final JLabel previewInfo = Ui.muted(" ");
    private final CardLayout rightCards = new CardLayout();
    private final JPanel right = new JPanel(rightCards);
    private final CardLayout pageCards = new CardLayout();
    private final JPanel holder = new JPanel(pageCards);
    private final JButton print = Ui.primary("Print", "print");
    private final JButton pdf = Ui.secondary("Save as PDF", "pdf");
    private final JButton returns = Ui.secondary("Take items back", "return");
    private Sale current;
    private boolean loading;

    public ReceiptsPage(MainFrame frame) {
        super(frame, "Receipts", "Find any past sale, print it again or take items back");
        list.onSelect(this::preview);
        list.onOpen(s -> new ReceiptDialog(this, ctx, ctx.sales().find(s.id())).open(560), false);
        list.setEmptyState(new EmptyState("receipts", "No receipts here",
                "Nothing matches these filters. Try a longer period, like “This month” or “All time”.", true));
        range.onChange(this::refresh);
        method.addActionListener(e -> refresh());
        status.addActionListener(e -> refresh());
        method.setToolTipText("Show one way of paying");
        status.setToolTipText("Show paid, unpaid or returned sales");
        Ui.onChange(search, this::refresh);

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
        returns.setToolTipText("The customer brought something back");
        returns.addActionListener(e -> {
            if (current == null) {
                return;
            }
            if (current.status() == SaleStatus.RETURNED) {
                Msg.info(this, "Already returned", "Everything on this receipt has already been taken back.");
                return;
            }
            if (new ReturnDialog(this, ctx, current).open(700)) {
                Toast.ok(this, "Return saved. Stock and balances are updated.");
                refresh();
            }
        });

        search.setColumns(20);
        JPanel bar = Ui.toolbar(new java.awt.Component[] {search, range}, method, status);

        Card listCard = new Card(new BorderLayout());
        listCard.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        listCard.add(list, BorderLayout.CENTER);

        previewHolder.setBackground(Theme.SURFACE_ALT);
        JScrollPane ps = new JScrollPane(previewHolder);
        ps.setBorder(BorderFactory.createEmptyBorder());
        ps.getVerticalScrollBar().setUnitIncrement(20);
        JPanel previewPanel = new JPanel(new BorderLayout(0, 10));
        previewPanel.setOpaque(false);
        previewPanel.add(previewInfo, BorderLayout.NORTH);
        previewPanel.add(ps, BorderLayout.CENTER);
        previewPanel.add(Ui.flowRight(6, returns, pdf, print), BorderLayout.SOUTH);
        Card previewCard = new Card(new BorderLayout());
        previewCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        previewCard.add(previewPanel, BorderLayout.CENTER);
        Card nothing = new Card(new BorderLayout());
        nothing.add(new EmptyState("receipts", "Choose a receipt", "It will be shown here exactly as it prints.", true),
                BorderLayout.CENTER);
        right.setOpaque(false);
        right.add(previewCard, "preview");
        right.add(nothing, "none");

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(bar, BorderLayout.NORTH);
        JPanel split = CustomersPage.split(listCard, right);
        content.add(split, BorderLayout.CENTER);

        EmptyState none = new EmptyState("receipts", "No sales yet",
                "Every sale you make shows up here, ready to print again or to take items back.")
                .action("Go to Sales", "sales", () -> frame.show(MainFrame.SALES));
        holder.setOpaque(false);
        holder.add(content, "content");
        holder.add(none, "empty");
        setBody(holder);
        preview(null);
    }

    private static RowList.Row row(Sale s) {
        DataTable.Status st = PageStatus.sale(s);
        String who = s.customerName() == null || s.customerName().isBlank() ? "Walk-in customer" : s.customerName();
        return RowList.Row.of(s.receiptNo() + "  •  " + who,
                Dates.formatDateTime(s.createdAt()) + (s.paymentMethodName() == null ? "" : "  •  " + s.paymentMethodName()),
                Money.format(s.grandTotal())).withIcon("receipts").withStatus(st.text(), st.tone())
                .dimmed(s.status() == SaleStatus.RETURNED);
    }

    private void preview(Sale header) {
        current = header == null ? null : ctx.sales().find(header.id());
        previewHolder.removeAll();
        if (current == null) {
            rightCards.show(right, "none");
            return;
        }
        returns.setEnabled(current.status() != SaleStatus.RETURNED);
        JPanel paper = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 12));
        paper.setOpaque(false);
        paper.add(new ReceiptDialog.Preview(ReceiptData.of(current, ctx.settings().get()), ctx.settings().get()));
        previewHolder.add(paper, BorderLayout.NORTH);
        List<SaleReturn> rets = ctx.sales().returnsFor(current.id());
        StringBuilder sb = new StringBuilder("<html><b>" + current.receiptNo() + "</b>  •  "
                + Dates.formatDateTime(current.createdAt()));
        for (SaleReturn r : rets) {
            sb.append("<br>Taken back on ").append(Dates.formatDate(r.createdAt())).append(": ")
                    .append(Money.format(r.totalAmount()))
                    .append(r.reason() == null || r.reason().isBlank() ? "" : " (" + esc(r.reason()) + ")");
        }
        previewInfo.setText(sb + "</html>");
        rightCards.show(right, "preview");
        previewHolder.revalidate();
        previewHolder.repaint();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
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
            method.addItem("Any way of paying");
            List<PaymentMethod> methods = ctx.settings().paymentMethods(false);
            for (PaymentMethod m : methods) {
                method.addItem(m);
                if (sel instanceof PaymentMethod pm && pm.id() == m.id()) {
                    sel = m;
                }
            }
            method.setSelectedItem(sel instanceof PaymentMethod ? sel : "Any way of paying");
            method.setVisible(methods.size() > 1);
            int st = status.getSelectedIndex();
            List<Sale> rows = ctx.sales().list(Dates.startOf(range.from()), Dates.endExclusive(range.to()),
                    search.getText(), sel instanceof PaymentMethod pm ? pm.id() : null, null,
                    st == 3 ? SaleStatus.RETURNED : null, 5000);
            if (st == 1) {
                rows = rows.stream().filter(s -> s.status() != SaleStatus.RETURNED && s.balanceDue() == 0).toList();
            } else if (st == 2) {
                rows = rows.stream().filter(s -> s.status() != SaleStatus.RETURNED && s.balanceDue() > 0).toList();
            }
            list.setItems(rows);
            long total = rows.stream().mapToLong(s -> s.grandTotal() - s.returnedTotal()).sum();
            boolean any = !rows.isEmpty() || ctx.sales().list(null, null, null, null, null, null, 1).size() > 0;
            pageCards.show(holder, any ? "content" : "empty");
            setSubtitle(rows.isEmpty() ? "Find any past sale, print it again or take items back"
                    : rows.size() + (rows.size() == 1 ? " receipt" : " receipts") + "  •  " + Money.format(total)
                    + " after returns  •  " + range.label().toLowerCase());
            if (current != null) {
                long id = current.id();
                rows.stream().filter(s -> s.id() == id).findFirst().ifPresentOrElse(list::select, () -> {
                    list.selectFirst();
                    preview(list.selected());
                });
            } else {
                list.selectFirst();
                preview(list.selected());
            }
        } finally {
            loading = false;
        }
    }

    @Override
    public void focusDefault() {
        search.requestFocusInWindow();
    }

    /** Show every receipt matching a text (e.g. a customer's name), whatever the date. */
    public void searchFor(String text) {
        loading = true;
        range.setPreset(DateRangeBar.Preset.ALL_TIME);
        search.setText(text);
        loading = false;
        refresh();
    }
}
