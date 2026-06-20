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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Edit shop-wide settings such as business details, tax, currency and policies. */
public class SettingsPanel extends PanelBase {

    private static final Color LOCKED_BG = new Color(0xEE, 0xF1, 0xF6);

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
    private final JTextField[] allFields = {
            businessName, address, phone, invoicePrefix, taxPercent,
            currencySymbol, footerMessage, lowStockThreshold, returnPolicyDays, loyaltyPer100
    };

    private final JLabel lockNotice = new JLabel(" ");
    private RoundedButton saveButton;
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

        saveButton = new RoundedButton("Save Settings", Theme.PRIMARY, Color.WHITE);
        saveButton.addActionListener(e -> save());
        addAction(saveButton);

        lockNotice.setFont(Theme.bold(12));
        lockNotice.setForeground(Theme.WARNING);

        JPanel wrap = new JPanel(new java.awt.BorderLayout());
        wrap.setOpaque(false);
        wrap.add(lockNotice, java.awt.BorderLayout.NORTH);
        JPanel cardHolder = new JPanel(new java.awt.BorderLayout());
        cardHolder.setOpaque(false);
        cardHolder.add(card, java.awt.BorderLayout.NORTH);
        wrap.add(cardHolder, java.awt.BorderLayout.CENTER);
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
        // A brand-new store stays locked until an administrator activates it from
        // the web console; until then settings cannot be changed.
        if (!storeActive()) {
            JOptionPane.showMessageDialog(this,
                    "This store is waiting for administrator activation.\n"
                            + "You can change settings once it has been activated from the web.",
                    "Settings locked", JOptionPane.WARNING_MESSAGE);
            return;
        }
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

    /**
     * Whether this device's current store is active (activated by an admin).
     * Admin/no specific store is always treated as active.
     */
    private boolean storeActive() {
        try {
            int storeId = context.getCurrentStoreId();
            if (storeId <= 0) {
                return true;
            }
            com.smartbillingpro.model.Store store = context.getStoreService().findById(storeId);
            return store == null || store.isActive();
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /** Greys out (locks) every field and the Save button while the store is pending. */
    private void setEditable(boolean editable) {
        for (JTextField field : allFields) {
            field.setEnabled(editable);
            field.setBackground(editable ? Color.WHITE : LOCKED_BG);
        }
        if (saveButton != null) {
            saveButton.setEnabled(editable);
        }
        if (editable) {
            lockNotice.setText(" ");
        } else {
            lockNotice.setText("\u23F3  Settings are locked until an administrator activates this store from the web.");
        }
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
        // Lock everything if the store is still waiting for activation.
        setEditable(storeActive());
    }
}
