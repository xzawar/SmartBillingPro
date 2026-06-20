package com.smartbillingpro.ui;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/** The sign-in window shown before the main application opens. */
public class LoginFrame extends JFrame {

    private final AppContext context;
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel errorLabel = new JLabel(" ");

    public LoginFrame(AppContext context) {
        this.context = context;
        setTitle("Smart Billing Pro - Sign In");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(880, 540);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(null);
        setContentPane(buildContent());
        getRootPane().setDefaultButton(null);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildBrandPanel(), BorderLayout.WEST);
        root.add(buildFormPanel(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildBrandPanel() {
        GradientPanel brand = new GradientPanel(new GridBagLayout());
        brand.setColors(Theme.PRIMARY, Theme.ACCENT);
        brand.setPreferredSize(new Dimension(360, 0));

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("\uD83D\uDED2");
        logo.setFont(Theme.regular(54));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Smart Billing Pro");
        title.setFont(Theme.bold(26));
        title.setForeground(java.awt.Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Point of Sale & Inventory");
        subtitle.setFont(Theme.regular(14));
        subtitle.setForeground(Theme.GOLD_LIGHT);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(logo);
        inner.add(javax.swing.Box.createVerticalStrut(14));
        inner.add(title);
        inner.add(javax.swing.Box.createVerticalStrut(6));
        inner.add(subtitle);
        brand.add(inner);
        return brand;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Theme.BG);

        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        card.setArc(22);
        card.setBorder(BorderFactory.createEmptyBorder(34, 40, 34, 40));
        card.setPreferredSize(new Dimension(360, 380));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        JLabel heading = new JLabel("Welcome back");
        heading.setFont(Theme.bold(22));
        heading.setForeground(Theme.TEXT);
        card.add(heading, c);

        c.gridy++;
        JLabel hint = new JLabel("Sign in to continue");
        hint.setFont(Theme.regular(13));
        hint.setForeground(Theme.MUTED);
        card.add(hint, c);

        c.gridy++;
        c.insets = new java.awt.Insets(22, 0, 4, 0);
        card.add(fieldLabel("Username"), c);
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        styleField(usernameField);
        card.add(usernameField, c);

        c.gridy++;
        c.insets = new java.awt.Insets(16, 0, 4, 0);
        card.add(fieldLabel("Password"), c);
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        styleField(passwordField);
        card.add(passwordField, c);

        c.gridy++;
        c.insets = new java.awt.Insets(10, 0, 0, 0);
        errorLabel.setFont(Theme.regular(12));
        errorLabel.setForeground(Theme.DANGER);
        card.add(errorLabel, c);

        c.gridy++;
        c.insets = new java.awt.Insets(16, 0, 0, 0);
        RoundedButton signIn = new RoundedButton("Sign In", Theme.PRIMARY, java.awt.Color.WHITE);
        signIn.addActionListener(e -> doLogin());
        card.add(signIn, c);

        KeyAdapter enter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doLogin();
                }
            }
        };
        usernameField.addKeyListener(enter);
        passwordField.addKeyListener(enter);

        wrapper.add(card);
        return wrapper;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.bold(12));
        label.setForeground(Theme.MUTED);
        return label;
    }

    private void styleField(JTextField field) {
        field.setFont(Theme.regular(14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        User user = context.getAuthService().authenticate(username, password);
        if (user == null) {
            errorLabel.setText("Invalid username or password.");
            passwordField.setText("");
            return;
        }
        context.setCurrentUser(user);
        context.getAuditService().log("LOGIN", "Signed in");
        dispose();
        SwingUtilities.invokeLater(() -> {
            if (user.getRole() == Role.ADMIN) {
                // The administrator only manages stores and staff.
                AdminFrame admin = new AdminFrame(context);
                admin.setVisible(true);
            } else {
                // Store staff land in the point-of-sale workspace for their store.
                context.reloadSettings();
                MainFrame main = new MainFrame(context);
                main.setVisible(true);
            }
        });
    }
}
