package com.smartbillingpro.ui;

import com.smartbillingpro.util.Money;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Vertical bar chart for one or two money series over time (grouped, one shared
 * axis). Hover a period to see exact values.
 */
public final class ColumnChart extends JComponent {

    public record Point(String label, long a, long b) {
    }

    private List<Point> points = new ArrayList<>();
    private String nameA = "";
    private String nameB;
    private int hover = -1;
    private final String emptyText;

    public ColumnChart(String emptyText) {
        this.emptyText = emptyText;
        setPreferredSize(new Dimension(420, 240));
        setMinimumSize(new Dimension(220, 180));
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setInitialDelay(0);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int h = indexAt(e.getX());
                if (h != hover) {
                    hover = h;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setData(List<Point> data, String seriesA, String seriesB) {
        this.points = data;
        this.nameA = seriesA;
        this.nameB = seriesB;
        hover = -1;
        repaint();
    }

    private static final int LEFT = 58;
    private static final int RIGHT = 10;
    private static final int TOP = 30;
    private static final int BOTTOM = 26;

    private int indexAt(int x) {
        if (points.isEmpty()) {
            return -1;
        }
        double slot = (getWidth() - LEFT - RIGHT) / (double) points.size();
        int i = (int) ((x - LEFT) / slot);
        return i >= 0 && i < points.size() ? i : -1;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = indexAt(e.getX());
        if (i < 0) {
            return null;
        }
        Point p = points.get(i);
        String s = "<html><b>" + p.label() + "</b><br>" + nameA + ": " + Money.format(p.a());
        if (nameB != null) {
            s += "<br>" + nameB + ": " + Money.format(p.b());
        }
        return s + "</html>";
    }

    static String compact(long minor) {
        double v = minor / 100.0;
        double abs = Math.abs(v);
        if (abs >= 1_000_000) {
            return trim(v / 1_000_000) + "M";
        }
        if (abs >= 1_000) {
            return trim(v / 1_000) + "k";
        }
        return trim(v);
    }

    private static String trim(double v) {
        String s = String.format("%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static long niceMax(long max) {
        if (max <= 0) {
            return 100_00;
        }
        double raw = max / 100.0;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double[] steps = {1, 2, 2.5, 5, 10};
        for (double s : steps) {
            if (raw <= s * mag) {
                return (long) Math.ceil(s * mag * 100);
            }
        }
        return (long) Math.ceil(10 * mag * 100);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setFont(Theme.font(11));
        FontMetrics fm = g2.getFontMetrics();
        boolean allZero = points.stream().allMatch(p -> p.a() == 0 && p.b() == 0);
        if (points.isEmpty() || allZero) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.font(13));
            int tw = g2.getFontMetrics().stringWidth(emptyText);
            g2.drawString(emptyText, (w - tw) / 2, h / 2);
            g2.dispose();
            return;
        }
        // legend (two series only)
        if (nameB != null) {
            int lx = LEFT;
            for (int s = 0; s < 2; s++) {
                g2.setColor(s == 0 ? Theme.SERIES_1 : Theme.SERIES_2);
                g2.fillRoundRect(lx, 8, 10, 10, 3, 3);
                g2.setColor(Theme.TEXT_2);
                String n = s == 0 ? nameA : nameB;
                g2.drawString(n, lx + 15, 17);
                lx += 30 + fm.stringWidth(n);
            }
        }
        long max = 0;
        long min = 0;
        for (Point p : points) {
            max = Math.max(max, Math.max(p.a(), nameB == null ? 0 : p.b()));
            min = Math.min(min, Math.min(p.a(), nameB == null ? 0 : p.b()));
        }
        long top = niceMax(max);
        long bottom = min < 0 ? -niceMax(-min) : 0;
        int plotH = h - TOP - BOTTOM;
        int plotW = w - LEFT - RIGHT;
        java.util.function.LongToIntFunction yOf = v -> TOP + (int) Math.round(plotH * (top - v) / (double) (top - bottom));
        // grid + axis labels
        int ticks = 4;
        for (int i = 0; i <= ticks; i++) {
            long v = bottom + (top - bottom) * i / ticks;
            int y = yOf.applyAsInt(v);
            g2.setColor(v == 0 ? Theme.BORDER_STRONG : Theme.GRID);
            g2.drawLine(LEFT, y, w - RIGHT, y);
            g2.setColor(Theme.MUTED);
            String label = compact(v);
            g2.drawString(label, LEFT - 8 - fm.stringWidth(label), y + 4);
        }
        double slot = plotW / (double) points.size();
        int series = nameB == null ? 1 : 2;
        double barW = Math.max(2, Math.min(28, slot * 0.62 / series));
        int zeroY = yOf.applyAsInt(0);
        int labelEvery = Math.max(1, (int) Math.ceil(points.size() / Math.max(1.0, plotW / 64.0)));
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            double cx = LEFT + slot * i + slot / 2;
            if (i == hover) {
                g2.setColor(new Color(0, 0, 0, 12));
                g2.fillRect((int) (LEFT + slot * i), TOP, (int) Math.ceil(slot), plotH);
            }
            for (int s = 0; s < series; s++) {
                long v = s == 0 ? p.a() : p.b();
                double x = cx - barW * series / 2.0 - (series - 1) + s * (barW + 2);
                int y = yOf.applyAsInt(v);
                g2.setColor(s == 0 ? Theme.SERIES_1 : Theme.SERIES_2);
                double top2 = Math.min(y, zeroY);
                double hh = Math.max(1, Math.abs(zeroY - y));
                g2.fill(new RoundRectangle2D.Double(x, top2, barW, hh, Math.min(4, barW), Math.min(4, barW)));
                if (v >= 0 && hh > 4) {
                    g2.fillRect((int) x, (int) (top2 + hh - 3), (int) Math.ceil(barW), 3);
                }
            }
            if (i % labelEvery == 0) {
                g2.setColor(Theme.MUTED);
                String l = p.label();
                g2.drawString(l, (int) (cx - fm.stringWidth(l) / 2.0), h - 8);
            }
        }
        g2.dispose();
    }
}
