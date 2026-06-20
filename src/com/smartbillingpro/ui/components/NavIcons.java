package com.smartbillingpro.ui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Crisp little vector icons drawn with Java2D, used next to the sidebar menu
 * names. Drawing them in code (instead of shipping image files) keeps the app a
 * single self-contained program with no external picture assets to lose, and
 * the icons stay sharp at any size and recolour automatically for the active
 * (white) and inactive (muted) states.
 */
public final class NavIcons {

    private NavIcons() {
    }

    /** Build an icon for the given menu name, drawn at {@code size} px in {@code color}. */
    public static Icon get(final String name, final int size, final Color color) {
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
                g2.translate(x, y);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                paintGlyph(g2, name == null ? "" : name, (float) size);
                g2.dispose();
            }
        };
    }

    private static void paintGlyph(Graphics2D g2, String name, float s) {
        switch (name) {
            case "dashboard": {
                double cell = p(s, 0.32);
                double r = p(s, 0.08);
                g2.draw(new RoundRectangle2D.Double(p(s, 0.14), p(s, 0.14), cell, cell, r, r));
                g2.draw(new RoundRectangle2D.Double(p(s, 0.54), p(s, 0.14), cell, cell, r, r));
                g2.draw(new RoundRectangle2D.Double(p(s, 0.14), p(s, 0.54), cell, cell, r, r));
                g2.draw(new RoundRectangle2D.Double(p(s, 0.54), p(s, 0.54), cell, cell, r, r));
                break;
            }
            case "billing": {
                Path2D.Double cart = new Path2D.Double();
                cart.moveTo(p(s, 0.10), p(s, 0.16));
                cart.lineTo(p(s, 0.24), p(s, 0.16));
                cart.lineTo(p(s, 0.34), p(s, 0.60));
                cart.lineTo(p(s, 0.80), p(s, 0.60));
                cart.lineTo(p(s, 0.88), p(s, 0.30));
                cart.lineTo(p(s, 0.30), p(s, 0.30));
                g2.draw(cart);
                g2.fill(new Ellipse2D.Double(p(s, 0.36), p(s, 0.66), p(s, 0.10), p(s, 0.10)));
                g2.fill(new Ellipse2D.Double(p(s, 0.66), p(s, 0.66), p(s, 0.10), p(s, 0.10)));
                break;
            }
            case "inventory": {
                g2.draw(new Rectangle2D.Double(p(s, 0.20), p(s, 0.30), p(s, 0.60), p(s, 0.50)));
                g2.draw(new Line2D.Double(p(s, 0.20), p(s, 0.46), p(s, 0.80), p(s, 0.46)));
                g2.draw(new Line2D.Double(p(s, 0.50), p(s, 0.30), p(s, 0.50), p(s, 0.46)));
                break;
            }
            case "customers": {
                g2.draw(new Ellipse2D.Double(p(s, 0.36), p(s, 0.16), p(s, 0.28), p(s, 0.28)));
                g2.draw(new Arc2D.Double(p(s, 0.22), p(s, 0.52), p(s, 0.56), p(s, 0.50), 0, 180, Arc2D.OPEN));
                break;
            }
            case "suppliers": {
                g2.draw(new Rectangle2D.Double(p(s, 0.20), p(s, 0.42), p(s, 0.60), p(s, 0.38)));
                Path2D.Double roof = new Path2D.Double();
                roof.moveTo(p(s, 0.20), p(s, 0.42));
                roof.lineTo(p(s, 0.50), p(s, 0.20));
                roof.lineTo(p(s, 0.80), p(s, 0.42));
                g2.draw(roof);
                g2.draw(new Rectangle2D.Double(p(s, 0.44), p(s, 0.58), p(s, 0.12), p(s, 0.22)));
                break;
            }
            case "purchases": {
                g2.draw(new Rectangle2D.Double(p(s, 0.10), p(s, 0.34), p(s, 0.42), p(s, 0.30)));
                g2.draw(new Rectangle2D.Double(p(s, 0.52), p(s, 0.44), p(s, 0.24), p(s, 0.20)));
                g2.fill(new Ellipse2D.Double(p(s, 0.20), p(s, 0.64), p(s, 0.10), p(s, 0.10)));
                g2.fill(new Ellipse2D.Double(p(s, 0.60), p(s, 0.64), p(s, 0.10), p(s, 0.10)));
                break;
            }
            case "staff": {
                g2.draw(new Ellipse2D.Double(p(s, 0.18), p(s, 0.18), p(s, 0.22), p(s, 0.22)));
                g2.draw(new Ellipse2D.Double(p(s, 0.52), p(s, 0.18), p(s, 0.22), p(s, 0.22)));
                g2.draw(new Arc2D.Double(p(s, 0.08), p(s, 0.46), p(s, 0.42), p(s, 0.42), 0, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Double(p(s, 0.42), p(s, 0.46), p(s, 0.42), p(s, 0.42), 0, 180, Arc2D.OPEN));
                break;
            }
            case "history": {
                g2.draw(new Ellipse2D.Double(p(s, 0.16), p(s, 0.16), p(s, 0.68), p(s, 0.68)));
                g2.draw(new Line2D.Double(p(s, 0.50), p(s, 0.50), p(s, 0.50), p(s, 0.28)));
                g2.draw(new Line2D.Double(p(s, 0.50), p(s, 0.50), p(s, 0.68), p(s, 0.50)));
                break;
            }
            case "reports": {
                g2.draw(new Line2D.Double(p(s, 0.20), p(s, 0.18), p(s, 0.20), p(s, 0.80)));
                g2.draw(new Line2D.Double(p(s, 0.20), p(s, 0.80), p(s, 0.84), p(s, 0.80)));
                g2.fill(new Rectangle2D.Double(p(s, 0.32), p(s, 0.55), p(s, 0.12), p(s, 0.25)));
                g2.fill(new Rectangle2D.Double(p(s, 0.50), p(s, 0.42), p(s, 0.12), p(s, 0.38)));
                g2.fill(new Rectangle2D.Double(p(s, 0.68), p(s, 0.30), p(s, 0.12), p(s, 0.50)));
                break;
            }
            case "settings": {
                double cx = p(s, 0.50);
                double cy = p(s, 0.50);
                double rIn = p(s, 0.18);
                double rOut = p(s, 0.30);
                g2.draw(new Ellipse2D.Double(cx - rIn, cy - rIn, rIn * 2, rIn * 2));
                g2.draw(new Ellipse2D.Double(cx - p(s, 0.07), cy - p(s, 0.07), p(s, 0.14), p(s, 0.14)));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8.0;
                    g2.draw(new Line2D.Double(cx + Math.cos(a) * rIn, cy + Math.sin(a) * rIn,
                            cx + Math.cos(a) * rOut, cy + Math.sin(a) * rOut));
                }
                break;
            }
            case "stores": {
                g2.draw(new Rectangle2D.Double(p(s, 0.20), p(s, 0.46), p(s, 0.60), p(s, 0.34)));
                Path2D.Double awning = new Path2D.Double();
                awning.moveTo(p(s, 0.16), p(s, 0.46));
                awning.lineTo(p(s, 0.24), p(s, 0.30));
                awning.lineTo(p(s, 0.76), p(s, 0.30));
                awning.lineTo(p(s, 0.84), p(s, 0.46));
                awning.closePath();
                g2.draw(awning);
                g2.draw(new Rectangle2D.Double(p(s, 0.44), p(s, 0.60), p(s, 0.12), p(s, 0.20)));
                break;
            }
            case "users": {
                Path2D.Double shield = new Path2D.Double();
                shield.moveTo(p(s, 0.50), p(s, 0.14));
                shield.lineTo(p(s, 0.80), p(s, 0.26));
                shield.lineTo(p(s, 0.80), p(s, 0.50));
                shield.curveTo(p(s, 0.80), p(s, 0.70), p(s, 0.66), p(s, 0.82), p(s, 0.50), p(s, 0.88));
                shield.curveTo(p(s, 0.34), p(s, 0.82), p(s, 0.20), p(s, 0.70), p(s, 0.20), p(s, 0.50));
                shield.lineTo(p(s, 0.20), p(s, 0.26));
                shield.closePath();
                g2.draw(shield);
                Path2D.Double check = new Path2D.Double();
                check.moveTo(p(s, 0.38), p(s, 0.50));
                check.lineTo(p(s, 0.47), p(s, 0.60));
                check.lineTo(p(s, 0.64), p(s, 0.38));
                g2.draw(check);
                break;
            }
            default:
                g2.draw(new RoundRectangle2D.Double(p(s, 0.20), p(s, 0.20), p(s, 0.60), p(s, 0.60),
                        p(s, 0.10), p(s, 0.10)));
        }
    }

    private static double p(float s, double f) {
        return s * f;
    }
}
