package com.smartbillingpro.ui;

import com.smartbillingpro.util.Money;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Smooth trend chart for one or two money series sharing one axis. Single
 * series get a soft area fill; two series get a legend. Hover shows a
 * crosshair and the exact values.
 */
public final class LineChart extends JComponent {

    public record Point(String label, long a, long b) {
    }

    private List<Point> points = new ArrayList<>();
    private String nameA = "";
    private String nameB;
    private int hover = -1;
    private final String emptyText;
    private java.util.function.LongFunction<String> fullFormat = Money::format;
    private java.util.function.LongFunction<String> axisFormat = v -> Money.symbol() + compact(v);
    private long scale = 100;
    private boolean wholeSteps;
    private boolean bars;

    private static final int LEFT = 56;
    private static final int RIGHT = 14;
    private static final int TOP = 34;
    private static final int BOTTOM = 28;

    public LineChart(String emptyText) {
        this.emptyText = emptyText;
        setPreferredSize(new Dimension(420, 250));
        setMinimumSize(new Dimension(220, 180));
        ToolTipManager.sharedInstance().registerComponent(this);
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

    /** Show values as something other than money (e.g. quantities). */
    public void setFormat(java.util.function.LongFunction<String> full, java.util.function.LongFunction<String> axis) {
        this.fullFormat = full;
        this.axisFormat = axis;
        repaint();
    }

    /** How many stored units make one shown unit (100 for money, 1000 for quantities); whole steps avoid 2.5-type ticks. */
    public void setScale(long unitsPerOne, boolean wholeSteps) {
        this.scale = Math.max(1, unitsPerOne);
        this.wholeSteps = wholeSteps;
        repaint();
    }

    /** Draw one bar per point instead of a line (better for counts per day). Single series only. */
    public void setBars(boolean bars) {
        this.bars = bars;
        repaint();
    }

    public void setData(List<Point> data, String seriesA, String seriesB) {
        this.points = data;
        this.nameA = seriesA;
        this.nameB = seriesB;
        hover = -1;
        repaint();
    }

    private double xOf(int i) {
        int plotW = getWidth() - LEFT - RIGHT;
        if (bars) {
            return LEFT + plotW * (i + 0.5) / Math.max(1, points.size());
        }
        return points.size() <= 1 ? LEFT + plotW / 2.0 : LEFT + plotW * i / (double) (points.size() - 1);
    }

    private int indexAt(int x) {
        if (points.isEmpty()) {
            return -1;
        }
        int best = -1;
        double dist = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double d = Math.abs(xOf(i) - x);
            if (d < dist) {
                dist = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = indexAt(e.getX());
        if (i < 0) {
            return null;
        }
        Point p = points.get(i);
        String s = "<html><b>" + p.label() + "</b><br>" + nameA + ": " + fullFormat.apply(p.a());
        if (nameB != null) {
            s += "<br>" + nameB + ": " + fullFormat.apply(p.b());
        }
        return s + "</html>";
    }

    /** 12500000 -> "125k" (minor units in, major units out). */
    public static String compact(long minor) {
        double v = minor / 100.0;
        double abs = Math.abs(v);
        if (abs >= 10_000_000) {
            return trim(v / 1_000_000) + "M";
        }
        if (abs >= 100_000) {
            return trim(v / 100_000) + " lac";
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

    /** Top of the axis: four equal, round steps that cover {@code max}. */
    private long niceMax(long max) {
        if (max <= 0) {
            return 4 * scale;
        }
        double rawStep = max / (double) scale / 4.0;
        double mag = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double step = 10 * mag;
        for (double s : wholeSteps ? new double[] {1, 2, 5, 10} : new double[] {1, 2, 2.5, 5, 10}) {
            if (rawStep <= s * mag) {
                step = s * mag;
                break;
            }
        }
        if (wholeSteps && step < 1) {
            step = 1;
        }
        return (long) Math.ceil(step * 4 * scale);
    }

    /** Calm placeholder: a soft chart icon and one line of text. */
    static void paintEmpty(Graphics2D g2, int w, int h, String text) {
        int s = 52;
        int x = (w - s) / 2;
        int y = Math.max(4, h / 2 - s);
        g2.setColor(Theme.ACCENT_SOFT);
        g2.fillOval(x, y, s, s);
        Icons.get("reports", 24, Theme.ACCENT_INK).paintIcon(null, g2, x + 14, y + 14);
        g2.setFont(Theme.font(13));
        g2.setColor(Theme.MUTED);
        int tw = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, Math.max(4, (w - tw) / 2), y + s + 22);
    }

    /** Monotone cubic path through the points (no overshoot below zero). */
    private static Path2D smooth(double[] xs, double[] ys) {
        Path2D p = new Path2D.Double();
        int n = xs.length;
        p.moveTo(xs[0], ys[0]);
        if (n == 1) {
            return p;
        }
        double[] m = new double[n];
        double[] d = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            d[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
        }
        m[0] = d[0];
        m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) {
            m[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2;
        }
        for (int i = 0; i < n - 1; i++) {
            double h = xs[i + 1] - xs[i];
            p.curveTo(xs[i] + h / 3, ys[i] + m[i] * h / 3, xs[i + 1] - h / 3, ys[i + 1] - m[i + 1] * h / 3,
                    xs[i + 1], ys[i + 1]);
        }
        return p;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth();
        int h = getHeight();
        g2.setFont(Theme.font(11));
        FontMetrics fm = g2.getFontMetrics();
        boolean allZero = points.stream().allMatch(p -> p.a() == 0 && p.b() == 0);
        if (points.isEmpty() || allZero) {
            paintEmpty(g2, w, h, emptyText);
            g2.dispose();
            return;
        }
        int series = nameB == null ? 1 : 2;
        if (series == 2) {
            int lx = w - RIGHT;
            for (int s = 1; s >= 0; s--) {
                String n = s == 0 ? nameA : nameB;
                lx -= fm.stringWidth(n);
                g2.setColor(Theme.TEXT_2);
                g2.drawString(n, lx, 16);
                lx -= 14;
                g2.setColor(s == 0 ? Theme.SERIES_1 : Theme.SERIES_2);
                g2.fill(new Ellipse2D.Double(lx, 8, 8, 8));
                lx -= 16;
            }
        }
        long max = 0;
        long min = 0;
        for (Point p : points) {
            max = Math.max(max, Math.max(p.a(), series == 2 ? p.b() : 0));
            min = Math.min(min, Math.min(p.a(), series == 2 ? p.b() : 0));
        }
        long top = niceMax(max);
        long bottom = min < 0 ? -niceMax(-min) : 0;
        int plotH = h - TOP - BOTTOM;
        java.util.function.LongToDoubleFunction yOf = v -> TOP + plotH * (top - v) / (double) (top - bottom);
        for (int i = 0; i <= 4; i++) {
            long v = bottom + (top - bottom) * i / 4;
            int y = (int) Math.round(yOf.applyAsDouble(v));
            g2.setColor(v == 0 ? Theme.BORDER_STRONG : Theme.GRID);
            g2.drawLine(LEFT, y, w - RIGHT, y);
            g2.setColor(Theme.MUTED);
            String label = axisFormat.apply(v);
            g2.drawString(label, LEFT - 10 - fm.stringWidth(label), y + 4);
        }
        int n = points.size();
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = xOf(i);
        }
        if (bars && series == 1) {
            int plotW = w - LEFT - RIGHT;
            double band = plotW / (double) n;
            double bw = Math.max(2, Math.min(22, band * 0.62));
            double base = yOf.applyAsDouble(Math.max(0, bottom));
            for (int i = 0; i < n; i++) {
                long v = points.get(i).a();
                if (v == 0) {
                    continue;
                }
                double y = yOf.applyAsDouble(v);
                double barTop = Math.min(y, base);
                double hgt = Math.max(2, Math.abs(base - y));
                g2.setColor(i == hover ? Theme.SERIES_1.darker() : Theme.SERIES_1);
                double r = Math.min(6, bw / 2);
                Path2D bar = new Path2D.Double();
                bar.moveTo(xs[i] - bw / 2, barTop + hgt);
                bar.lineTo(xs[i] - bw / 2, barTop + r);
                bar.quadTo(xs[i] - bw / 2, barTop, xs[i] - bw / 2 + r, barTop);
                bar.lineTo(xs[i] + bw / 2 - r, barTop);
                bar.quadTo(xs[i] + bw / 2, barTop, xs[i] + bw / 2, barTop + r);
                bar.lineTo(xs[i] + bw / 2, barTop + hgt);
                bar.closePath();
                g2.fill(bar);
            }
            paintXLabels(g2, fm, xs, w, h);
            g2.dispose();
            return;
        }
        if (hover >= 0) {
            g2.setColor(Theme.BORDER_STRONG);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {4, 4}, 0));
            g2.drawLine((int) xs[hover], TOP, (int) xs[hover], h - BOTTOM);
        }
        for (int s = series - 1; s >= 0; s--) {
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                ys[i] = yOf.applyAsDouble(s == 0 ? points.get(i).a() : points.get(i).b());
            }
            Color c = s == 0 ? Theme.SERIES_1 : Theme.SERIES_2;
            Path2D line = smooth(xs, ys);
            if (series == 1) {
                Path2D area = new Path2D.Double(line);
                double base = yOf.applyAsDouble(Math.max(0, bottom));
                area.lineTo(xs[n - 1], base);
                area.lineTo(xs[0], base);
                area.closePath();
                g2.setPaint(new GradientPaint(0, TOP, Theme.alpha(c, 60), 0, h - BOTTOM, Theme.alpha(c, 0)));
                g2.fill(area);
            }
            g2.setColor(c);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);
            boolean dots = n <= 40;
            for (int i = 0; i < n; i++) {
                if (!dots && i != hover) {
                    continue;
                }
                double r = i == hover ? 5 : 3.2;
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Double(xs[i] - r - 1.5, ys[i] - r - 1.5, (r + 1.5) * 2, (r + 1.5) * 2));
                g2.setColor(c);
                g2.fill(new Ellipse2D.Double(xs[i] - r, ys[i] - r, r * 2, r * 2));
            }
        }
        paintXLabels(g2, fm, xs, w, h);
        g2.dispose();
    }

    private void paintXLabels(Graphics2D g2, FontMetrics fm, double[] xs, int w, int h) {
        int n = xs.length;
        g2.setFont(Theme.font(11));
        g2.setColor(Theme.MUTED);
        int plotW = w - LEFT - RIGHT;
        int widest = 0;
        for (Point p : points) {
            widest = Math.max(widest, fm.stringWidth(p.label()));
        }
        int every = Math.max(1, (int) Math.ceil(n / Math.max(1.0, plotW / (double) (widest + 14))));
        for (int i = 0; i < n; i += every) {
            String l = points.get(i).label();
            int lw = fm.stringWidth(l);
            int x = (int) Math.max(0, Math.min(w - lw, xs[i] - lw / 2.0));
            g2.drawString(l, x, h - 8);
        }
    }
}
