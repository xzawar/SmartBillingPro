package com.smartbillingpro.ui;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.SidebarButton;
import com.smartbillingpro.ui.panels.BillingPanel;
import com.smartbillingpro.ui.panels.CustomerPanel;
import com.smartbillingpro.ui.panels.DashboardPanel;
import com.smartbillingpro.ui.panels.HistoryPanel;
import com.smartbillingpro.ui.panels.InventoryPanel;
import com.smartbillingpro.ui.panels.PurchasePanel;
import com.smartbillingpro.ui.panels.ReportPanel;
import com.smartbillingpro.ui.panels.SettingsPanel;
import com.smartbillingpro.ui.panels.SupplierPanel;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

/** The main window: top bar, sidebar navigation and the swappable content area. */
public class MainFrame extends JFrame {

    private final AppContext context;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel content = new JPanel(cardLayout);
    private final JPanel sidebar = new JPanel();
    private final Map<String, SidebarButton> navButtons = new LinkedHashMap<>();
    private String currentKey;

    public MainFrame(AppContext context) {
        this.context = context;
        setTitle("Smart Billing Pro");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1240, 760);
        setMinimumSize(new Dimension(1080, 660));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);
        content.setBackground(Theme.BG);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        registerPanels();
        navigateTo("dashboard");
    }

    private JComponent buildTopBar() {
        GradientPanel bar = new GradientPanel(new BorderLayout());
        bar.setColors(Theme.PRIMARY, Theme.ACCENT);
        bar.setPreferredSize(new Dimension(0, 64));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 22));

        // Show the shop's own name (from its settings) so each store sees its brand.
        String storeName = context.getSettings().getBusinessName();
        if (storeName == null || storeName.isBlank()) {
            storeName = "Smart Billing Pro";
        }
        JLabel brand = new JLabel("\uD83D\uDED2  " + storeName);
        brand.setFont(Theme.bold(20));
        brand.setForeground(Color.WHITE);
        bar.add(brand, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));

        User user = context.getCurrentUser();
        JLabel who = new JLabel(user.getDisplayName() + "  \u2022  " + user.getRole().getLabel());
        who.setFont(Theme.bold(13));
        who.setForeground(Color.WHITE);

        RoundedButton logout = new RoundedButton("Logout", Theme.withAlpha(Color.WHITE, 40), Color.WHITE);
        logout.addActionListener(e -> logout());

        right.add(who);
        right.add(Box.createHorizontalStrut(16));
        right.add(logout);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildSidebar() {
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(232, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
        return sidebar;
    }

    private void registerPanels() {
        Role role = context.getCurrentUser().getRole();

        addNav("dashboard", "\uD83D\uDCCA  Dashboard", new DashboardPanel(this, context));
        addNav("billing", "\uD83D\uDED2  New Sale", new BillingPanel(this, context));
        addNav("inventory", "\uD83D\uDCE6  Inventory", new InventoryPanel(this, context));
        addNav("customers", "\uD83D\uDC65  Customers", new CustomerPanel(this, context));
        if (role.canManageSuppliers()) {
            addNav("suppliers", "\uD83C\uDFED  Suppliers", new SupplierPanel(this, context));
        }
        if (role.canManageInventory()) {
            addNav("purchases", "\uD83D\uDCE5  Purchases", new PurchasePanel(this, context));
        }
        addNav("history", "\uD83E\uDDFE  Sales History", new HistoryPanel(this, context));
        if (role.canViewReports()) {
            addNav("reports", "\uD83D\uDCC8  Reports", new ReportPanel(this, context));
        }
        if (role.canManageSettings()) {
            addNav("settings", "\u2699\uFE0F  Settings", new SettingsPanel(this, context));
        }

        sidebar.add(Box.createVerticalGlue());
        JLabel version = new JLabel("v3.0");
        version.setFont(Theme.regular(11));
        version.setForeground(Theme.SIDEBAR_TEXT);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setBorder(BorderFactory.createEmptyBorder(8, 26, 0, 0));
        sidebar.add(version);
    }

    private void addNav(String key, String label, JComponent panel) {
        content.add(panel, key);
        SidebarButton button = new SidebarButton(label);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> navigateTo(key));
        sidebar.add(button);
        navButtons.put(key, button);
    }

    /** Switches the visible panel and refreshes it if needed. */
    public void navigateTo(String key) {
        if (!navButtons.containsKey(key)) {
            return;
        }
        currentKey = key;
        cardLayout.show(content, key);
        for (Map.Entry<String, SidebarButton> entry : navButtons.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(key));
        }
        Component[] components = content.getComponents();
        for (Component component : components) {
            if (component.isVisible() && component instanceof Refreshable) {
                ((Refreshable) component).refreshData();
            }
        }
    }

    /** Re-runs refresh on the panel that is currently visible. */
    public void refreshCurrent() {
        if (currentKey != null) {
            navigateTo(currentKey);
        }
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(this, "Log out of Smart Billing Pro?",
                "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            context.getAuditService().log("LOGOUT", "Signed out");
            context.setCurrentUser(null);
            context.getBillingService().clear();
            dispose();
            LoginFrame login = new LoginFrame(context);
            login.setVisible(true);
        }
    }

    public AppContext getContext() {
        return context;
    }
}
