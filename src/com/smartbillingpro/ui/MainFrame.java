package com.smartbillingpro.ui;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.app.AppPaths;
import com.smartbillingpro.ui.pages.CustomersPage;
import com.smartbillingpro.ui.pages.DashboardPage;
import com.smartbillingpro.ui.pages.InventoryPage;
import com.smartbillingpro.ui.pages.LedgersPage;
import com.smartbillingpro.ui.pages.PosPage;
import com.smartbillingpro.ui.pages.ReceiptsPage;
import com.smartbillingpro.ui.pages.ReportsPage;
import com.smartbillingpro.ui.pages.SettingsPage;
import com.smartbillingpro.ui.pages.SuppliersPage;
import com.smartbillingpro.util.Dates;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** The single application window: sidebar navigation and one page at a time. */
public final class MainFrame extends JFrame {

    public static final String DASHBOARD = "dashboard";
    public static final String SALES = "sales";
    public static final String INVENTORY = "inventory";
    public static final String SUPPLIERS = "suppliers";
    public static final String LEDGERS = "ledgers";
    public static final String CUSTOMERS = "customers";
    public static final String RECEIPTS = "receipts";
    public static final String REPORTS = "reports";
    public static final String SETTINGS = "settings";

    private static final String[][] NAV = {
        {DASHBOARD, "Dashboard", "dashboard"},
        {SALES, "Sales", "sales"},
        {INVENTORY, "Inventory", "inventory"},
        {SUPPLIERS, "Suppliers", "suppliers"},
        {LEDGERS, "Ledgers", "ledgers"},
        {CUSTOMERS, "Customers", "customers"},
        {RECEIPTS, "Receipts", "receipts"},
        {REPORTS, "Reports", "reports"},
        {SETTINGS, "Tools & Settings", "settings"},
    };

    private final AppContext ctx;
    private final Runnable onExit;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, NavButton> navButtons = new LinkedHashMap<>();
    private final JLabel storeName = new JLabel();
    private final JLabel clock = Ui.muted("");
    private final JLabel backupChip = Ui.pill("", Ui.Tone.NEUTRAL);
    private final JPanel sidebar = new JPanel();
    private String current;
    private boolean compact;

