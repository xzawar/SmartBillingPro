package com.smartbillingpro.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Monoline vector icons drawn in code on a 24 x 24 grid, so they are crisp at any
 * size and recolour freely (no image files to ship or lose).
 */
public final class Icons {

    private Icons() {
    }

    public static Icon get(String name, int size, Color color) {
        return new Icon() {
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
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.scale(size / 24.0, size / 24.0);
                g2.setColor(c != null && !c.isEnabled() ? Theme.alpha(color, 110) : color);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                draw(g2, name);
                g2.dispose();
            }
        };
    }

    public static Icon get(String name, int size) {
        return get(name, size, Theme.TEXT_2);
    }

    private static Shape rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }

    private static Shape poly(double... pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) {
            p.lineTo(pts[i], pts[i + 1]);
        }
        return p;
    }

    private static void ln(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void draw(Graphics2D g, String name) {
        switch (name) {
            case "dashboard" -> {
                g.draw(rr(3, 3, 7.5, 9, 2));
                g.draw(rr(13.5, 3, 7.5, 5, 2));
                g.draw(rr(3, 15, 7.5, 6, 2));
                g.draw(rr(13.5, 11, 7.5, 10, 2));
            }
            case "sales" -> {
                g.draw(poly(2.5, 3.5, 5.5, 3.5, 8, 15, 18.5, 15, 21, 7, 6.5, 7));
                g.fill(new Ellipse2D.Double(7.6, 17.8, 3, 3));
                g.fill(new Ellipse2D.Double(16, 17.8, 3, 3));
            }
            case "inventory" -> {
                g.draw(poly(12, 2.8, 20.5, 7.3, 20.5, 16.7, 12, 21.2, 3.5, 16.7, 3.5, 7.3, 12, 2.8));
                g.draw(poly(3.8, 7.4, 12, 11.8, 20.2, 7.4));
                ln(g, 12, 11.8, 12, 21);
            }
            case "suppliers" -> {
                g.draw(rr(2.5, 6, 11, 10, 1.5));
                g.draw(poly(13.5, 9, 18, 9, 21.5, 12.5, 21.5, 16, 13.5, 16));
                g.fill(new Ellipse2D.Double(5, 15.5, 4, 4));
                g.fill(new Ellipse2D.Double(15.5, 15.5, 4, 4));
            }
            case "ledgers" -> {
                g.draw(rr(4.5, 3, 15, 18, 2));
                ln(g, 8.5, 3, 8.5, 21);
                ln(g, 11.5, 8, 16.5, 8);
                ln(g, 11.5, 12, 16.5, 12);
            }
            case "customers", "user" -> {
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                g.draw(new Arc2D.Double(4, 14, 16, 14, 0, 180, Arc2D.OPEN));
                if (name.equals("customers")) {
                    g.draw(new Arc2D.Double(16.5, 4.5, 5, 6, -90, 180, Arc2D.OPEN));
                }
            }
            case "receipts" -> {
                g.draw(poly(5, 2.8, 19, 2.8, 19, 21, 16.7, 19.3, 14.3, 21, 12, 19.3, 9.7, 21, 7.3, 19.3, 5, 21, 5, 2.8));
                ln(g, 8.5, 8, 15.5, 8);
                ln(g, 8.5, 11.5, 15.5, 11.5);
                ln(g, 8.5, 15, 12.5, 15);
            }
            case "reports" -> {
                ln(g, 3.5, 20.5, 20.5, 20.5);
                g.draw(rr(5, 11, 3.2, 9.5, 1));
                g.draw(rr(10.4, 6, 3.2, 14.5, 1));
                g.draw(rr(15.8, 13.5, 3.2, 7, 1));
            }
            case "settings" -> {
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    ln(g, 12 + Math.cos(a) * 6.3, 12 + Math.sin(a) * 6.3, 12 + Math.cos(a) * 9, 12 + Math.sin(a) * 9);
                }
                g.draw(new Ellipse2D.Double(5.7, 5.7, 12.6, 12.6));
            }
            case "search" -> {
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                ln(g, 14.8, 14.8, 20, 20);
            }
            case "plus" -> {
                ln(g, 12, 5, 12, 19);
                ln(g, 5, 12, 19, 12);
            }
            case "minus" -> ln(g, 5, 12, 19, 12);
            case "close" -> {
                ln(g, 6, 6, 18, 18);
                ln(g, 18, 6, 6, 18);
            }
            case "check" -> g.draw(poly(5, 12.5, 10, 17.5, 19.5, 7));
            case "barcode" -> {
                g.setStroke(new BasicStroke(1.6f));
                double[] xs = {4, 6.5, 8, 10.5, 13, 14.5, 17, 20};
                for (double x : xs) {
                    ln(g, x, 5, x, 19);
                }
            }
            case "trash" -> {
                ln(g, 4, 6.5, 20, 6.5);
                g.draw(poly(9, 6.5, 9, 3.5, 15, 3.5, 15, 6.5));
                g.draw(poly(6, 6.5, 7, 20.5, 17, 20.5, 18, 6.5));
            }
            case "edit" -> {
                g.draw(poly(4, 20, 4.5, 15.5, 15.5, 4.5, 19.5, 8.5, 8.5, 19.5, 4, 20));
                ln(g, 13, 7, 17, 11);
            }
            case "print" -> {
                g.draw(poly(6.5, 9, 6.5, 3, 17.5, 3, 17.5, 9));
                g.draw(rr(3, 9, 18, 8, 2));
                g.draw(rr(6.5, 14, 11, 7, 1));
            }
            case "pdf", "file" -> {
                g.draw(poly(6, 2.8, 14, 2.8, 19, 7.8, 19, 21.2, 6, 21.2, 6, 2.8));
                g.draw(poly(14, 2.8, 14, 7.8, 19, 7.8));
                ln(g, 9, 13, 16, 13);
                ln(g, 9, 16.5, 14, 16.5);
            }
            case "download" -> {
                ln(g, 12, 3.5, 12, 15);
                g.draw(poly(7.5, 10.5, 12, 15, 16.5, 10.5));
                ln(g, 4.5, 20, 19.5, 20);
            }
            case "upload" -> {
                ln(g, 12, 15, 12, 3.5);
                g.draw(poly(7.5, 8, 12, 3.5, 16.5, 8));
                ln(g, 4.5, 20, 19.5, 20);
            }
            case "backup" -> {
                g.draw(new Ellipse2D.Double(4, 3, 16, 5));
                g.draw(new Arc2D.Double(4, 13.5, 16, 5, 180, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(4, 8.25, 16, 5, 180, 180, Arc2D.OPEN));
                ln(g, 4, 5.5, 4, 16);
                ln(g, 20, 5.5, 20, 16);
            }
            case "warning" -> {
                g.draw(poly(12, 3.5, 21.5, 20, 2.5, 20, 12, 3.5));
                ln(g, 12, 9.5, 12, 14);
                g.fill(new Ellipse2D.Double(11, 16, 2, 2));
            }
            case "info" -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                ln(g, 12, 11, 12, 16.5);
                g.fill(new Ellipse2D.Double(11, 7, 2, 2));
            }
            case "money" -> {
                g.draw(rr(2.5, 6, 19, 12, 2));
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
            }
            case "pause" -> {
                ln(g, 9, 6, 9, 18);
                ln(g, 15, 6, 15, 18);
            }
            case "clock" -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(poly(12, 7, 12, 12, 15.5, 14));
            }
            case "lock" -> {
                g.draw(rr(5, 10.5, 14, 10, 2));
                g.draw(new Arc2D.Double(8, 4, 8, 12, 0, 180, Arc2D.OPEN));
                ln(g, 8, 10, 8, 10.5);
                ln(g, 16, 10, 16, 10.5);
            }
            case "refresh" -> {
                g.draw(new Arc2D.Double(4, 4, 16, 16, 60, 270, Arc2D.OPEN));
                g.draw(poly(16, 2.8, 16.5, 7.2, 12.2, 7.6));
            }
            case "return" -> {
                g.draw(poly(9, 5, 4, 10, 9, 15));
                g.draw(new Arc2D.Double(4, 10, 16, 10, 90, -180, Arc2D.OPEN));
                ln(g, 4, 10, 12, 10);
            }
            case "scale" -> {
                ln(g, 12, 4, 12, 20);
                ln(g, 7, 20, 17, 20);
                ln(g, 5, 7, 19, 7);
                g.draw(poly(5, 7, 2.5, 13, 7.5, 13, 5, 7));
                g.draw(poly(19, 7, 16.5, 13, 21.5, 13, 19, 7));
            }
            case "folder" -> g.draw(poly(3, 6, 9, 6, 11, 8.5, 21, 8.5, 21, 19, 3, 19, 3, 6));
            case "archive" -> {
                g.draw(rr(3, 4, 18, 5, 1));
                g.draw(poly(5, 9, 5, 20, 19, 20, 19, 9));
                ln(g, 10, 13, 14, 13);
            }
            case "chevron-down" -> g.draw(poly(6.5, 9.5, 12, 15, 17.5, 9.5));
            case "chevron-right" -> g.draw(poly(9.5, 6.5, 15, 12, 9.5, 17.5));
            default -> g.draw(new Ellipse2D.Double(5, 5, 14, 14));
        }
    }
}
