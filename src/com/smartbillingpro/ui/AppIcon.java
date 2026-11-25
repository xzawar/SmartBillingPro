package com.smartbillingpro.ui;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The application logo: a lime shopping cart on the theme's deep ink tile. Drawn from the same
 * shapes as {@code resources/branding/app-logo.svg} (64 × 64 design grid), so it is sharp at any size.
 */
public final class AppIcon {

    private AppIcon() {
    }

    /** Draws the logo into a {@code size} × {@code size} square at the origin. */
    public static void paint(Graphics2D g, double size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.scale(size / 64.0, size / 64.0);
        g2.setColor(Theme.ACCENT_INK);
        g2.fill(new RoundRectangle2D.Double(2, 2, 60, 60, 32, 32));
        g2.setColor(Theme.ACCENT);
        Path2D cart = new Path2D.Double();
        cart.moveTo(13, 17);
        cart.lineTo(19.5, 17);
        cart.lineTo(25, 40);
        cart.lineTo(46, 40);
        cart.lineTo(50.5, 24);
        cart.lineTo(21, 24);
        g2.setStroke(new BasicStroke(4.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(cart);
        Graphics2D soft = (Graphics2D) g2.create();
        soft.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        soft.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        soft.draw(new Line2D.Double(28, 31, 44, 31));
        soft.dispose();
        g2.fill(new Ellipse2D.Double(27.5 - 3.8, 48 - 3.8, 7.6, 7.6));
        g2.fill(new Ellipse2D.Double(43.5 - 3.8, 48 - 3.8, 7.6, 7.6));
        g2.dispose();
    }

    public static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paint(g, size);
        g.dispose();
        return img;
    }

    public static List<Image> images() {
        List<Image> list = new ArrayList<>();
        for (int s : new int[] {16, 24, 32, 48, 64, 128, 256}) {
            list.add(render(s));
        }
        return list;
    }

    public static Icon icon(int size) {
        return new ImageIcon(render(size));
    }
}
