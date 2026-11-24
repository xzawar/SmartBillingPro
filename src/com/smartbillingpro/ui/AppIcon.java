package com.smartbillingpro.ui;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** The application logo, drawn in code (blue tile with a receipt). */
public final class AppIcon {

    private AppIcon() {
    }

    public static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 64.0;
        g.scale(s, s);
        g.setPaint(new GradientPaint(0, 0, new Color(0x2F7BF0), 64, 64, new Color(0x1546A8)));
        g.fill(new RoundRectangle2D.Double(2, 2, 60, 60, 16, 16));
        Path2D r = new Path2D.Double();
        r.moveTo(19, 13);
        r.lineTo(45, 13);
        r.lineTo(45, 51);
        r.lineTo(40.7, 47.5);
        r.lineTo(36.3, 51);
        r.lineTo(32, 47.5);
        r.lineTo(27.7, 51);
        r.lineTo(23.3, 47.5);
        r.lineTo(19, 51);
        r.closePath();
        g.setColor(Color.WHITE);
        g.fill(r);
        g.setColor(new Color(0x1F5FD1));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(25, 23, 39, 23);
        g.drawLine(25, 30, 39, 30);
        g.drawLine(25, 37, 33, 37);
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
