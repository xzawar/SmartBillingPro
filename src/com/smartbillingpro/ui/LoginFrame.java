package com.smartbillingpro.ui;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.CloudAccess;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.RememberMe;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * The sign-in window shown before the main application opens.
 *
 * <p>The screen is deliberately simple: a username, a password and a single
 * <b>Sign In</b> button (plus an optional <b>Create a new store</b> button when
 * a cloud backend is configured). The Sign In button works for both local and
 * online accounts - it checks the local database first (so saved accounts open
 * instantly and offline) and falls back to Supabase when the account is not
 * found locally.
 */
public class LoginFrame extends JFrame {

    /** Which kind of accounts a particular launcher accepts. */
    public enum Mode { ALL, ADMIN_ONLY, STORE_ONLY }

    private final AppContext context;
    private final Mode mode;
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JCheckBox rememberMe = new JCheckBox("Remember me");
    private final JLabel errorLabel = new JLabel(" ");
    private RoundedButton signInButton;

    public LoginFrame(AppContext context) {
        this(context, Mode.ALL);
    }

    public LoginFrame(AppContext context, Mode mode) {
        this.context = context;
        this.mode = mode == null ? Mode.ALL : mode;
        setTitle("Smart Billing Pro - " + titleForMode());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(880, 540);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(null);
        setContentPane(buildContent());
        getRootPane().setDefaultButton(null);
    }

    private String titleForMode() {
        switch (mode) {
            case ADMIN_ONLY:
                return "Admin Sign In";
            case STORE_ONLY:
                return "Store Sign In";
            default:
                return "Sign In";
        }
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

        JLabel logo = new JLabel(mode == Mode.ADMIN_ONLY ? "\uD83D\uDEE1\uFE0F" : "\uD83D\uDED2");
        logo.setFont(Theme.regular(54));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Smart Billing Pro");
        title.setFont(Theme.bold(26));
        title.setForeground(java.awt.Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel(mode == Mode.ADMIN_ONLY
                ? "Administrator Console"
                : "Point of Sale & Inventory");
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

        // Username
        c.insets = new java.awt.Insets(0, 0, 4, 0);
        card.add(fieldLabel("Username"), c);
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        styleField(usernameField);
        card.add(usernameField, c);

        // Password
        c.gridy++;
        c.insets = new java.awt.Insets(16, 0, 4, 0);
        card.add(fieldLabel("Password"), c);
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        styleField(passwordField);
        card.add(passwordField, c);

        // Remember me
        c.gridy++;
        c.insets = new java.awt.Insets(12, 0, 0, 0);
        rememberMe.setFont(Theme.regular(13));
        rememberMe.setForeground(Theme.TEXT);
        rememberMe.setOpaque(false);
        rememberMe.setFocusPainted(false);
        rememberMe.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(rememberMe, c);

        // Status / error message
        c.gridy++;
        c.insets = new java.awt.Insets(8, 0, 0, 0);
        errorLabel.setFont(Theme.regular(12));
        errorLabel.setForeground(Theme.DANGER);
        card.add(errorLabel, c);

        // Sign In button (works for both local and online accounts)
        c.gridy++;
        c.insets = new java.awt.Insets(14, 0, 0, 0);
        signInButton = new RoundedButton("Sign In", Theme.PRIMARY, java.awt.Color.WHITE);
        signInButton.addActionListener(e -> doLogin());
        card.add(signInButton, c);

        // Create a new store: only when a cloud backend is configured, and never on
        // the admin-only launcher (admins manage stores from the web console).
        if (CloudAccess.isConfigured() && mode != Mode.ADMIN_ONLY) {
            c.gridy++;
            c.insets = new java.awt.Insets(10, 0, 0, 0);
            RoundedButton createStore = new RoundedButton("Create a new store",
                    Theme.GOLD, Theme.TEXT);
            createStore.addActionListener(e -> openCreateStore());
            card.add(createStore, c);
        }

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

        // Pre-fill the last remembered username for convenience.
        String remembered = RememberMe.get();
        if (remembered != null) {
            usernameField.setText(remembered);
            rememberMe.setSelected(true);
            SwingUtilities.invokeLater(passwordField::requestFocusInWindow);
        }

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

    /**
     * The single Sign In action. Checks the local database first (instant for
     * saved accounts, works offline); if the account is not found locally and a
     * cloud backend is configured, it then checks Supabase, downloads the data
     * and signs in.
     */
    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            showError("Enter your username and password.");
            return;
        }

        // 1) Local database first.
        User local = context.getAuthService().authenticate(username, password);
        if (local != null) {
            if (!allowed(local, mode)) {
                showRejected();
                return;
            }
            finishLogin(local, username, password, true);
            return;
        }

        // 2) Not found locally - try the cloud (Supabase) if configured.
        if (CloudAccess.isConfigured()) {
            setBusy("Checking online\u2026");
            new Thread(() -> {
                User online = null;
                String err = null;
                try {
                    online = CloudAccess.signInOnline(context, username, password);
                } catch (RuntimeException ex) {
                    err = ex.getMessage();
                }
                final User result = online;
                final String error = err;
                SwingUtilities.invokeLater(() -> {
                    setIdle();
                    if (result == null) {
                        showError(error != null ? "Could not sign in: " + error
                                : "Invalid username or password.");
                        passwordField.setText("");
                    } else if (!allowed(result, mode)) {
                        showRejected();
                    } else {
                        finishLogin(result, username, password, false);
                    }
                });
            }, "online-signin").start();
            return;
        }

        showError("Invalid username or password.");
        passwordField.setText("");
    }

