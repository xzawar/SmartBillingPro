package com.smartbillingpro.print;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** {@link DocCanvas} on a Java2D surface: the screen preview or a printer page. */
public final class Graphics2DCanvas implements DocCanvas {

    private static final String FAMILY = pickFamily();
    private static final Graphics2D SCRATCH = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();

    private final Graphics2D g;
    private final Map<DocFont, Font> fonts = new HashMap<>();

    public Graphics2DCanvas(Graphics2D g) {
        this.g = g;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** A canvas for measuring only. */
    public static Graphics2DCanvas metricsOnly() {
        return new Graphics2DCanvas(SCRATCH);
    }

    private static String pickFamily() {
        try {
            Set<String> names = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[] {"Segoe UI", "Arial", "DejaVu Sans", "Liberation Sans"}) {
                if (names.contains(f)) {
                    return f;
                }
            }
        } catch (RuntimeException ignored) {
            // fall back to the logical font
        }
        return Font.SANS_SERIF;
    }

    private Font font(DocFont f) {
        return fonts.computeIfAbsent(f, k -> {
            Font base = k.bold() ? com.smartbillingpro.util.Fonts.semiBold() : com.smartbillingpro.util.Fonts.regular();
            if (base != null) {
                return base.deriveFont(Font.PLAIN, k.size());
            }
            return new Font(FAMILY, k.bold() ? Font.BOLD : Font.PLAIN, 12).deriveFont(k.size());
        });
    }

    private FontMetrics fm(DocFont f) {
        return g.getFontMetrics(font(f));
    }

    @Override
    public float textWidth(String text, DocFont font) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (float) font(font).getStringBounds(text, g.getFontRenderContext()).getWidth();
    }

    @Override
    public float ascent(DocFont font) {
        return fm(font).getAscent();
    }

    @Override
    public float lineHeight(DocFont font) {
        return font.size() * 1.32f;
    }

    @Override
    public void text(String text, float x, float baselineY, DocFont font, Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.setFont(font(font));
        g.setColor(color);
        g.drawString(text, x, baselineY);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float width, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        g.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    @Override
    public void fillRect(float x, float y, float w, float h, Color color) {
        g.setColor(color);
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }
}
