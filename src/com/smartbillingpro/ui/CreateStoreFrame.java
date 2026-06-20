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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * An app-styled "Create a new store" experience that lives entirely inside one
 * window. Instead of popping up a separate dialog for the owner account, both
 * steps are shown as themed cards that swap in place (just like the rest of the
 * app), so the whole flow feels native:
 *
 * <ol>
 *   <li><b>Store details</b> - name, phone, address. Press Enter or Continue.</li>
 *   <li><b>Owner account</b> - shown as a second card in the same window.</li>
 *   <li>The owner is created and signed straight into their dashboard, which
 *       opens <em>locked</em> with a "waiting for administrator activation"
 *       banner until an administrator activates the store.</li>
 * </ol>
 */
public class CreateStoreFrame extends JFrame {

    private final AppContext context;
    private final LoginFrame.Mode mode;

    // Step 1 - store details
    private final JTextField storeName = new JTextField(22);
    private final JTextField storePhone = new JTextField(22);
    private final JTextField storeAddress = new JTextField(22);
    private final JLabel storeError = new JLabel(" ");

    // Step 2 - owner account
    private final JTextField ownerName = new JTextField(22);
    private final JTextField ownerUser = new JTextField(22);
    private final JPasswordField ownerPass = new JPasswordField(22);
    private final JPasswordField ownerConfirm = new JPasswordField(22);
    private final JLabel ownerError = new JLabel(" ");
    private RoundedButton createButton;

    // Card swapping (store <-> owner) inside the same window
    private final CardLayout cards = new CardLayout();
    private final JPanel cardArea = new JPanel(cards);
    private JLabel step1;
    private JLabel step2;
    private JLabel step3;

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

        showStep(1);
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

        step1 = stepLabel("1.  Store details");
        step2 = stepLabel("2.  Owner account");
        step3 = stepLabel("3.  Start using your shop");
        sidebar.add(step1);
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(step2);
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(step3);
        sidebar.add(Box.createVerticalGlue());