    public MainFrame(AppContext ctx, Runnable onExit) {
        super(AppPaths.APP_NAME);
        this.ctx = ctx;
        this.onExit = onExit;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImages(AppIcon.images());
        setMinimumSize(new Dimension(980, 640));
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1366, screen.width), Math.min(820, screen.height));
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.add(buildTopBar(), BorderLayout.NORTH);
        content.setBackground(Theme.BG);
        main.add(content, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        buildPages();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestExit();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                setCompact(getWidth() < 1180);
            }
        });
        for (int i = 0; i < NAV.length; i++) {
            final String key = NAV[i][0];
            Ui.key(root, "ctrl " + (i + 1), () -> show(key));
        }
        Timer t = new Timer(20_000, e -> tick());
        t.start();
        tick();
        updateBackupStatus();
        show(SALES);
    }

    public AppContext context() {
        return ctx;
    }

    private void setStoreName(String name) {
        String safe = name == null ? "" : name.replace("&", "&amp;").replace("<", "&lt;");
        storeName.setText("<html><div style='width:150px'>" + safe + "</div></html>");
        storeName.setToolTipText(name);
    }

    private void buildPages() {
        for (Page p : pages.values()) {
            content.remove(p);
        }
        pages.clear();
        pages.put(DASHBOARD, new DashboardPage(this));
        pages.put(SALES, new PosPage(this));
        pages.put(INVENTORY, new InventoryPage(this));
        pages.put(SUPPLIERS, new SuppliersPage(this));
        pages.put(LEDGERS, new LedgersPage(this));
        pages.put(CUSTOMERS, new CustomersPage(this));
        pages.put(RECEIPTS, new ReceiptsPage(this));
        pages.put(REPORTS, new ReportsPage(this));
        pages.put(SETTINGS, new SettingsPage(this));
        pages.forEach((k, p) -> content.add(p, k));
        setStoreName(ctx.settings().get().storeName());
    }

    /** Rebuild every screen (after a restore or a settings change that affects formatting). */
    public void reloadAll(String showKey) {
        buildPages();
        setStoreName(ctx.settings().get().storeName());
        current = null;
        show(showKey);
        updateBackupStatus();
    }

    public void show(String key) {
        Page target = pages.get(key);
        if (target == null) {
            return;
        }
        if (current != null && !current.equals(key) && !pages.get(current).canLeave()) {
            return;
        }
        current = key;
        cards.show(content, key);
        navButtons.forEach((k, b) -> b.setActive(k.equals(key)));
        try {
            target.refresh();
        } catch (RuntimeException e) {
            Msg.error(this, e);
        }
        javax.swing.SwingUtilities.invokeLater(target::focusDefault);
    }

    @SuppressWarnings("unchecked")
    public <T extends Page> T page(String key) {
        return (T) pages.get(key);
    }

    public void storeNameChanged() {
        setStoreName(ctx.settings().get().storeName());
    }

    // ------------------------------------------------------------ sidebar

    private Component buildSidebar() {
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.NAV);
        sidebar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                BorderFactory.createEmptyBorder(18, 12, 14, 12)));
        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        JLabel logo = new JLabel(AppIcon.icon(34));
        brand.add(logo, BorderLayout.WEST);
        storeName.setFont(Theme.bold(15));
        storeName.setForeground(Theme.TEXT);
        JLabel app = new JLabel("Smart Billing Pro");
        app.setFont(Theme.font(12));
        app.setForeground(Theme.MUTED);
        JPanel names = new JPanel(new BorderLayout());
        names.setOpaque(false);
        names.add(storeName, BorderLayout.NORTH);
        names.add(app, BorderLayout.SOUTH);
        brand.add(names, BorderLayout.CENTER);
        brand.putClientProperty("names", names);
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(22));
        for (int i = 0; i < NAV.length; i++) {
            String[] n = NAV[i];
            NavButton b = new NavButton(n[1], n[2], "Ctrl+" + (i + 1));
            b.addActionListener(e -> show(n[0]));
            navButtons.put(n[0], b);
            sidebar.add(b);
            sidebar.add(Box.createVerticalStrut(2));
        }
        sidebar.add(Box.createVerticalGlue());
        JLabel version = new JLabel("Offline • v" + AppPaths.VERSION);
        version.setFont(Theme.font(12));
        version.setForeground(Theme.MUTED);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        sidebar.add(version);
        sidebar.setPreferredSize(new Dimension(232, 0));
        return sidebar;
    }

    private void setCompact(boolean c) {
        if (c == compact) {
            return;
        }
        compact = c;
        sidebar.setPreferredSize(new Dimension(c ? 70 : 232, 0));
        for (Component comp : sidebar.getComponents()) {
            if (comp instanceof JPanel p && p.getClientProperty("names") instanceof JPanel names) {
                names.setVisible(!c);
            }
            if (comp instanceof JLabel l) {
                l.setVisible(!c);
            }
        }
        navButtons.values().forEach(b -> b.setCompact(c));
        sidebar.revalidate();
        sidebar.repaint();
    }

    /** Sidebar item with icon, label and active indicator. */
    private static final class NavButton extends JButton {
        private final String label;
        private final String icon;
        private boolean active;

        NavButton(String label, String icon, String shortcut) {
            super(label);
            this.label = label;
            this.icon = icon;
            setToolTipText(label + "  (" + shortcut + ")");
            setHorizontalAlignment(SwingConstants.LEFT);
            setFont(Theme.font(14));
            setForeground(Theme.NAV_TEXT);
            setIcon(Icons.get(icon, 20, Theme.NAV_TEXT));
            setIconTextGap(12);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 10));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            getModel().addChangeListener(e -> repaint());
        }

        void setActive(boolean a) {
            active = a;
            Color c = a ? Theme.NAV_TEXT_ACTIVE : Theme.NAV_TEXT;
            setForeground(c);
            setFont(a ? Theme.bold(14) : Theme.font(14));
            setIcon(Icons.get(icon, 20, c));
            getAccessibleContext().setAccessibleDescription(a ? "current page" : null);
            repaint();
        }

        void setCompact(boolean c) {
            setText(c ? "" : label);
            setHorizontalAlignment(c ? SwingConstants.CENTER : SwingConstants.LEFT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(Theme.NAV_ACTIVE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            } else if (getModel().isRollover()) {
                g2.setColor(Theme.NAV_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------ top bar

    private Component buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 26, 10, 20)));
        clock.setFont(Theme.font(13));
        clock.setForeground(Theme.TEXT_2);
        bar.add(clock, BorderLayout.WEST);
        backupChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backupChip.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                show(SETTINGS);
                SettingsPage sp = page(SETTINGS);
                sp.showBackupTab();
            }
        });
        JButton lock = Ui.icon("lock", "Lock the screen");
        lock.addActionListener(e -> lockScreen());
        lock.setVisible(ctx.settings().get().pinEnabled());
        bar.putClientProperty("lock", lock);
        bar.add(Ui.flowRight(12, backupChip, lock, ownerChip()), BorderLayout.EAST);
        return bar;
    }

    /** Avatar with the shop's initials - there is one owner and no login. */
    private JComponent ownerChip() {
        JLabel avatar = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_INK);
                g2.fillOval(0, 0, 34, 34);
                g2.setColor(Theme.ACCENT);
                g2.setFont(Theme.bold(13));
                String in = initials(ctx.settings().get().storeName());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                g2.drawString(in, (34 - fm.stringWidth(in)) / 2, 17 + fm.getAscent() / 2 - 2);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(34, 34));
        JLabel name = new JLabel("Owner");
        name.setFont(Theme.bold(13));
        JLabel role = Ui.muted("Offline \u2022 this PC");
        role.setFont(Theme.font(11));
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(name, BorderLayout.NORTH);
        text.add(role, BorderLayout.SOUTH);
        JPanel chip = new JPanel(new BorderLayout(8, 0));
        chip.setOpaque(false);
        chip.add(avatar, BorderLayout.WEST);
        chip.add(text, BorderLayout.CENTER);
        return chip;
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : (name == null ? "" : name).trim().split("\\s+")) {
            if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(0)) && sb.length() < 2) {
                sb.append(Character.toUpperCase(w.charAt(0)));
            }
        }
        return sb.length() == 0 ? "SB" : sb.toString();
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        clock.setText(now.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                + ", " + Dates.formatDate(now.toLocalDate()) + "   " + Dates.formatTime(now));
    }

    /** Refresh the backup indicator (safe to call from any thread). */
    public void updateBackupStatus() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            String last = ctx.backups().lastBackupTime();
            String status = ctx.backups().lastStatus();
            LocalDate lastDate = last.isBlank() ? null : Dates.dateOf(last);
            if ("FAILED".equals(status)) {
                backupChip.setText("⚠ Backup failed – click to fix");
                Ui.setTone(backupChip, Ui.Tone.BAD);
            } else if (lastDate == null) {
                backupChip.setText("No backup yet");
                Ui.setTone(backupChip, Ui.Tone.WARN);
            } else if (lastDate.equals(LocalDate.now())) {
                backupChip.setText("✓ Backed up today " + Dates.formatTime(Dates.parse(last)));
                Ui.setTone(backupChip, Ui.Tone.GOOD);
            } else {
                backupChip.setText("Last backup " + Dates.formatDate(lastDate));
                Ui.setTone(backupChip, Ui.Tone.WARN);
            }
            backupChip.setToolTipText(ctx.backups().lastError().isBlank() ? "Backups are saved in "
                    + ctx.backups().backupDir() : ctx.backups().lastError());
        });
    }

    public void pinChanged() {
        Component top = ((BorderLayout) ((JPanel) content.getParent()).getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (top instanceof JPanel p && p.getClientProperty("lock") instanceof JButton lock) {
            lock.setVisible(ctx.settings().get().pinEnabled());
        }
    }

    private void lockScreen() {
        PinDialog.unlock(this, ctx, true);
    }

    // ------------------------------------------------------------ exit

    private void requestExit() {
        for (Page p : pages.values()) {
            if (!p.canExit()) {
                return;
            }
        }
        onExit.run();
    }
}
