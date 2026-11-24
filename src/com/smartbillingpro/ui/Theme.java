package com.smartbillingpro.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Design tokens. Every colour, size and font the UI uses comes from here, so a
 * new visual design (e.g. the "GNA" reference) can be applied in one place.
 *
 * <p>Financial status colours are fixed by meaning: green = paid / profit /
 * success, red = dues / loss / error, amber = pending / low stock / warning.
 * They are always paired with a text label or icon, never colour alone.</p>
 */
public final class Theme {

    // surfaces
    public static final Color BG = new Color(0xF3F5F8);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(0xF8FAFC);
    public static final Color BORDER = new Color(0xE2E7EE);
    public static final Color BORDER_STRONG = new Color(0xCBD3DE);

    // text
    public static final Color TEXT = new Color(0x111827);
    public static final Color TEXT_2 = new Color(0x4B5565);
    public static final Color MUTED = new Color(0x6B7480);

    // brand
    public static final Color PRIMARY = new Color(0x1F5FD1);
    public static final Color PRIMARY_HOVER = new Color(0x1A51B3);
    public static final Color PRIMARY_SOFT = new Color(0xE8F0FD);

    // navigation
    public static final Color NAV = new Color(0x0F1B2D);
    public static final Color NAV_HOVER = new Color(0x182841);
    public static final Color NAV_ACTIVE = new Color(0x1E3352);
    public static final Color NAV_TEXT = new Color(0xA9B6C9);
    public static final Color NAV_TEXT_ACTIVE = Color.WHITE;

    // status (always with a label)
    public static final Color GOOD = new Color(0x15803D);
    public static final Color GOOD_SOFT = new Color(0xE7F6EC);
    public static final Color WARN = new Color(0xA16207);
    public static final Color WARN_FILL = new Color(0xE6A100);
    public static final Color WARN_SOFT = new Color(0xFEF5D8);
    public static final Color BAD = new Color(0xC62828);
    public static final Color BAD_SOFT = new Color(0xFDECEC);
    public static final Color INFO_SOFT = PRIMARY_SOFT;

    // chart series (validated categorical slots 1-2)
    public static final Color SERIES_1 = new Color(0x2A78D6);
    public static final Color SERIES_2 = new Color(0xEB6834);
    public static final Color GRID = new Color(0xEDF0F4);

    public static final int RADIUS = 10;

    private static final String FAMILY = pickFamily();

    private Theme() {
    }

    private static String pickFamily() {
        try {
            Set<String> names = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[] {"Segoe UI", "Inter", "Roboto", "DejaVu Sans", "Liberation Sans"}) {
                if (names.contains(f)) {
                    return f;
                }
            }
        } catch (RuntimeException ignored) {
            // headless
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int size) {
        return new Font(FAMILY, Font.PLAIN, size);
    }

    public static Font bold(int size) {
        return new Font(FAMILY, Font.BOLD, size);
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Install FlatLaf and the token-driven defaults. */
    public static void install() {
        UIManager.put("defaultFont", font(13));
        FlatLightLaf.setup();
        UIManager.put("defaultFont", font(13));
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.accentColor", PRIMARY);
        UIManager.put("Component.focusColor", alpha(PRIMARY, 90));
        UIManager.put("Component.focusedBorderColor", PRIMARY);
        UIManager.put("Component.borderColor", BORDER_STRONG);
        UIManager.put("Panel.background", BG);
        UIManager.put("TextField.margin", new java.awt.Insets(5, 8, 5, 8));
        UIManager.put("Button.margin", new java.awt.Insets(6, 14, 6, 14));
        UIManager.put("Table.rowHeight", 34);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.gridColor", new Color(0xEEF1F5));
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
        UIManager.put("Table.selectionBackground", PRIMARY_SOFT);
        UIManager.put("Table.selectionInactiveBackground", new Color(0xEEF2F8));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.selectionInactiveForeground", TEXT);
        UIManager.put("TableHeader.height", 34);
        UIManager.put("TableHeader.background", SURFACE_ALT);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TableHeader.font", bold(11));
        UIManager.put("TableHeader.separatorColor", BG);
        UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.tabHeight", 38);
        UIManager.put("TabbedPane.selectedForeground", TEXT);
        UIManager.put("TabbedPane.underlineColor", PRIMARY);
        UIManager.put("TabbedPane.tabSelectionHeight", 3);
        UIManager.put("TabbedPane.font", bold(13));
        UIManager.put("ToolTip.background", new Color(0x111827));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("List.selectionBackground", PRIMARY_SOFT);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("List.selectionInactiveBackground", PRIMARY_SOFT);
        UIManager.put("List.selectionInactiveForeground", TEXT);
        UIManager.put("SplitPane.dividerSize", 6);
    }
}
