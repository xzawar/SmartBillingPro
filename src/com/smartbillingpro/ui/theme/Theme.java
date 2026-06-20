package com.smartbillingpro.ui.theme;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Central palette, fonts and small colour helpers for a consistent look. */
public final class Theme {

    public static final Color PRIMARY = new Color(0x1E3A8A);
    public static final Color PRIMARY_DARK = new Color(0x172554);
    public static final Color ACCENT = new Color(0x5B21B6);
    public static final Color GOLD = new Color(0xD4AF37);
    public static final Color GOLD_LIGHT = new Color(0xF4D03F);
    public static final Color BG = new Color(0xF0F4FF);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x1F2937);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color SUCCESS = new Color(0x10B981);
    public static final Color DANGER = new Color(0xEF4444);
    public static final Color WARNING = new Color(0xF59E0B);
    public static final Color INFO = new Color(0x3B82F6);
    public static final Color CARD_BORDER = new Color(0xE5E7EB);
    public static final Color TABLE_STRIPE = new Color(0xF8FAFF);
    public static final Color SIDEBAR = new Color(0x172554);
    public static final Color SIDEBAR_HOVER = new Color(0x1E3A8A);
    public static final Color SIDEBAR_TEXT = new Color(0xCBD5F5);

    private static final String FAMILY = pickFamily();

    private Theme() {
    }

    private static String pickFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String preferred : new String[] {"Poppins", "Segoe UI", "Inter", "Roboto"}) {
            if (available.contains(preferred)) {
                return preferred;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, int size) {
        return new Font(FAMILY, style, size);
    }

    public static Font regular(int size) {
        return font(Font.PLAIN, size);
    }

    public static Font bold(int size) {
        return font(Font.BOLD, size);
    }

    /** Returns a darker shade for hover/pressed states. */
    public static Color darken(Color color, double factor) {
        return new Color(
                Math.max(0, (int) (color.getRed() * factor)),
                Math.max(0, (int) (color.getGreen() * factor)),
                Math.max(0, (int) (color.getBlue() * factor)));
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** Applies a few global defaults so stock Swing widgets match the theme. */
    public static void apply() {
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", regular(13));
        UIManager.put("TextField.font", regular(13));
        UIManager.put("PasswordField.font", regular(13));
        UIManager.put("ComboBox.font", regular(13));
        UIManager.put("Table.font", regular(13));
        UIManager.put("TableHeader.font", bold(12));
        UIManager.put("TextArea.font", new Font(Font.MONOSPACED, Font.PLAIN, 12));
        UIManager.put("Table.selectionBackground", withAlpha(PRIMARY, 40));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", CARD_BORDER);
    }
}
