package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceStatus;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StatusCellRenderer;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.TextPreviewDialog;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Browse past sales, reprint receipts and process voids / refunds. */
public class HistoryPanel extends PanelBase {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Invoice", "Date", "Customer", "Items", "Total", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(model);
    private final JTextField searchField = new JTextField(16);
    private final List<Invoice> rows = new ArrayList<>();

    public HistoryPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Sales History", "Every receipt, void and refund");

        Ui.styleField(searchField);
        searchField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                reload();
            }
        });
        addAction(Ui.formLabel("Search  "));
        addAction(searchField);

        table.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        RoundedPanel tableCard = new RoundedPanel(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tableCard.add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);
        container.add(tableCard, BorderLayout.CENTER);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        RoundedButton view = new RoundedButton("View Receipt", Theme.PRIMARY, Color.WHITE);
        view.addActionListener(e -> viewReceipt());
        bar.add(view);
        if (context.getCurrentUser().getRole().canVoidInvoice()) {
            RoundedButton voidBtn = new RoundedButton("Void", Theme.withAlpha(Theme.DANGER, 30), Theme.DANGER);
            voidBtn.addActionListener(e -> reverse(true));
            bar.add(voidBtn);
        }
        if (context.getCurrentUser().getRole().canProcessReturns()) {
            RoundedButton refundBtn = new RoundedButton("Refund", Theme.withAlpha(Theme.ACCENT, 30), Theme.ACCENT);
            refundBtn.addActionListener(e -> reverse(false));
            bar.add(refundBtn);
        }
        container.add(bar, BorderLayout.SOUTH);
        setBody(container);
    }

    private Invoice selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        return rows.get(row);
    }

    private void viewReceipt() {
        Invoice header = selected();
        if (header == null) {
            JOptionPane.showMessageDialog(this, "Select a sale to view.");
            return;
        }
        Invoice invoice = context.getInvoiceService().load(header.getId());
        String receipt = context.getReportService().receipt(invoice, settings());
        new TextPreviewDialog(frame, "Receipt " + invoice.getInvoiceNumber(), receipt, 32).setVisible(true);
    }

    private void reverse(boolean isVoid) {
        Invoice invoice = selected();
        if (invoice == null) {
            JOptionPane.showMessageDialog(this, "Select a sale first.");
            return;
        }
        if (invoice.getStatus() != InvoiceStatus.COMPLETED) {
            JOptionPane.showMessageDialog(this, "Only completed sales can be " + (isVoid ? "voided" : "refunded") + ".");
            return;
        }
        String action = isVoid ? "Void" : "Refund";
        String reason = JOptionPane.showInputDialog(this, action + " reason for " + invoice.getInvoiceNumber() + ":");
        if (reason == null) {
            return;
        }
        if (isVoid) {
            context.getInvoiceService().voidInvoice(invoice.getId(), reason);
        } else {
            context.getInvoiceService().refundInvoice(invoice.getId(), reason);
        }
        JOptionPane.showMessageDialog(this, action + " complete. Stock has been restored.");
        reload();
    }

    private void reload() {
        List<Invoice> invoices = context.getInvoiceService().all();
        String term = searchField.getText().trim().toLowerCase();
        String symbol = symbol();
        rows.clear();
        model.setRowCount(0);
        for (Invoice invoice : invoices) {
            if (!term.isEmpty()) {
                String number = invoice.getInvoiceNumber() == null ? "" : invoice.getInvoiceNumber().toLowerCase();
                String customer = invoice.getCustomerName() == null ? "" : invoice.getCustomerName().toLowerCase();
                if (!number.contains(term) && !customer.contains(term)) {
                    continue;
                }
            }
            rows.add(invoice);
            model.addRow(new Object[] {invoice.getInvoiceNumber(), invoice.getCreatedAt(),
                    invoice.getCustomerName() == null ? "Walk-in" : invoice.getCustomerName(),
                    invoice.getItemCount(), Money.format(symbol, invoice.getGrandTotal()),
                    invoice.getStatus().getLabel()});
        }
        setSubtitle(rows.size() + " sale(s)");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
