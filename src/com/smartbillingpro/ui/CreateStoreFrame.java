package com.smartbillingpro.ui;

import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.CloudAccess;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.RememberMe;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

/**
 * A full-screen, app-styled wizard for creating a brand-new store from the
 * desktop app (instead of a small pop-up dialog). It mirrors the look of the
 * main app: a gradient top bar, a dark step sidebar and a rounded card.
 *
 * <p>Flow:
 * <ol>
 *   <li>The user fills in the store details on this screen.</li>
 *   <li>Pressing <b>Continue</b> opens a pop-up to create the owner account.</li>
 *   <li>The owner account is created (defaulting to the OWNER role) and the
 *       user is signed straight into their dashboard.</li>
 *   <li>The store starts <em>not activated</em>: the dashboard opens but
 *       settings stay locked until an administrator activates the store from
 *       the web console.</li>
 * </ol>
 */
public class CreateStoreFrame extends JFrame {

    private final AppContext context;
    private final LoginFrame.Mode mode;
    private final JTextField storeName = new JTextField(22);
    private final JTextField storePhone = new JTextField(22);
    private final JTextField storeAddress = new JTextField(22);
    private final JLabel errorLabel = new JLabel(" ");

    public CreateStoreFrame(AppContext context, LoginFrame.Mode mode) {
        this.context = context;
        this.mode = mode == null ? LoginFrame.Mode.ALL : mode;
        setTitle("Smart Billing Pro - Create a new store");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1040, 660);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildBody(), BorderLayout.CENTER);
        setContentPane(root);
    }

    private JComponent buildTopBar() {
        GradientPanel bar = new GradientPanel(new BorderLayout());
        bar.setColors(Theme.PRIMARY_DARK, Theme.ACCENT);
        bar.setPreferredSize(new Dimension(0, 64));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 22));

        JLabel brand = new JLabel("\uD83D\uDED2  Create a new store");
        brand.setFont(Theme.bold(20));
        brand.setForeground(Color.WHITE);
        bar.add(brand, BorderLayout.WEST);

        RoundedButton back = new RoundedButton("Back to sign in",
                Theme.withAlpha(Color.WHITE, 40), Color.WHITE);
        back.addActionListener(e -> backToLogin());
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.add(back);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(232, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(28, 26, 16, 16));

        sidebar.add(stepLabel("1.  Store details", true));
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(stepLabel("2.  Owner account", false));
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(stepLabel("3.  Start using your shop", false));
        sidebar.add(Box.createVerticalGlue());

        JLabel note = new JLabel("<html>New stores stay locked<br>until an administrator<br>activates them.</html>");
        note.setFont(Theme.regular(11));
        note.setForeground(Theme.SIDEBAR_TEXT);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(note);
        return sidebar;
    }

    private JLabel stepLabel(String text, boolean active) {
        JLabel l = new JLabel(text);
        l.setFont(active ? Theme.bold(14) : Theme.regular(14));
        l.setForeground(active ? Color.WHITE : Theme.SIDEBAR_TEXT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Theme.BG);

        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        card.setArc(22);
        card.setBorder(BorderFactory.createEmptyBorder(32, 40, 32, 40));
        card.setPreferredSize(new Dimension(470, 430));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        JLabel heading = new JLabel("Store details");
        heading.setFont(Theme.bold(22));
        heading.setForeground(Theme.TEXT);
        card.add(heading, c);

        c.gridy++;
        JLabel hint = new JLabel("Tell us about your shop to get started.");
        hint.setFont(Theme.regular(13));
        hint.setForeground(Theme.MUTED);
        card.add(hint, c);

        addField(card, c, "Store name", storeName);
        addField(card, c, "Phone", storePhone);
        addField(card, c, "Address", storeAddress);

        c.gridy++;
        c.insets = new java.awt.Insets(10, 0, 0, 0);
        errorLabel.setFont(Theme.regular(12));
        errorLabel.setForeground(Theme.DANGER);
        card.add(errorLabel, c);

        c.gridy++;
        c.insets = new java.awt.Insets(16, 0, 0, 0);
        RoundedButton cont = new RoundedButton("Continue", Theme.PRIMARY, Color.WHITE);
        cont.addActionListener(e -> openOwnerDialog());
        card.add(cont, c);

        wrapper.add(card);
        return wrapper;
    }

    private void addField(RoundedPanel card, GridBagConstraints c, String label, JTextField field) {
        c.gridy++;
        c.insets = new java.awt.Insets(18, 0, 4, 0);
        JLabel l = new JLabel(label);
        l.setFont(Theme.bold(12));
        l.setForeground(Theme.MUTED);
        card.add(l, c);
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        field.setFont(Theme.regular(14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        card.add(field, c);
    }

    /** Step 2: a pop-up to create the owner account, then register everything. */
    private void openOwnerDialog() {
        if (storeName.getText().trim().isEmpty()) {
            showError("Store name is required.");
            return;
        }
        errorLabel.setText(" ");

        JTextField ownerName = new JTextField(20);
        JTextField ownerUser = new JTextField(20);
        JPasswordField ownerPass = new JPasswordField(20);
        JPasswordField ownerConfirm = new JPasswordField(20);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.add(new JLabel("Your full name (store owner)"));
        form.add(ownerName);
        form.add(new JLabel("Choose a username"));
        form.add(ownerUser);
        form.add(new JLabel("Choose a password"));
        form.add(ownerPass);
        form.add(new JLabel("Confirm password"));
        form.add(ownerConfirm);

        int ok = JOptionPane.showConfirmDialog(this, form,
                "Create the store owner account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        String user = ownerUser.getText().trim();
        String pass = new String(ownerPass.getPassword());
        String confirm = new String(ownerConfirm.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            showError("The owner username and password are required.");
            return;
        }
        if (!pass.equals(confirm)) {
            showError("The two passwords do not match.");
            return;
        }
        submit(storeName.getText().trim(), storePhone.getText().trim(),
                storeAddress.getText().trim(), user, pass, ownerName.getText().trim());
    }

    /** Registers the store + owner in the cloud and opens the owner dashboard. */
    private void submit(String name, String phone, String address,
            String ownerUser, String ownerPass, String ownerName) {
        errorLabel.setForeground(Theme.MUTED);
        errorLabel.setText("Creating your store\u2026");
        new Thread(() -> {
            User user = null;
            String err = null;
            try {
                user = CloudAccess.registerStore(context, name, phone, address,
                        ownerUser, ownerPass, ownerName);
            } catch (RuntimeException ex) {
                err = ex.getMessage();
            }
            final User result = user;
            final String error = err;
            SwingUtilities.invokeLater(() -> {
                if (result == null) {
                    showError(error != null ? "Could not create store: " + error
                            : "Store created online, but local sign-in failed.");
                    return;
                }
                // Do NOT remember this freshly-created owner across restarts:
                // the store is still locked/pending, and auto-logging in as the
                // owner on every launch would stop the administrator from ever
                // reaching the admin panel. We sign them in for THIS session
                // only; the next launch shows the sign-in screen again.
                RememberMe.clear();
                context.setCurrentUser(result);
                context.getAuditService().log("REGISTER_STORE", "Created store " + name);
                JOptionPane.showMessageDialog(this,
                        "Your store has been created and you are now signed in.\n"
                                + "It is waiting for administrator activation - you can look\n"
                                + "around, but settings stay locked until it is activated from\n"
                                + "the web console.",
                        "Store created", JOptionPane.INFORMATION_MESSAGE);
                dispose();
                LoginFrame.openFrameFor(context, result);
            });
        }, "register-store").start();
    }

    private void showError(String message) {
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setText(message);
    }

    private void backToLogin() {
        dispose();
        SwingUtilities.invokeLater(() -> new LoginFrame(context, mode).setVisible(true));
    }
}
