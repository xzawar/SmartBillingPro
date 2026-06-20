package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Settings;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Edit shop-wide settings such as business details, tax, currency and policies. */
public class SettingsPanel extends PanelBase {

    private final JTextField businessName = new JTextField(20);
    private final JTextField address = new JTextField(20);
    private final JTextField phone = new JTextField(20);
    private final JTextField invoicePrefix = new JTextField(20);
    private final JTextField taxPercent = new JTextField(20);
    private final JTextField currencySymbol = new JTextField(20);
    private final JTextField footerMessage = new JTextField(20);
    private final JTextField lowStockThreshold = new JTextField(20);
    private final JTextField returnPolicyDays = new JTextField(20);
    private final JTextField loyaltyPer100 = new JTextField(20);

    private int row = 0;

    public SettingsPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Settings", "Configure how your shop and receipts behave");

        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(20, 24, 22, 24));

        addRow(card, "Business Name", businessName);
        addRow(card, "Address", address);
        addRow(card, "Phone", phone);
        addRow(card, "Invoice Prefix", invoicePrefix);
        addRow(card, "Default Tax %", taxPercent);
        addRow(card, "Currency Symbol", currencySymbol);
        addRow(card, "Receipt Footer", footerMessage);
        addRow(card, "Low Stock Threshold", lowStockThreshold);
        addRow(card, "Return Policy (days)", returnPolicyDays);
        addRow(card, "Loyalty Points / 100 spent", loyaltyPer100);

        RoundedButton save = new RoundedButton("Save Settings", Theme.PRIMARY, Color.WHITE);
        save.addActionListener(e -> save());
        addAction(save);

        JPanel wrap = new JPanel(new java.awt.BorderLayout());
        wrap.setOpaque(false);
        wrap.add(card, java.awt.BorderLayout.NORTH);
        setBody(wrap);
    }

    private void addRow(JPanel card, String label, JComponent field) {
        if (field instanceof JTextField) {
            Ui.styleField((JTextField) field);
        }
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(8, 0, 0, 16);
        card.add(Ui.formLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 0, 0);
        card.add(field, c);
        row++;
    }

    private int parseInt(JTextField field, int fallback) {
        try {
            String raw = field.getText().trim();
            return raw.isEmpty() ? fallback : Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void save() {
        if (businessName.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Business name is required.");
            return;
        }
        Settings s = new Settings();
        s.setBusinessName(businessName.getText().trim());
        s.setAddress(address.getText().trim());
        s.setPhone(phone.getText().trim());
        s.setInvoicePrefix(invoicePrefix.getText().trim().isEmpty() ? "INV" : invoicePrefix.getText().trim());
        s.setDefaultTaxPercent(Money.parse(taxPercent.getText()));
        s.setCurrencySymbol(currencySymbol.getText().isEmpty() ? "Rs " : currencySymbol.getText());
        s.setFooterMessage(footerMessage.getText().trim());
        s.setLowStockThreshold(parseInt(lowStockThreshold, 5));
        s.setReturnPolicyDays(parseInt(returnPolicyDays, 7));
        s.setLoyaltyPointsPer100(parseInt(loyaltyPer100, 1));

        context.getSettingsService().save(s);
        context.reloadSettings();
        JOptionPane.showMessageDialog(this, "Settings saved.");
    }

    @Override
    public void refreshData() {
        Settings s = settings();
        businessName.setText(s.getBusinessName());
        address.setText(s.getAddress());
        phone.setText(s.getPhone());
        invoicePrefix.setText(s.getInvoicePrefix());
        taxPercent.setText(Money.scale(s.getDefaultTaxPercent()).toPlainString());
        currencySymbol.setText(s.getCurrencySymbol());
        footerMessage.setText(s.getFooterMessage());
        lowStockThreshold.setText(String.valueOf(s.getLowStockThreshold()));
        returnPolicyDays.setText(String.valueOf(s.getReturnPolicyDays()));
        loyaltyPer100.setText(String.valueOf(s.getLoyaltyPointsPer100()));
    }
}
