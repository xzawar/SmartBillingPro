package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceItem;
import com.smartbillingpro.model.Settings;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.panels.DefaultDocListener;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.print.PrinterException;
import java.math.BigDecimal;

/**
 * Shows a finished (or in-progress) invoice on screen with a clean, fully
 * laid-out design and a Print button. This is the single invoice format used
 * everywhere in the app – both right after a sale and when re-opening a past
 * sale from Sales History.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Checkout mode</b> – opened straight after "Generate Invoice". The
 *       cashier can type the amount paid; the change is shown live. The sale is
 *       saved to the database (and stock deducted) ONLY when Print is pressed.
 *       Closing the window without printing throws the bill away.</li>
 *   <li><b>Reprint mode</b> – opened from Sales History for an already-saved
 *       invoice. Everything is read-only and Print simply re-prints it.</li>
 * </ul>
 */
public class InvoicePreviewDialog extends JDialog {

    private final JTextArea printArea = new JTextArea();
    private final Invoice invoice;
    private final Settings settings;
    private final String symbol;
    private final boolean checkout;
    private final AppContext context; // non-null only in checkout mode

    private JTextField paidField;       // editable amount paid (checkout only)
    private JLabel changeValueLabel;    // live change due (checkout only)
    private boolean completed = false;  // true once a checkout sale is saved

    /** Reprint an already-saved invoice (read-only). Used by Sales History. */
    public InvoicePreviewDialog(Window owner, Invoice invoice, Settings settings, String receiptText) {
        this(owner, null, invoice, settings, receiptText);
    }

    /** Checkout preview for a new, not-yet-saved sale. Saves only on Print. */
    public InvoicePreviewDialog(Window owner, AppContext context, Invoice invoice, Settings settings) {
        this(owner, context, invoice, settings, null);
    }