    /** Completes a successful sign-in and opens the right workspace. */
    private void finishLogin(User user, String username, String password, boolean ensureCloud) {
        // Honour the "Remember me" choice: it lasts until an explicit logout.
        if (rememberMe.isSelected()) {
            RememberMe.remember(user.getUsername());
        } else {
            RememberMe.clear();
        }
        context.setCurrentUser(user);
        context.getAuditService().log("LOGIN", "Signed in");
        // If we signed in from the local copy but a cloud backend is configured,
        // make sure we also hold a cloud session so background sync runs both
        // ways (this is what keeps Supabase and the desktop in step).
        if (ensureCloud) {
            CloudAccess.ensureBackgroundSync(context, username, password);
        }
        dispose();
        SwingUtilities.invokeLater(() -> openFrameFor(context, user));
    }

    /** Opens the full-screen "Create a new store" wizard. */
    private void openCreateStore() {
        dispose();
        SwingUtilities.invokeLater(() -> new CreateStoreFrame(context, mode).setVisible(true));
    }

    private void showRejected() {
        showError(mode == Mode.ADMIN_ONLY
                ? "This window is for the administrator only."
                : "Administrators must use the Admin app.");
        passwordField.setText("");
    }

    private void showError(String message) {
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setText(message);
    }

    private void setBusy(String message) {
        errorLabel.setForeground(Theme.MUTED);
        errorLabel.setText(message);
        if (signInButton != null) {
            signInButton.setEnabled(false);
        }
    }

    private void setIdle() {
        if (signInButton != null) {
            signInButton.setEnabled(true);
        }
    }

    /** Whether a user may sign in through a launcher of the given mode. */
    static boolean allowed(User user, Mode mode) {
        boolean isAdmin = isAdministrator(user);
        if (mode == Mode.ADMIN_ONLY) {
            return isAdmin;
        }
        if (mode == Mode.STORE_ONLY) {
            return !isAdmin;
        }
        return true;
    }

    /**
     * True for the software administrator. We trust the ADMIN role, but also
     * treat the built-in "admin" username as an administrator so a corrupted
     * role can never lock the admin out of the admin panel.
     */
    static boolean isAdministrator(User user) {
        return user.getRole() == Role.ADMIN
                || "admin".equalsIgnoreCase(user.getUsername());
    }

    /** Opens the correct workspace for a signed-in user. */
    static void openFrameFor(AppContext context, User user) {
        // Start background cloud sync (no-op unless the cloud is configured and a
        // device session has been saved).
        try {
            context.startCloudSync();
        } catch (RuntimeException ignored) {
            // never block sign-in on a sync problem
        }
        if (isAdministrator(user)) {
            new AdminFrame(context).setVisible(true);
        } else {
            context.reloadSettings();
            new MainFrame(context).setVisible(true);
        }
    }

    /**
     * Shared entry point for every launcher. If a user is remembered (and is
     * valid for this launcher's mode) they are signed in automatically and the
     * login window is skipped; otherwise the login window is shown.
     */
    public static void launch(AppContext context, Mode mode) {
        String remembered = RememberMe.get();
        if (remembered != null) {
            User user = context.getAuthService().findActiveByUsername(remembered);
            if (user != null && allowed(user, mode)) {
                context.setCurrentUser(user);
                context.getAuditService().log("LOGIN", "Auto sign-in (remember me)");
                openFrameFor(context, user);
                return;
            }
        }
        new LoginFrame(context, mode).setVisible(true);
    }
}
