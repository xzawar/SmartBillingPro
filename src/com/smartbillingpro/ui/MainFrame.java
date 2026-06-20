package com.smartbillingpro.ui;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.NavIcons;
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
import com.smartbillingpro.ui.panels.StaffPanel;
import com.smartbillingpro.ui.panels.SupplierPanel;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.CloudSync;
import com.smartbillingpro.util.LiveSync;
import com.smartbillingpro.util.RememberMe;

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
    private final LiveSync liveSync;
    private String currentKey;
    private JPanel activationBanner;

    public MainFrame(AppContext context) {
        this.context = context;
        setTitle("Smart Billing Pro");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1240, 760);
        setMinimumSize(new Dimension(1080, 660));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        JPanel north = new JPanel(new BorderLayout());
        north.add(buildTopBar(), BorderLayout.NORTH);
        activationBanner = buildActivationBanner();
        activationBanner.setVisible(storePending());
        north.add(activationBanner, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);
        content.setBackground(Theme.BG);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        registerPanels();
        navigateTo("dashboard");

        // Keep every open window in sync in near real time: watch the shared
        // database on a background thread and refresh the visible screen
        // whenever anything changes (here or in another app/counter).
        liveSync = new LiveSync(context.getDatabaseFile(), this::refreshCurrent);
        liveSync.start();
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

        addNav("dashboard", "dashboard", "Dashboard", new DashboardPanel(this, context));
        // Only managers and cashiers operate the till. Owners monitor data instead.
        if (role.canSell()) {
            addNav("billing", "billing", "New Sale", new BillingPanel(this, context));
        }
        addNav("inventory", "inventory", "Inventory", new InventoryPanel(this, context));
        addNav("customers", "customers", "Customers", new CustomerPanel(this, context));
        if (role.canManageSuppliers()) {
            addNav("suppliers", "suppliers", "Suppliers", new SupplierPanel(this, context));
        }
        if (role.canManageInventory()) {
            addNav("purchases", "purchases", "Purchases", new PurchasePanel(this, context));
        }
        // The store owner manages their own managers and cashiers here.
        if (role.canManageStaff()) {
            addNav("staff", "staff", "Staff", new StaffPanel(this, context));
        }
        addNav("history", "history", "Sales History", new HistoryPanel(this, context));
        if (role.canViewReports()) {
            addNav("reports", "reports", "Reports", new ReportPanel(this, context));
        }
        if (role.canManageSettings()) {
            addNav("settings", "settings", "Settings", new SettingsPanel(this, context));
        }

        sidebar.add(Box.createVerticalGlue());
        JLabel version = new JLabel("v3.1");
        version.setFont(Theme.regular(11));
        version.setForeground(Theme.SIDEBAR_TEXT);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setBorder(BorderFactory.createEmptyBorder(8, 26, 0, 0));
        sidebar.add(version);
    }

    private void addNav(String key, String iconName, String label, JComponent panel) {
        content.add(panel, key);
        SidebarButton button = new SidebarButton(label);
        button.setIcons(NavIcons.get(iconName, 18, Theme.SIDEBAR_TEXT),
                NavIcons.get(iconName, 18, Color.WHITE));
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
        if (activationBanner != null) {
            // Hide the banner automatically once the administrator activates the store.
            activationBanner.setVisible(storePending());
        }
        if (currentKey != null) {
            navigateTo(currentKey);
        }
    }

    /** A warning strip telling the owner the store is waiting for activation. */
    private JPanel buildActivationBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(Theme.WARNING);
        banner.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        JLabel msg = new JLabel("\u23F3  Waiting for administrator activation \u2014 "
                + "your store is locked until an administrator activates it from the web console.");
        msg.setFont(Theme.bold(13));
        msg.setForeground(Color.WHITE);
        banner.add(msg, BorderLayout.WEST);
        return banner;
    }

    /** True when the signed-in user's store has not been activated yet. */
    private boolean storePending() {
        try {
            int storeId = context.getCurrentStoreId();
            if (storeId <= 0) {
                return false;
            }
            com.smartbillingpro.model.Store store = context.getStoreService().findById(storeId);
            return store != null && !store.isActive();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(this, "Log out of Smart Billing Pro?",
                "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        // Offer to back this store's data up to the cloud on the way out.
        maybeCloudSyncOnLogout();
        liveSync.stop();
        // "Remember me" only lasts until an explicit logout.
        RememberMe.clear();
        context.getAuditService().log("LOGOUT", "Signed out");
        context.setCurrentUser(null);
        context.getBillingService().clear();
        dispose();
        LoginFrame login = new LoginFrame(context);
        login.setVisible(true);
    }

    /**
     * When cloud sync is configured, upload this store's database on logout. If
     * the device is online the upload runs automatically; if not, the user is
     * offered the choice to try anyway. When cloud sync is not configured this
     * does nothing, so the app keeps working fully offline.
     */
    private void maybeCloudSyncOnLogout() {
        if (!CloudSync.isConfigured()) {
            return;
        }
        int storeId = context.getCurrentStoreId();
        java.io.File db = context.getDatabaseFile();
        boolean online = CloudSync.isOnline();
        if (!online) {
            int up = JOptionPane.showConfirmDialog(this,
                    "No internet connection was detected.\n"
                            + "Try uploading this store's data to the cloud anyway?",
                    "Cloud Sync", JOptionPane.YES_NO_OPTION);
            if (up != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            String msg = CloudSync.upload(db, storeId);
            JOptionPane.showMessageDialog(this, msg, "Cloud Sync", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not upload to the cloud:\n" + ex.getMessage(),
                    "Cloud Sync", JOptionPane.WARNING_MESSAGE);
        }
    }

    public AppContext getContext() {
        return context;
    }
}
