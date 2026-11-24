package com.smartbillingpro.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** One stacked bar of stock lines by status, with a labelled legend underneath. */
public final class StockStatusBar extends JComponent {

    private long[] counts = new long[3];
    private static final String[] LABELS = {"In stock", "Low stock", "Out of stock"};
    private static final Color[] COLORS = {Theme.GOOD, Theme.WARN_FILL, Theme.BAD};

    public StockStatusBar() {
        setPreferredSize(new Dimension(300, 64));
    }

    public void setCounts(long[] counts) {
        this.counts = counts;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        long total = counts[0] + counts[1] + counts[2];
        int w = getWidth();
        if (total == 0) {
            g2.setColor(Theme.GRID);
            g2.fillRoundRect(0, 4, w, 14, 8, 8);
        } else {
            int x = 0;
            for (int i = 0; i < 3; i++) {
                if (counts[i] == 0) {
                    continue;
                }
                int seg = (int) Math.round(w * counts[i] / (double) total);
                seg = Math.max(seg, 4);
                if (x + seg > w) {
                    seg = w - x;
                }
                g2.setColor(COLORS[i]);
                g2.fillRoundRect(x, 4, Math.max(1, seg - 2), 14, 6, 6);
                x += seg;
            }
        }
        g2.setFont(Theme.font(12));
        int lx = 0;
        for (int i = 0; i < 3; i++) {
            g2.setColor(COLORS[i]);
            g2.fillOval(lx, 33, 9, 9);
            g2.setColor(Theme.TEXT_2);
            String t = LABELS[i] + "  " + counts[i];
            g2.drawString(t, lx + 14, 42);
            lx += g2.getFontMetrics().stringWidth(t) + 30;
        }
        g2.dispose();
    }
}
