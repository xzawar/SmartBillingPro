package com.smartbillingpro.ui;

import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.NavIcons;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.SidebarButton;
import com.smartbillingpro.ui.panels.RecycleBinPanel;
import com.smartbillingpro.ui.panels.StorePanel;
import com.smartbillingpro.ui.panels.UserPanel;
import com.smartbillingpro.ui.theme.Theme;
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

/**
 * The administrator console. Unlike the store workspace, this window is only
 * for running the business behind the software: creating stores (one per
 * customer/shop) and creating the staff accounts that belong to each store.
 * There is no selling or product management here.
 */
public class AdminFrame extends JFrame {

    private final AppContext context;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel content = new JPanel(cardLayout);
    private final JPanel sidebar = new JPanel();
    private final Map<String, SidebarButton> navButtons = new LinkedHashMap<>();
    private final LiveSync liveSync;
    private String currentKey;

    public AdminFrame(AppContext context) {
        this.context = context;
        setTitle("Smart Billing Pro - Admin Console");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1140, 720);
        setMinimumSize(new Dimension(960, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);
        content.setBackground(Theme.BG);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        registerPanels();
        navigateTo("stores");

        // Refresh the admin console in near real time as stores and accounts
        // change (for example a store user signing in from the Store app).
        liveSync = new LiveSync(context.getDatabaseFile(), this::refreshCurrent);
        liveSync.start();
    }

    private JComponent buildTopBar() {
        GradientPanel bar = new GradientPanel(new BorderLayout());
        bar.setColors(Theme.PRIMARY_DARK, Theme.ACCENT);
        bar.setPreferredSize(new Dimension(0, 64));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 22));

        JLabel brand = new JLabel("\uD83D\uDEE1\uFE0F  Admin Console");
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
        addNav("stores", "stores", "Stores", new StorePanel(this, context));
        addNav("users", "users", "Store Owners", new UserPanel(this, context));
        addNav("trash", "trash", "Deleted", new RecycleBinPanel(this, context));

        sidebar.add(Box.createVerticalGlue());
        JLabel version = new JLabel("Admin \u2022 v3.2");
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

    public void navigateTo(String key) {
        if (!navButtons.containsKey(key)) {
            return;
        }
        currentKey = key;
        cardLayout.show(content, key);
        for (Map.Entry<String, SidebarButton> entry : navButtons.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(key));
        }
        for (Component component : content.getComponents()) {
            if (component.isVisible() && component instanceof Refreshable) {
                ((Refreshable) component).refreshData();
            }
        }
    }

    public void refreshCurrent() {
        if (currentKey != null) {
            navigateTo(currentKey);
        }
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(this, "Log out of the admin console?",
                "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            liveSync.stop();
            RememberMe.clear();
            context.getAuditService().log("LOGOUT", "Signed out");
            context.setCurrentUser(null);
            dispose();
            LoginFrame login = new LoginFrame(context);
            login.setVisible(true);
        }
    }

    public AppContext getContext() {
        return context;
    }
}
