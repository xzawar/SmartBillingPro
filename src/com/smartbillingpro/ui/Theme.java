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
 * Design tokens, following the supplied design reference ("Neptune" design
 * DNA plus the dashboard references):
 *
 * <ul>
 *   <li><b>One bright thing per screen</b> - the lime {@link #ACCENT} fills only the
 *       primary pill button and the active navigation item; text on it is the
 *       deep same-hue {@link #ACCENT_INK}, never white.</li>
 *   <li>Warm near-black ink, warm greys, white surfaces on a barely-tinted
 *       background; surfaces separate by tint, not shadows.</li>
 *   <li>Semantic status colours (positive / negative / warning / neutral / info),
 *       always shown with a text label.</li>
 *   <li>Inter in two working weights: Regular for reading, SemiBold for anything
 *       that must be found (titles, amounts, buttons).</li>
 *   <li>16 px radius surfaces, pill buttons.</li>
 * </ul>
 * Every colour and font in the UI comes from here.
 */
public final class Theme {

    // ---------------------------------------------------------------- brand
    /** The single saturated fill (primary button, active nav). */
    public static final Color ACCENT = new Color(0xC8EB52);
    public static final Color ACCENT_HOVER = new Color(0xBADF3F);
    public static final Color ACCENT_PRESSED = new Color(0xA9CE2F);
    /** Deep same-hue ink: text/icons on the accent, links, focus. */
    public static final Color ACCENT_INK = new Color(0x2B3A06);
    /** Pale tint for secondary buttons / selection. */
    public static final Color ACCENT_SOFT = new Color(0xF2F8DC);

    // ---------------------------------------------------------------- surfaces
    public static final Color BG = new Color(0xF4F4F0);          // background.screen (warm tint)
    public static final Color SURFACE = Color.WHITE;              // background.elevated
    public static final Color SURFACE_ALT = new Color(0xF7F7F3);  // background.neutral on white
    public static final Color NEUTRAL_FILL = new Color(0xEDEEE7); // secondary-neutral button / chips
    public static final Color BORDER = new Color(0xE4E5E0);       // border.neutral (12% ink on white)
    public static final Color BORDER_STRONG = new Color(0xD3D5CE);

    // ---------------------------------------------------------------- content
    public static final Color TEXT = new Color(0x0E0F0C);
    public static final Color TEXT_2 = new Color(0x454745);
    public static final Color MUTED = new Color(0x6A6C6A);

    /** Interactive ink (links, focus, selected outlines). */
    public static final Color PRIMARY = ACCENT_INK;
    public static final Color PRIMARY_HOVER = new Color(0x1F2B03);
    public static final Color PRIMARY_SOFT = ACCENT_SOFT;

    // ---------------------------------------------------------------- navigation (light sidebar)
    public static final Color NAV = Color.WHITE;
    public static final Color NAV_HOVER = new Color(0xF1F2EC);
    public static final Color NAV_ACTIVE = ACCENT;
    public static final Color NAV_TEXT = TEXT_2;
    public static final Color NAV_TEXT_ACTIVE = ACCENT_INK;

    // ---------------------------------------------------------------- sentiment (always with a label)
    public static final Color GOOD = new Color(0x054D28);
    public static final Color GOOD_SOFT = new Color(0xE2F6D5);
    public static final Color GOOD_FILL = new Color(0x2F9E5B);
    public static final Color WARN = new Color(0x4A3B1C);
    public static final Color WARN_FILL = new Color(0xFFD11A);
    public static final Color WARN_SOFT = new Color(0xFFF7D7);
    public static final Color BAD = new Color(0xCB272F);
    public static final Color BAD_SOFT = new Color(0xFBEAEA);
    public static final Color INFO = new Color(0x054D4D);
    public static final Color INFO_SOFT = new Color(0xE0F7F7);
    public static final Color NEUTRAL_SOFT = new Color(0xEEEFE9);

    // ---------------------------------------------------------------- pastel tiles (dashboard cards)
    /** Background tint and icon-tile colour pairs for summary cards. */
    public enum Tint {
        GREEN(new Color(0xEAF7EE), new Color(0x2F9E5B)),
        TEAL(new Color(0xE3F6F5), new Color(0x0E8C86)),
        BLUE(new Color(0xEAF2FD), new Color(0x2A78D6)),
        VIOLET(new Color(0xF0EEFD), new Color(0x6250D6)),
        ORANGE(new Color(0xFFF2E8), new Color(0xD9692B)),
        YELLOW(new Color(0xFFF7DC), new Color(0xC99400)),
        RED(new Color(0xFDEEEE), new Color(0xCB272F));

        public final Color bg;
        public final Color icon;

        Tint(Color bg, Color icon) {
            this.bg = bg;
            this.icon = icon;
        }
    }

    // ---------------------------------------------------------------- charts (validated pair)
    public static final Color SERIES_1 = new Color(0x6250D6);
    public static final Color SERIES_2 = new Color(0x1BAF7A);
    public static final Color GRID = new Color(0xEEEFEA);

    // ---------------------------------------------------------------- shape
    public static final int RADIUS = 16;
    public static final int RADIUS_SMALL = 10;

    // ---------------------------------------------------------------- type
    private static final Font INTER_REGULAR = com.smartbillingpro.util.Fonts.regular();
    private static final Font INTER_SEMIBOLD = com.smartbillingpro.util.Fonts.semiBold();
    private static final Font INTER_BOLD = com.smartbillingpro.util.Fonts.bold();
    private static final String FALLBACK = pickFallback();

    private Theme() {
    }

    private static String pickFallback() {
        try {
            Set<String> names = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[] {"Segoe UI", "Roboto", "DejaVu Sans", "Liberation Sans"}) {
                if (names.contains(f)) {
                    return f;
                }
            }
        } catch (RuntimeException ignored) {
            // headless
        }
        return Font.SANS_SERIF;
    }

    /** True when the bundled Inter font is in use. */
    public static boolean interLoaded() {
        return INTER_REGULAR != null;
    }

    /** Family name for code that needs a family string (e.g. receipt rendering). */
    public static String family() {
        return INTER_REGULAR != null ? INTER_REGULAR.getFamily() : FALLBACK;
    }

    /** Regular 400 - for reading. */
    public static Font font(int size) {
        return INTER_REGULAR != null ? INTER_REGULAR.deriveFont(Font.PLAIN, (float) size) : new Font(FALLBACK, Font.PLAIN, size);
    }

    /** SemiBold 600 - titles, amounts, buttons, anything that must be found. */
    public static Font bold(int size) {
        return INTER_SEMIBOLD != null ? INTER_SEMIBOLD.deriveFont(Font.PLAIN, (float) size) : new Font(FALLBACK, Font.BOLD, size);
    }

    /** Bold 700 - hero numbers only. */
    public static Font heavy(int size) {
        return INTER_BOLD != null ? INTER_BOLD.deriveFont(Font.PLAIN, (float) size) : new Font(FALLBACK, Font.BOLD, size);
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Install FlatLaf and the token-driven defaults. */
    public static void install() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont", font(13));
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 999);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.accentColor", ACCENT_INK);
        UIManager.put("Component.focusColor", alpha(ACCENT, 170));
        UIManager.put("Component.focusedBorderColor", ACCENT_INK);
        UIManager.put("Component.borderColor", BORDER_STRONG);
        UIManager.put("Component.disabledBorderColor", BORDER);
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.margin", new java.awt.Insets(6, 10, 6, 10));
        UIManager.put("PasswordField.margin", new java.awt.Insets(6, 10, 6, 10));
        UIManager.put("ComboBox.padding", new java.awt.Insets(4, 8, 4, 8));
        UIManager.put("Button.margin", new java.awt.Insets(7, 18, 7, 18));
        UIManager.put("Button.font", bold(13));
        UIManager.put("Button.background", NEUTRAL_FILL);
        UIManager.put("Button.borderColor", NEUTRAL_FILL);
        UIManager.put("Button.hoverBackground", new Color(0xE3E5DC));
        UIManager.put("Button.foreground", ACCENT_INK);
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", ACCENT_INK);
        UIManager.put("Button.default.borderColor", ACCENT);
        UIManager.put("Button.default.hoverBackground", ACCENT_HOVER);
        UIManager.put("Button.default.focusedBackground", ACCENT);
        UIManager.put("Button.default.boldText", true);
        UIManager.put("CheckBox.icon.selectedBackground", ACCENT_INK);
        UIManager.put("CheckBox.icon.checkmarkColor", Color.WHITE);
        UIManager.put("Table.rowHeight", 38);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.gridColor", new Color(0xF0F0EC));
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
        UIManager.put("Table.selectionBackground", ACCENT_SOFT);
        UIManager.put("Table.selectionInactiveBackground", new Color(0xF4F6EC));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.selectionInactiveForeground", TEXT);
        UIManager.put("TableHeader.height", 36);
        UIManager.put("TableHeader.background", SURFACE);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TableHeader.font", bold(11));
        UIManager.put("TableHeader.separatorColor", SURFACE);
        UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.thumb", new Color(0xD7D8D2));
        UIManager.put("ScrollBar.track", alpha(Color.WHITE, 0));
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.tabHeight", 40);
        UIManager.put("TabbedPane.selectedForeground", TEXT);
        UIManager.put("TabbedPane.foreground", TEXT_2);
        UIManager.put("TabbedPane.underlineColor", ACCENT_INK);
        UIManager.put("TabbedPane.inactiveUnderlineColor", BORDER_STRONG);
        UIManager.put("TabbedPane.tabSelectionHeight", 3);
        UIManager.put("TabbedPane.hoverColor", NAV_HOVER);
        UIManager.put("TabbedPane.focusColor", SURFACE_ALT);
        UIManager.put("TabbedPane.contentAreaColor", BORDER);
        UIManager.put("TabbedPane.font", bold(13));
        UIManager.put("TabbedPane.tabInsets", new java.awt.Insets(4, 14, 4, 14));
        UIManager.put("ToolTip.background", TEXT);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(7, 10, 7, 10));
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageFont", font(14));
        UIManager.put("OptionPane.buttonFont", bold(13));
        UIManager.put("List.selectionBackground", ACCENT_SOFT);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("List.selectionInactiveBackground", ACCENT_SOFT);
        UIManager.put("List.selectionInactiveForeground", TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_SOFT);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("SplitPane.dividerSize", 8);
        UIManager.put("SplitPaneDivider.gripColor", BORDER_STRONG);
        UIManager.put("TitlePane.background", SURFACE);
    }
}