    private InvoicePreviewDialog(Window owner, AppContext context, Invoice invoice, Settings settings,
            String receiptText) {
        super(owner, "Invoice " + invoice.getInvoiceNumber(), ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.checkout = context != null;
        this.invoice = invoice;
        this.settings = settings;
        this.symbol = settings.getCurrencySymbol();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        // ---------- header ----------
        GradientPanel header = new GradientPanel(new BorderLayout());
        header.setColors(Theme.PRIMARY, Theme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        JPanel headLeft = new JPanel();
        headLeft.setOpaque(false);
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        JLabel biz = new JLabel(settings.getBusinessName());
        biz.setFont(Theme.bold(20));
        biz.setForeground(Color.WHITE);
        JLabel addr = new JLabel(settings.getAddress() == null ? "" : settings.getAddress());
        addr.setFont(Theme.regular(12));
        addr.setForeground(new Color(235, 238, 255));
        headLeft.add(biz);
        headLeft.add(addr);
        header.add(headLeft, BorderLayout.WEST);
        JLabel tag = new JLabel("INVOICE");
        tag.setFont(Theme.bold(22));
        tag.setForeground(Color.WHITE);
        header.add(tag, BorderLayout.EAST);

        // ---------- body ----------
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        RoundedPanel meta = new RoundedPanel(new BorderLayout());
        meta.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        JPanel metaGrid = new JPanel(new GridLayout(0, 2, 18, 8));
        metaGrid.setOpaque(false);
        metaGrid.add(metaLine(checkout ? "Invoice # (provisional)" : "Invoice #", invoice.getInvoiceNumber()));
        metaGrid.add(metaLine("Date", invoice.getCreatedAt()));
        metaGrid.add(metaLine("Cashier", invoice.getCashier()));
        metaGrid.add(metaLine("Customer", invoice.getCustomerName()));
        metaGrid.add(metaLine("Phone", invoice.getCustomerPhone()));
        metaGrid.add(metaLine("Address", invoice.getCustomerAddress()));
        meta.add(metaGrid, BorderLayout.CENTER);
        body.add(meta);
        body.add(Box.createVerticalStrut(14));

        DefaultTableModel model = new DefaultTableModel(
                new Object[] {"Item", "Unit Price", "Qty", "Line Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (InvoiceItem item : invoice.getItems()) {
            model.addRow(new Object[] {item.getName(), Money.format(symbol, item.getUnitPrice()),
                    item.getQuantity(), Money.format(symbol, item.getLineTotal())});
        }
        StripedTable table = new StripedTable(model);
        table.setPreferredScrollableViewportSize(new Dimension(0, 180));
        RoundedPanel tableCard = new RoundedPanel(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tableCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableCard.add(Ui.scroll(table), BorderLayout.CENTER);
        body.add(tableCard);
        body.add(Box.createVerticalStrut(14));

        RoundedPanel totals = new RoundedPanel();
        totals.setLayout(new BoxLayout(totals, BoxLayout.Y_AXIS));
        totals.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        totals.setAlignmentX(Component.LEFT_ALIGNMENT);
        totals.add(totalRow("Subtotal", Money.format(symbol, invoice.getSubtotal()), false));
        totals.add(totalRow("Discount", "\u2212" + Money.format(symbol, invoice.getDiscount()), false));
        totals.add(totalRow("Tax (" + Money.scale(invoice.getTaxPercent()).toPlainString() + "%)",
                Money.format(symbol, invoice.getTaxAmount()), false));
        totals.add(totalRow("Grand Total", Money.format(symbol, invoice.getGrandTotal()), true));
        if (checkout) {
            totals.add(amountPaidEditor());
            totals.add(liveChangeRow());
        } else {
            totals.add(totalRow("Amount Paid", Money.format(symbol, invoice.getAmountPaid()), false));
            totals.add(totalRow("Change Due", Money.format(symbol, invoice.getChangeDue()), false));
        }
        body.add(totals);

        String footerMessage = settings.getFooterMessage();
        if (footerMessage != null && !footerMessage.trim().isEmpty()) {
            body.add(Box.createVerticalStrut(14));
            JLabel footer = new JLabel(footerMessage);
            footer.setFont(Theme.regular(12));
            footer.setForeground(Theme.MUTED);
            footer.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(footer);
        }

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // ---------- buttons ----------
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setBackground(Theme.BG);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 16, 14, 16));
        RoundedButton close = new RoundedButton(checkout ? "Cancel" : "Close",
                Theme.withAlpha(Theme.MUTED, 40), Theme.TEXT);
        close.addActionListener(e -> dispose());
        RoundedButton print = new RoundedButton(checkout ? "Print & Save" : "Print", Theme.PRIMARY, Color.WHITE);
        print.addActionListener(e -> onPrint());
        buttons.add(close);
        buttons.add(print);

        if (receiptText != null) {
            printArea.setText(receiptText);
        }
        printArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(580, 720));
        setLocationRelativeTo(owner);
    }

    /** True when a checkout sale was actually saved (Print pressed). */
    public boolean isCompleted() {
        return completed;
    }

    private JPanel metaLine(String label, String value) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.regular(11));
        l.setForeground(Theme.MUTED);
        JLabel v = new JLabel(value == null || value.trim().isEmpty() ? "\u2014" : value);
        v.setFont(Theme.bold(13));
        v.setForeground(Theme.TEXT);
        panel.add(l, BorderLayout.NORTH);
        panel.add(v, BorderLayout.CENTER);
        return panel;
    }

    private JPanel totalRow(String label, String value, boolean strong) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel l = new JLabel(label);
        JLabel v = new JLabel(value);
        l.setFont(strong ? Theme.bold(17) : Theme.regular(14));
        v.setFont(strong ? Theme.bold(17) : Theme.regular(14));
        l.setForeground(strong ? Theme.PRIMARY : Theme.TEXT);
        v.setForeground(strong ? Theme.PRIMARY : Theme.TEXT);
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    /** Editable "Amount Paid" row used in checkout mode. */
    private JPanel amountPaidEditor() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(6, 0, 3, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel l = new JLabel("Amount Paid");
        l.setFont(Theme.regular(14));
        l.setForeground(Theme.TEXT);
        paidField = new JTextField(invoice.getGrandTotal().toPlainString());
        Ui.styleField(paidField);
        paidField.setHorizontalAlignment(JTextField.RIGHT);
        paidField.setPreferredSize(new Dimension(140, 32));
        paidField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                recomputeChange();
            }
        });
        row.add(l, BorderLayout.WEST);
        row.add(paidField, BorderLayout.EAST);
        return row;
    }

    private JPanel liveChangeRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel l = new JLabel("Change Due");
        l.setFont(Theme.regular(14));
        l.setForeground(Theme.TEXT);
        changeValueLabel = new JLabel();
        changeValueLabel.setFont(Theme.bold(14));
        row.add(l, BorderLayout.WEST);
        row.add(changeValueLabel, BorderLayout.EAST);
        recomputeChange();
        return row;
    }

    private void recomputeChange() {
        if (changeValueLabel == null) {
            return;
        }
        BigDecimal paid = Money.parse(paidField.getText());
        BigDecimal change = paid.subtract(invoice.getGrandTotal());
        if (change.signum() < 0) {
            changeValueLabel.setForeground(Theme.DANGER);
            changeValueLabel.setText("Short by " + Money.format(symbol, change.negate()));
        } else {
            changeValueLabel.setForeground(Theme.SUCCESS);
            changeValueLabel.setText(Money.format(symbol, change));
        }
    }

    private void onPrint() {
        if (checkout && !finalizeSale()) {
            return;
        }
        doPrint();
        if (checkout) {
            dispose();
        }
    }

    /**
     * Persist the sale (and deduct stock) the moment the user prints. Returns
     * false if the payment is short or saving failed, leaving the dialog open.
     */
    private boolean finalizeSale() {
        BigDecimal grand = invoice.getGrandTotal();
        BigDecimal paid = Money.parse(paidField.getText());
        if (paid.compareTo(grand) < 0) {
            JOptionPane.showMessageDialog(this,
                    "Amount paid (" + Money.format(symbol, paid) + ") is less than the grand total ("
                            + Money.format(symbol, grand) + ").",
                    "Insufficient Payment", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        invoice.setAmountPaid(Money.scale(paid));
        invoice.setChangeDue(Money.scale(paid.subtract(grand)));
        try {
            // Save the customer only now, so closing the preview leaves nothing
            // behind in the database.
            String phone = invoice.getCustomerPhone();
            if (phone != null && !phone.trim().isEmpty()) {
                Customer existing = context.getCustomerService().findByPhone(phone);
                if (existing == null) {
                    existing = context.getCustomerService().create(
                            new Customer(invoice.getCustomerName(), phone, invoice.getCustomerAddress()));
                }
                invoice.setCustomerId(existing.getId());
            }
            Invoice saved = context.getInvoiceService().completeSale(invoice);
            // Print the final receipt with the real, saved invoice number.
            printArea.setText(context.getReportService().receipt(saved, settings));
            completed = true;
            return true;
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save this sale:\n" + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void doPrint() {
        try {
            printArea.print();
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(this, "Could not print: " + ex.getMessage(),
                    "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
