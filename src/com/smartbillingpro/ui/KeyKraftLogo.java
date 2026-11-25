package com.smartbillingpro.ui;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * The KeyKraft logo, drawn from the same vector shapes and colours as
 * {@code resources/branding/keykraft-logo.svg} (a 1024 × 1024 design), so it
 * stays sharp at every size and screen scale.
 */
public final class KeyKraftLogo implements Icon {

    /** Design grid of the SVG. */
    private static final double GRID = 1024;
    private static final Color INK = new Color(0x0B, 0x0B, 0x0B);
    private static final float[] STOPS = {0f, 0.859f, 1f};
    private static final Color[] GREENS = {new Color(0x8E, 0xD2, 0x19), new Color(0x75, 0xB7, 0x00),
        new Color(0x71, 0xB3, 0x00)};

    private final int size;

    public KeyKraftLogo(int size) {
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        paint(g2, size);
        g2.dispose();
    }

    /** Draws the logo into a {@code size} × {@code size} square at the origin. */
    public static void paint(Graphics2D g, double size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.transform(AffineTransform.getScaleInstance(size / GRID, size / GRID));
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(310, 210), 875f, STOPS, GREENS,
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fill(new Ellipse2D.Double(0, 0, GRID, GRID));
        Path2D mark = new Path2D.Double();
        mark.moveTo(390, 262);
        mark.lineTo(690, 352);
        mark.lineTo(690, 672);
        mark.lineTo(390, 762);
        mark.closePath();
        g2.setColor(INK);
        g2.fill(mark);
        g2.dispose();
    }

    /** The logo as a transparent image (e.g. for window icons or exports). */
    public static BufferedImage image(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paint(g, size);
        g.dispose();
        return img;
    }
}
