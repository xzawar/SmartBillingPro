package com.smartbillingpro.util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * The bundled Inter typeface (SIL Open Font License, see resources/fonts).
 * Used on screen, on printed receipts and embedded in PDFs so output looks the
 * same on every computer.
 */
public final class Fonts {

    public static final String REGULAR_RESOURCE = "/fonts/Inter-Regular.ttf";
    public static final String SEMIBOLD_RESOURCE = "/fonts/Inter-SemiBold.ttf";
    public static final String BOLD_RESOURCE = "/fonts/Inter-Bold.ttf";

    private static final Font REGULAR = load(REGULAR_RESOURCE);
    private static final Font SEMIBOLD = load(SEMIBOLD_RESOURCE);
    private static final Font BOLD = load(BOLD_RESOURCE);

    private Fonts() {
    }

    private static Font load(String resource) {
        try (InputStream in = Fonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            } catch (RuntimeException ignored) {
                // still usable without registration
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** Regular weight, or null when the font files are missing. */
    public static Font regular() {
        return REGULAR;
    }

    /** SemiBold weight (used wherever text must stand out), or null. */
    public static Font semiBold() {
        return SEMIBOLD;
    }

    public static Font bold() {
        return BOLD;
    }

    public static InputStream open(String resource) {
        return Fonts.class.getResourceAsStream(resource);
    }
}