        JLabel note = new JLabel("<html>New stores stay locked<br>until an administrator<br>activates them.</html>");
        note.setFont(Theme.regular(11));
        note.setForeground(Theme.SIDEBAR_TEXT);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(note);
        return sidebar;
    }

    private JLabel stepLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.regular(14));
        l.setForeground(Theme.SIDEBAR_TEXT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Theme.BG);
        cardArea.setOpaque(false);
        cardArea.add(buildStoreCard(), "store");
        cardArea.add(buildOwnerCard(), "owner");
        wrapper.add(cardArea);
        return wrapper;
    }

    // ---- Step 1 card: store details ----
    private JComponent buildStoreCard() {
        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        card.setArc(22);
        card.setBorder(BorderFactory.createEmptyBorder(32, 40, 32, 40));
        card.setPreferredSize(new Dimension(480, 470));
        GridBagConstraints c = baseConstraints();

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
        c.insets = new Insets(10, 0, 0, 0);
        storeError.setFont(Theme.regular(12));
        storeError.setForeground(Theme.DANGER);
        card.add(storeError, c);

        c.gridy++;
        c.insets = new Insets(16, 0, 0, 0);
        RoundedButton cont = new RoundedButton("Continue", Theme.PRIMARY, Color.WHITE);
        cont.addActionListener(e -> goToOwnerStep());
        card.add(cont, c);

        addEnter(storeName, this::goToOwnerStep);
        addEnter(storePhone, this::goToOwnerStep);
        addEnter(storeAddress, this::goToOwnerStep);
        return card;
    }

    // ---- Step 2 card: owner account (identical styling, same window) ----
    private JComponent buildOwnerCard() {
        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        card.setArc(22);
        card.setBorder(BorderFactory.createEmptyBorder(32, 40, 32, 40));
        card.setPreferredSize(new Dimension(480, 520));
        GridBagConstraints c = baseConstraints();

        JLabel heading = new JLabel("Owner account");
        heading.setFont(Theme.bold(22));
        heading.setForeground(Theme.TEXT);
        card.add(heading, c);

        c.gridy++;
        JLabel hint = new JLabel("Create the account you will use to run this store.");
        hint.setFont(Theme.regular(13));
        hint.setForeground(Theme.MUTED);
        card.add(hint, c);

        addField(card, c, "Your full name", ownerName);
        addField(card, c, "Choose a username", ownerUser);
        addField(card, c, "Choose a password", ownerPass);
        addField(card, c, "Confirm password", ownerConfirm);

        c.gridy++;
        c.insets = new Insets(10, 0, 0, 0);
        ownerError.setFont(Theme.regular(12));
        ownerError.setForeground(Theme.DANGER);
        card.add(ownerError, c);

        c.gridy++;
        c.insets = new Insets(16, 0, 0, 0);
        createButton = new RoundedButton("Create store", Theme.PRIMARY, Color.WHITE);
        createButton.addActionListener(e -> submitOwner());
        card.add(createButton, c);

        c.gridy++;
        c.insets = new Insets(10, 0, 0, 0);
        RoundedButton back = new RoundedButton("Back", Theme.withAlpha(Theme.PRIMARY, 30), Theme.TEXT);
        back.addActionListener(e -> showStep(1));
        card.add(back, c);

        addEnter(ownerName, this::submitOwner);
        addEnter(ownerUser, this::submitOwner);
        addEnter(ownerPass, this::submitOwner);
        addEnter(ownerConfirm, this::submitOwner);
        return card;
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        return c;
    }

    private void addField(RoundedPanel card, GridBagConstraints c, String label, JTextField field) {
        c.gridy++;
        c.insets = new Insets(16, 0, 4, 0);
        JLabel l = new JLabel(label);
        l.setFont(Theme.bold(12));
        l.setForeground(Theme.MUTED);
        card.add(l, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        field.setFont(Theme.regular(14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        card.add(field, c);
    }

    private void addEnter(JComponent field, Runnable action) {
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    action.run();
                }
            }
        });
    }

    /** Swaps the visible card and highlights the matching step in the sidebar. */
    private void showStep(int n) {
        cards.show(cardArea, n == 1 ? "store" : "owner");
        styleStep(step1, n == 1);
        styleStep(step2, n == 2);
        styleStep(step3, false);
        SwingUtilities.invokeLater(() -> (n == 1 ? storeName : ownerName).requestFocusInWindow());
    }

    private void styleStep(JLabel l, boolean active) {
        if (l == null) {
            return;
        }
        l.setFont(active ? Theme.bold(14) : Theme.regular(14));
        l.setForeground(active ? Color.WHITE : Theme.SIDEBAR_TEXT);
    }

    /** Validates the store details, then moves to the owner card. */
    private void goToOwnerStep() {
        if (storeName.getText().trim().isEmpty()) {
            storeError.setText("Store name is required.");
            return;
        }
        storeError.setText(" ");
        showStep(2);
    }

    /**
     * Validates the owner account, then jumps STRAIGHT into the owner dashboard.
     *
     * <p>Instead of waiting for the network, we sign the owner in locally with a
     * temporary account so their dashboard opens instantly - fully locked, with
     * the "waiting for administrator activation" banner. The real cloud
     * registration then runs on a background thread; when it finishes the screen
     * quietly swaps to the saved account, and the whole app unlocks itself the
     * moment an administrator activates the store online (the background sync
     * checks every few seconds and updates the screen in place).
     */
    private void submitOwner() {
        String user = ownerUser.getText().trim();
        String pass = new String(ownerPass.getPassword());
        String confirm = new String(ownerConfirm.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            ownerError.setForeground(Theme.DANGER);
            ownerError.setText("The owner username and password are required.");
            return;
        }
        if (!pass.equals(confirm)) {
            ownerError.setForeground(Theme.DANGER);
            ownerError.setText("The two passwords do not match.");
            return;
        }

        final String name = storeName.getText().trim();
        final String phone = storePhone.getText().trim();
        final String address = storeAddress.getText().trim();
        final String fullName = ownerName.getText().trim();
        final String ownerLabel = fullName.isEmpty() ? user : fullName;

        // Sign in locally with a temporary owner so the dashboard can open NOW.
        // We never remember this pending owner across restarts, or auto-login
        // would stop the administrator from ever reaching the admin panel.
        User optimistic = new User(0, user, ownerLabel, Role.OWNER, true);
        optimistic.setStoreId(CloudAccess.LOCAL_STORE_ID);
        RememberMe.clear();
        context.setCurrentUser(optimistic);

        MainFrame frame = new MainFrame(context, true);
        frame.setVisible(true);
        dispose();

        // Register the store + owner in the cloud on a background thread.
        new Thread(() -> {
            User created = null;
            String err = null;
            try {
                created = CloudAccess.registerStore(context, name, phone, address, user, pass, fullName);
            } catch (RuntimeException ex) {
                err = ex.getMessage();
            }
            final User result = created;
            final String error = err;
            SwingUtilities.invokeLater(() -> {
                if (result == null) {
                    frame.onStoreRegistrationFailed(error);
                } else {
                    context.setCurrentUser(result);
                    context.getAuditService().log("REGISTER_STORE", "Created store " + name);
                    frame.onStoreRegistered();
                }
            });
        }, "register-store").start();
    }

    private void backToLogin() {
        dispose();
        SwingUtilities.invokeLater(() -> new LoginFrame(context, mode).setVisible(true));
    }
}
