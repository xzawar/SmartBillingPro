package com.smartbillingpro.ui.panels;

import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.TextPreviewDialog;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Dates;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;

/** Generate inventory, sales and profit reports that can be printed or saved. */
public class ReportPanel extends PanelBase {

    private final JTextField fromField = new JTextField(12);
    private final JTextField toField = new JTextField(12);

    public ReportPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Reports", "Inventory, sales and profit insights");

        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(22, 24, 24, 24));

        Ui.styleField(fromField);
        Ui.styleField(toField);
        LocalDate today = LocalDate.now();
        fromField.setText(today.withDayOfMonth(1).toString());
        toField.setText(today.toString());

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        card.add(Ui.title("Inventory Report", 15), c);
        c.gridx = 1;
        RoundedButton inventoryBtn = new RoundedButton("Generate", Theme.PRIMARY, Color.WHITE);
        inventoryBtn.addActionListener(e -> showInventory());
        card.add(inventoryBtn, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        card.add(makeDateRow(), c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy = 2;
        card.add(Ui.title("Sales Report", 15), c);
        c.gridx = 1;
        RoundedButton salesBtn = new RoundedButton("Generate", Theme.PRIMARY, Color.WHITE);
        salesBtn.addActionListener(e -> showSales());
        card.add(salesBtn, c);

        c.gridx = 0;
        c.gridy = 3;
        card.add(Ui.title("Profit Report", 15), c);
        c.gridx = 1;
        RoundedButton profitBtn = new RoundedButton("Generate", Theme.GOLD, Theme.PRIMARY_DARK);
        profitBtn.addActionListener(e -> showProfit());
        card.add(profitBtn, c);

        JPanel wrap = new JPanel(new java.awt.BorderLayout());
        wrap.setOpaque(false);
        wrap.add(card, java.awt.BorderLayout.NORTH);
        setBody(wrap);
    }

    private JPanel makeDateRow() {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        JLabel hint = Ui.formLabel("Date range (YYYY-MM-DD) for sales & profit:");
        row.add(hint);
        row.add(Ui.formLabel("From"));
        fromField.setPreferredSize(new Dimension(120, 32));
        toField.setPreferredSize(new Dimension(120, 32));
        row.add(fromField);
        row.add(Ui.formLabel("To"));
        row.add(toField);
        return row;
    }

    private LocalDate parse(JTextField field) {
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void showInventory() {
        String report = context.getReportService().inventoryReport(settings());
        new TextPreviewDialog(frame, "Inventory Report", report, 76).setVisible(true);
    }

    private void showSales() {
        LocalDate from = parse(fromField);
        LocalDate to = parse(toField);
        if (from == null || to == null) {
            JOptionPane.showMessageDialog(this, "Enter valid dates as YYYY-MM-DD.");
            return;
        }
        String report = context.getReportService().salesReport(settings(), from, to);
        new TextPreviewDialog(frame, "Sales Report", report, 76).setVisible(true);
    }

    private void showProfit() {
        LocalDate from = parse(fromField);
        LocalDate to = parse(toField);
        if (from == null || to == null) {
            JOptionPane.showMessageDialog(this, "Enter valid dates as YYYY-MM-DD.");
            return;
        }
        String report = context.getReportService().profitReport(settings(), from, to);
        new TextPreviewDialog(frame, "Profit Report", report, 76).setVisible(true);
    }

    @Override
    public void refreshData() {
        // Reports are generated on demand; nothing to preload.
    }
}
